package io.capstead.runtime;

import io.capstead.core.CapabilityBudgetException;
import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityMetadata;
import io.capstead.core.ModelInvocation;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Turns every {@code @Capability} invocation into a first-class {@link CapabilityExecution} and
 * fans it out to all registered {@link CapabilityExecutionRecorder}s via a {@link CapabilityExecutionPublisher}.
 *
 * <p>Applied via a Spring AOP advisor, so capture is fully automatic — application code writes no
 * metrics. The interceptor enforces {@code @DailyBudget} before proceeding, then owns the execution
 * lifecycle (open, assign id, attribute principal, time, mark success/error, price each model
 * invocation via the {@link TokenCostEstimator}, publish) and exposes the in-flight execution through
 * {@link CapabilityExecutionContext} so token/model data can be enriched by whoever actually calls the
 * model. Nested capability calls are linked into an execution tree automatically by the context stack.
 */
public class CapabilityMethodInterceptor implements MethodInterceptor {

    private final CapabilityMetadataResolver metadataResolver;
    private final Supplier<CapabilityExecutionPublisher> publisherSupplier;
    private final Supplier<TokenCostEstimator> costEstimatorSupplier;
    private final Supplier<CapabilityBudgetLedger> budgetLedgerSupplier;
    private final Supplier<CapabilityExecutionOptions> optionsSupplier;

    private volatile boolean resolved;
    private CapabilityExecutionPublisher publisher;
    private TokenCostEstimator costEstimator;
    private CapabilityBudgetLedger budgetLedger;
    private CapabilityExecutionOptions options;

    /**
     * Collaborators are supplied lazily (resolved on first capability invocation) so that building
     * this interceptor for the AOP advisor does not force early creation of the publisher, recorders,
     * meter registry, cost estimator or budget ledger during the BeanPostProcessor phase.
     */
    public CapabilityMethodInterceptor(CapabilityMetadataResolver metadataResolver,
                                       Supplier<CapabilityExecutionPublisher> publisherSupplier,
                                       Supplier<TokenCostEstimator> costEstimatorSupplier,
                                       Supplier<CapabilityBudgetLedger> budgetLedgerSupplier,
                                       Supplier<CapabilityExecutionOptions> optionsSupplier) {
        this.metadataResolver = metadataResolver;
        this.publisherSupplier = publisherSupplier;
        this.costEstimatorSupplier = costEstimatorSupplier;
        this.budgetLedgerSupplier = budgetLedgerSupplier;
        this.optionsSupplier = optionsSupplier;
    }

