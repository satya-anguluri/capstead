package io.capstead.runtime;

import io.capstead.core.CapabilityExecution;
import io.capstead.core.ModelInvocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Thread-local access to the capability execution(s) currently in flight.
 *
 * <p>This is Capstead's enrichment seam. Because Capstead does not execute the model, it cannot
 * observe token usage, model name or cost directly — those are supplied by whoever <em>does</em>
 * make the call:
 *
 * <pre>{@code
 * CapabilityExecutionContext.recordModelInvocation("claude-sonnet", 1240, 2980, new BigDecimal("0.023"));
 * }</pre>
 *
 * <p>Executions are held on a <strong>stack</strong>, so when one {@code @Capability} calls another
 * (a composed capability), the nested execution is automatically linked to its parent via
 * {@code parentExecutionId} — giving an execution tree with no workflow engine. The
 * {@code begin}/{@code clear} lifecycle is managed by {@link CapabilityMethodInterceptor}; enrichment
 * calls are no-ops when no execution is active.
 */
public final class CapabilityExecutionContext {

    private static final Logger log = System.getLogger(CapabilityExecutionContext.class.getName());

    private static final ThreadLocal<Deque<CapabilityExecution.Builder>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * The allow-list consulted by {@link #recordAttribute(String, String)}.
     *
     * <p>Static because this whole class is: enrichment is called from application code that has no
     * handle on a Capstead bean, which is the reason the seam is static in the first place. Auto-
     * configuration installs the application's registry at startup.
     *
     * <p>Defaults to {@link ExecutionAttributeRegistry#empty()}, so an application that has declared
     * nothing records nothing rather than everything.
     */
    private static volatile ExecutionAttributeRegistry registry = ExecutionAttributeRegistry.empty();

    /** Names already warned about, so a rejected attribute inside a loop logs once rather than per call. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CapabilityExecutionContext() {
    }

    /**
     * Marks the start of an execution on the current thread: assigns its {@code executionId} (if
     * unset) and links it to the enclosing execution's id, then pushes it onto the stack.
     */
    public static void begin(CapabilityExecution.Builder builder) {
        Deque<CapabilityExecution.Builder> stack = STACK.get();
        if (builder.executionId() == null) {
            builder.executionId(UUID.randomUUID().toString());
        }
        CapabilityExecution.Builder parent = stack.peek();
        if (parent != null && builder.parentExecutionId() == null) {
            builder.parentExecutionId(parent.executionId());
        }
        stack.push(builder);
    }

    /** The in-flight execution builder (top of the stack), if any. */
    public static Optional<CapabilityExecution.Builder> current() {
        return Optional.ofNullable(STACK.get().peek());
    }

    /** Ends the current execution on the thread by popping the stack. */
    public static void clear() {
        Deque<CapabilityExecution.Builder> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /** Records one model/LLM call against the current execution (the multi-call API). */
    public static void recordModelInvocation(String model, int inputTokens, int outputTokens, BigDecimal estimatedCost) {
        current().ifPresent(builder -> builder.addModelInvocation(
                new ModelInvocation(model, inputTokens, outputTokens, estimatedCost, Instant.now())));
    }

    /** Records the principal (caller) for the current execution. */
    public static void recordPrincipal(String principal) {
        current().ifPresent(builder -> builder.principal(principal));
    }

    /**
     * Enriches the current execution with the model used. Back-compat single-invocation API — for
     * multiple calls, prefer {@link #recordModelInvocation}.
     */
    public static void recordModel(String model) {
        current().ifPresent(builder -> builder.model(model));
    }

    /** Enriches the current execution with token usage (back-compat single-invocation API). */
    public static void recordTokens(int inputTokens, int outputTokens) {
        current().ifPresent(builder -> builder.inputTokens(inputTokens).outputTokens(outputTokens));
    }

    /** Enriches the current execution with an estimated cost (back-compat single-invocation API). */
    public static void recordCost(BigDecimal estimatedCost) {
        current().ifPresent(builder -> builder.estimatedCost(estimatedCost));
    }

    /** Enriches the current execution with a retry count. */
    public static void recordRetries(int retries) {
        current().ifPresent(builder -> builder.retries(retries));
    }

    /**
     * Record what this execution decided, or which versioned input it acted on.
     *
     * <pre>{@code
     * CapabilityExecutionContext.recordAttribute("policy.authorization.outcome", "DENY");
     * CapabilityExecutionContext.recordAttribute("evidence.sourceRevision", "7");
     * }</pre>
     *
     * <p>A no-op when no execution is active, like every other enrichment call here.
     *
     * <p><b>Silently does nothing if the name is not declared in the registry</b>, or if the name or value
     * breaks the rules in {@code ExecutionAttributes}. It never throws: this runs inside a business method
     * that must not fail because of how it is being measured. A rejection is logged once per name, at WARN,
     * naming the attribute — which is how a developer finds out, without a loop filling the log.
     */
    public static void recordAttribute(String name, String value) {
        current().ifPresent(builder -> {
            if (!registry.allows(name)) {
                warnOnce(name, "it is not declared in capstead.attributes.allowed");
                return;
            }
            if (builder.attributeRejected(name, value)) {
                warnOnce(name, "the name or value breaks the attribute rules, or this execution is at the"
                        + " limit of " + io.capstead.core.ExecutionAttributes.MAX_PER_EXECUTION);
                return;
            }
            builder.attribute(name, value);
        });
    }

    private static void warnOnce(String name, String because) {
        if (WARNED.add(String.valueOf(name))) {
            log.log(Level.WARNING, "[capstead] attribute ''{0}'' was not recorded: {1}", name, because);
        }
    }

    /**
     * Install the application's attribute allow-list. Called by auto-configuration at startup.
     *
     * <p>Also clears the warned-name set, so a test or a restart that changes the registry can be warned
     * about the same name again rather than being silently different from a fresh JVM.
     */
    public static void useRegistry(ExecutionAttributeRegistry replacement) {
        registry = replacement == null ? ExecutionAttributeRegistry.empty() : replacement;
        WARNED.clear();
    }

    /** The installed allow-list. Never null. */
    public static ExecutionAttributeRegistry registry() {
        return registry;
    }
}
