package io.capstead.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A first-class record of one capability execution.
 *
 * <p>This is the atom the entire Capstead metrics story is built on: Actuator, Micrometer,
 * dashboards, cost reports, budgets and alerts all derive from streams of these. Capstead captures
 * the lifecycle fields automatically (id, timing, success, error, parent); the AI-specific data is
 * <em>enriched</em> — typically by a Spring AI observation bridge — as a list of
 * {@link ModelInvocation}s via {@code CapabilityExecutionContext}, because Capstead does not execute
 * the model itself.
 *
 * <p>Token and cost accessors are <strong>derived totals</strong> summed across the invocations, so
 * a capability that calls the model several times is reported accurately.
 */
public final class CapabilityExecution {

    private final String executionId;
    private final String parentExecutionId;
    private final String capabilityName;
    private final String version;
    private final String domain;
    private final String principal;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final long durationMs;
    private final int retries;
    private final boolean success;
    private final String errorType;
    private final String capturedInput;
    private final String capturedOutput;
    private final List<ModelInvocation> modelInvocations;

    private CapabilityExecution(Builder builder) {
        this.executionId = builder.executionId;
        this.parentExecutionId = builder.parentExecutionId;
        this.capabilityName = builder.capabilityName;
        this.version = builder.version;
        this.domain = builder.domain;
        this.principal = builder.principal;
        this.startedAt = builder.startedAt;
        this.finishedAt = builder.finishedAt;
        this.durationMs = builder.durationMs;
        this.retries = builder.retries;
        this.success = builder.success;
        this.errorType = builder.errorType;
        this.capturedInput = builder.capturedInput;
        this.capturedOutput = builder.capturedOutput;
        this.modelInvocations = Collections.unmodifiableList(builder.resolveInvocations());
    }

    /** Unique id of this execution, assigned when it begins. */
    public String executionId() {
        return executionId;
    }

    /** Id of the enclosing capability execution, or {@code null} when this is a root execution. */
    public String parentExecutionId() {
        return parentExecutionId;
    }

    public String capabilityName() {
        return capabilityName;
    }

    public String version() {
        return version;
    }

    public String domain() {
        return domain;
    }

    /** Stable identity across versions: {@code name@version}. */
    public String coordinates() {
        return capabilityName + "@" + version;
    }

    /** The principal (caller) this execution ran for, when recorded; otherwise {@code null}. */
    public String principal() {
        return principal;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public long durationMs() {
        return durationMs;
    }

    public int retries() {
        return retries;
    }

    public boolean success() {
        return success;
    }

    /** Fully-qualified exception type on failure; {@code null} on success. */
    public String errorType() {
        return errorType;
    }

    /**
     * A (possibly redacted) summary of the capability's input, captured only when input capture is
     * enabled; {@code null} otherwise. Capstead does not store raw inputs by default.
     */
    public String capturedInput() {
        return capturedInput;
    }

    /**
     * A (possibly redacted) summary of the capability's output, captured only when output capture is
     * enabled; {@code null} otherwise. Capstead does not store raw outputs by default.
     */
    public String capturedOutput() {
        return capturedOutput;
    }

    /** Every model call made during this execution, in order (possibly empty). */
    public List<ModelInvocation> modelInvocations() {
        return modelInvocations;
    }

    /** Number of model calls made during this execution. */
    public int modelInvocationCount() {
        return modelInvocations.size();
    }

    /** Total input tokens summed across all model invocations. */
    public int inputTokens() {
        int sum = 0;
        for (ModelInvocation invocation : modelInvocations) {
            sum += invocation.inputTokens();
        }
        return sum;
    }

    /** Total output tokens summed across all model invocations. */
    public int outputTokens() {
        int sum = 0;
        for (ModelInvocation invocation : modelInvocations) {
            sum += invocation.outputTokens();
        }
        return sum;
    }

    public int totalTokens() {
        return inputTokens() + outputTokens();
    }

    /** Total estimated cost summed across invocations, or {@code null} if none is priced. */
    public BigDecimal estimatedCost() {
        BigDecimal total = null;
        for (ModelInvocation invocation : modelInvocations) {
            if (invocation.estimatedCost() != null) {
                total = total == null ? invocation.estimatedCost() : total.add(invocation.estimatedCost());
            }
        }
        return total;
    }

    /** The model of the most recent invocation (convenience), or {@code null} if there were none. */
    public String model() {
        return modelInvocations.isEmpty() ? null : modelInvocations.get(modelInvocations.size() - 1).model();
    }

    public static Builder builder(String capabilityName, String version) {
        return new Builder(capabilityName, version);
    }

    /** Mutable builder assembled across the execution lifecycle and enrichment. */
    public static final class Builder {

        private String executionId;
        private String parentExecutionId;
        private final String capabilityName;
        private final String version;
        private String domain = "";
        private String principal;
        private Instant startedAt;
        private Instant finishedAt;
        private long durationMs;
        private int retries;
        private boolean success = true;
        private String errorType;
        private String capturedInput;
        private String capturedOutput;

        // The multi-call model invocation API.
        private final List<ModelInvocation> modelInvocations = new ArrayList<>();

        // Back-compat single-invocation enrichment fields (synthesized into one invocation if used alone).
        private String model;
        private int inputTokens;
        private int outputTokens;
        private BigDecimal estimatedCost;

        private Builder(String capabilityName, String version) {
            this.capabilityName = capabilityName;
            this.version = version;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public String executionId() {
            return executionId;
        }

        public Builder parentExecutionId(String parentExecutionId) {
            this.parentExecutionId = parentExecutionId;
            return this;
        }

        public String parentExecutionId() {
            return parentExecutionId;
        }

        public Builder domain(String domain) {
            this.domain = domain == null ? "" : domain;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder capturedInput(String capturedInput) {
            this.capturedInput = capturedInput;
            return this;
        }

        public Builder capturedOutput(String capturedOutput) {
            this.capturedOutput = capturedOutput;
            return this;
        }

        /** Append an explicit model invocation (the multi-call API). */
        public Builder addModelInvocation(ModelInvocation invocation) {
            if (invocation != null) {
                this.modelInvocations.add(invocation);
            }
            return this;
        }

        public List<ModelInvocation> modelInvocations() {
            return modelInvocations;
        }

        // --- Back-compat single-invocation enrichment ---

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** Current model set on this builder (via single-invocation enrichment), or {@code null}. */
        public String model() {
            return model;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public int inputTokens() {
            return inputTokens;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public int outputTokens() {
            return outputTokens;
        }

        public Builder estimatedCost(BigDecimal estimatedCost) {
            this.estimatedCost = estimatedCost;
            return this;
        }

        /** Current estimated cost set on this builder, or {@code null} if not yet priced. */
        public BigDecimal estimatedCost() {
            return estimatedCost;
        }

        /**
         * Resolves the final invocation list: explicit invocations win; otherwise the back-compat
         * single-invocation fields are synthesized into one invocation (or none, if nothing was recorded).
         */
        List<ModelInvocation> resolveInvocations() {
            if (!modelInvocations.isEmpty()) {
                return new ArrayList<>(modelInvocations);
            }
            if (model != null || inputTokens != 0 || outputTokens != 0 || estimatedCost != null) {
                List<ModelInvocation> synthesized = new ArrayList<>(1);
                synthesized.add(new ModelInvocation(model, inputTokens, outputTokens, estimatedCost, finishedAt));
                return synthesized;
            }
            return new ArrayList<>();
        }

        public CapabilityExecution build() {
            return new CapabilityExecution(this);
        }
    }
}
