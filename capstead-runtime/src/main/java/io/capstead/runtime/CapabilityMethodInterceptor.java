package io.capstead.runtime;

import io.capstead.core.CapabilityBudgetException;
import io.capstead.core.CapabilityExecution;
import io.capstead.core.CapabilityMetadata;

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
 * fans it out to all registered {@link CapabilityExecutionRecorder}s.
 *
 * <p>Applied via a Spring AOP advisor, so capture is fully automatic — application code writes no
 * metrics. The interceptor enforces {@code @DailyBudget} before proceeding, then owns the execution
 * lifecycle (open, time, mark success/error, price via the {@link TokenCostEstimator}, publish) and
 * exposes the in-flight execution through {@link CapabilityExecutionContext} so token/model data can
 * be enriched by whoever actually calls the model.
 */
public class CapabilityMethodInterceptor implements MethodInterceptor {

    private final CapabilityMetadataResolver metadataResolver;
    private final Supplier<List<CapabilityExecutionRecorder>> recordersSupplier;
    private final Supplier<TokenCostEstimator> costEstimatorSupplier;
    private final Supplier<CapabilityBudgetLedger> budgetLedgerSupplier;

    private volatile boolean resolved;
    private List<CapabilityExecutionRecorder> recorders;
    private TokenCostEstimator costEstimator;
    private CapabilityBudgetLedger budgetLedger;

    /**
     * Collaborators are supplied lazily (resolved on first capability invocation) so that building
     * this interceptor for the AOP advisor does not force early creation of the recorders, meter
     * registry, cost estimator or budget ledger during the BeanPostProcessor phase.
     */
    public CapabilityMethodInterceptor(CapabilityMetadataResolver metadataResolver,
                                       Supplier<List<CapabilityExecutionRecorder>> recordersSupplier,
                                       Supplier<TokenCostEstimator> costEstimatorSupplier,
                                       Supplier<CapabilityBudgetLedger> budgetLedgerSupplier) {
        this.metadataResolver = metadataResolver;
        this.recordersSupplier = recordersSupplier;
        this.costEstimatorSupplier = costEstimatorSupplier;
        this.budgetLedgerSupplier = budgetLedgerSupplier;
    }

    private void ensureResolved() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {
                    this.recorders = recordersSupplier.get();
                    this.costEstimator = costEstimatorSupplier.get();
                    this.budgetLedger = budgetLedgerSupplier.get();
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
        CapabilityExecutionContext.begin(builder);

        long start = System.nanoTime();
        boolean success = true;
        String errorType = null;
        try {
            return invocation.proceed();
        } catch (Throwable t) {
            success = false;
            errorType = t.getClass().getName();
            throw t;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (costEstimator != null && builder.estimatedCost() == null && builder.model() != null) {
                BigDecimal cost = costEstimator.estimate(
                        builder.model(), builder.inputTokens(), builder.outputTokens());
                if (cost != null) {
                    builder.estimatedCost(cost);
                }
            }
            CapabilityExecution execution = builder
                    .finishedAt(Instant.now())
                    .durationMs(durationMs)
                    .success(success)
                    .errorType(errorType)
                    .build();
            CapabilityExecutionContext.clear();
            for (CapabilityExecutionRecorder recorder : recorders) {
                recorder.record(execution);
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
