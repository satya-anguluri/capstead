package io.capstead.runtime;

import io.capstead.annotation.Capability;
import io.capstead.annotation.DailyBudget;
import io.capstead.core.CapabilityBudgetException;
import io.capstead.core.CapabilityExecution;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;

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
    public CapabilityMethodInterceptor(Supplier<List<CapabilityExecutionRecorder>> recordersSupplier,
                                       Supplier<TokenCostEstimator> costEstimatorSupplier,
                                       Supplier<CapabilityBudgetLedger> budgetLedgerSupplier) {
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
        Capability annotation =
                AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(), Capability.class);
        if (annotation == null) {
            return invocation.proceed();
        }

        ensureResolved();
        enforceDailyBudget(invocation, annotation);

        CapabilityExecution.Builder builder = CapabilityExecution
                .builder(annotation.name(), annotation.version())
                .domain(annotation.domain())
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

    private void enforceDailyBudget(MethodInvocation invocation, Capability annotation) {
        if (budgetLedger == null) {
            return;
        }
        DailyBudget dailyBudget =
                AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(), DailyBudget.class);
        if (dailyBudget == null) {
            return;
        }
        BigDecimal budget = parseBudget(dailyBudget.value());
        if (budget == null) {
            return;
        }
        String coordinates = annotation.name() + "@" + annotation.version();
        BigDecimal spent = budgetLedger.spentToday(coordinates);
        if (spent.compareTo(budget) >= 0) {
            throw new CapabilityBudgetException(annotation.name(), budget, spent);
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