    private void ensureResolved() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {
                    this.publisher = publisherSupplier.get();
                    this.costEstimator = costEstimatorSupplier.get();
                    this.budgetLedger = budgetLedgerSupplier.get();
                    CapabilityExecutionOptions resolvedOptions = optionsSupplier.get();
                    this.options = resolvedOptions == null ? CapabilityExecutionOptions.defaults() : resolvedOptions;
                    this.resolved = true;
                }
            }
        }
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        CapabilityMetadata metadata = metadataResolver.resolve(invocation.getMethod());
        if (metadata == null) {
            return invocation.proceed();
        }

        ensureResolved();
        enforceDailyBudget(invocation.getMethod(), metadata);

        CapabilityExecution.Builder builder = CapabilityExecution
                .builder(metadata.name(), metadata.version())
                .domain(metadata.domain())
                .startedAt(Instant.now());
        attributePrincipal(builder);
        captureInput(builder, invocation.getArguments());
        CapabilityExecutionContext.begin(builder);

        long start = System.nanoTime();
        boolean success = true;
        String errorType = null;
        try {
            Object result = invocation.proceed();
            captureOutput(builder, result);
            recordDeclaredUsage(invocation, builder);
            return result;
        } catch (Throwable t) {
            success = false;
            errorType = t.getClass().getName();
            throw t;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            priceModelInvocations(builder);
            CapabilityExecution execution = builder
                    .finishedAt(Instant.now())
                    .durationMs(durationMs)
                    .success(success)
                    .errorType(errorType)
                    .build();
            CapabilityExecutionContext.clear();
            publisher.publish(execution);
        }
    }

    private void attributePrincipal(CapabilityExecution.Builder builder) {
        if (options.principalProvider() == null) {
            return;
        }
        String principal = options.principalProvider().currentPrincipal();
        if (principal != null) {
            builder.principal(principal);
        }
    }

    private void captureInput(CapabilityExecution.Builder builder, Object[] arguments) {
        if (!options.captureInput() || arguments == null || arguments.length == 0) {
            return;
        }
        builder.capturedInput(options.redactor().redact(summarize(arguments)));
    }

    private void captureOutput(CapabilityExecution.Builder builder, Object result) {
        if (!options.captureOutput() || result == null) {
            return;
        }
        builder.capturedOutput(options.redactor().redact(truncate(String.valueOf(result))));
    }

    private String summarize(Object[] arguments) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.valueOf(arguments[i]));
        }
        return truncate(sb.toString());
    }

    private String truncate(String value) {
        int max = options.captureMaxLength();
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /**
     * Config-declared usage metering: when a {@link CapabilityUsageRule} is declared and the call
     * recorded nothing itself, synthesize one model invocation from the declared argument (e.g. TTS
     * text length as characters). Runs before {@link #priceModelInvocations}, so the synthesized
     * invocation is priced from {@code capstead.cost} like any other. Real enrichment always wins —
     * a rule never overrides what the call recorded.
     */
    private void recordDeclaredUsage(MethodInvocation invocation, CapabilityExecution.Builder builder) {
        CapabilityUsageRule rule = metadataResolver.usageRule(invocation.getMethod());
        if (rule == null) {
            return;
        }
        if (!builder.modelInvocations().isEmpty() || builder.model() != null) {
            return; // the call enriched its own usage — declared metering is only a fallback
        }
        int units = rule.measure(invocation.getArguments());
        if (units < 0) {
            return;
        }
        builder.addModelInvocation(new ModelInvocation(rule.model(), units, 0, null, Instant.now()));
    }

    /** Prices any model invocation (or the synthesized back-compat one) that lacks an estimated cost. */
    private void priceModelInvocations(CapabilityExecution.Builder builder) {
        if (costEstimator == null) {
            return;
        }
        List<ModelInvocation> invocations = builder.modelInvocations();
        if (!invocations.isEmpty()) {
            for (int i = 0; i < invocations.size(); i++) {
                ModelInvocation invocation = invocations.get(i);
                if (invocation.estimatedCost() == null && invocation.model() != null) {
                    BigDecimal cost = costEstimator.estimate(
                            invocation.model(), invocation.inputTokens(), invocation.outputTokens());
                    if (cost != null) {
                        invocations.set(i, new ModelInvocation(invocation.model(), invocation.inputTokens(),
                                invocation.outputTokens(), cost, invocation.invokedAt()));
                    }
                }
            }
            return;
        }
        // Back-compat single-invocation path: price the fields that will be synthesized into one invocation.
        if (builder.estimatedCost() == null && builder.model() != null) {
            BigDecimal cost = costEstimator.estimate(
                    builder.model(), builder.inputTokens(), builder.outputTokens());
            if (cost != null) {
                builder.estimatedCost(cost);
            }
        }
    }

    private void enforceDailyBudget(Method method, CapabilityMetadata metadata) {
        if (budgetLedger == null) {
            return;
        }
        String spec = metadataResolver.dailyBudget(method);
        if (spec == null) {
            return;
        }
        BigDecimal budget = parseBudget(spec);
        if (budget == null) {
            return;
        }
        BigDecimal spent = budgetLedger.spentToday(metadata.coordinates());
        if (spent.compareTo(budget) >= 0) {
            throw new CapabilityBudgetException(metadata.name(), budget, spent);
        }
    }

    private static BigDecimal parseBudget(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
