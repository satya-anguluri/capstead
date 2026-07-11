package io.capstead.core;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A first-class record of one capability execution.
 *
 * <p>This is the atom the entire Capstead metrics story is built on: Actuator, Micrometer,
 * dashboards, cost reports, budgets and alerts all derive from streams of these. Capstead captures
 * the lifecycle fields automatically (timing, success, error); the AI-specific fields (model,
 * tokens, cost) are <em>enriched</em> — typically by a Spring AI observation bridge — via
 * {@code CapabilityExecutionContext}, because Capstead does not execute the model itself.
 */
public final class CapabilityExecution {

    private final String capabilityName;
    private final String version;
    private final String domain;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final long durationMs;
    private final String model;
    private final int inputTokens;
    private final int outputTokens;
    private final BigDecimal estimatedCost;
    private final int retries;
    private final boolean success;
    private final String errorType;

    private CapabilityExecution(Builder builder) {
        this.capabilityName = builder.capabilityName;
        this.version = builder.version;
        this.domain = builder.domain;
        this.startedAt = builder.startedAt;
        this.finishedAt = builder.finishedAt;
        this.durationMs = builder.durationMs;
        this.model = builder.model;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.estimatedCost = builder.estimatedCost;
        this.retries = builder.retries;
        this.success = builder.success;
        this.errorType = builder.errorType;
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

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public long durationMs() {
        return durationMs;
    }

    /** Model used, when enriched (e.g. {@code "claude-sonnet"}); otherwise {@code null}. */
    public String model() {
        return model;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    /** Estimated cost, when enriched; otherwise {@code null}. */
    public BigDecimal estimatedCost() {
        return estimatedCost;
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

    public static Builder builder(String capabilityName, String version) {
        return new Builder(capabilityName, version);
    }

    /** Mutable builder assembled across the execution lifecycle and enrichment. */
    public static final class Builder {
        private final String capabilityName;
        private final String version;
        private String domain = "";
        private Instant startedAt;
        private Instant finishedAt;
        private long durationMs;
        private String model;
        private int inputTokens;
        private int outputTokens;
        private BigDecimal estimatedCost;
        private int retries;
        private boolean success = true;
        private String errorType;

        private Builder(String capabilityName, String version) {
            this.capabilityName = capabilityName;
            this.version = version;
        }

        public Builder domain(String domain) {
            this.domain = domain == null ? "" : domain;
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

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder estimatedCost(BigDecimal estimatedCost) {
            this.estimatedCost = estimatedCost;
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

        /** Current model set on this builder (via enrichment), or {@code null}. */
        public String model() {
            return model;
        }

        public int inputTokens() {
            return inputTokens;
        }

        public int outputTokens() {
            return outputTokens;
        }

        /** Current estimated cost set on this builder, or {@code null} if not yet priced. */
        public BigDecimal estimatedCost() {
            return estimatedCost;
        }

        public CapabilityExecution build() {
            return new CapabilityExecution(this);
        }
    }
}
