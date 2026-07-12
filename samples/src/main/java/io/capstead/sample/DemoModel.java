package io.capstead.sample;

import io.capstead.runtime.CapabilityExecutionContext;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A stand-in for a real LLM client. It returns canned text and, crucially, records a
 * {@code ModelInvocation} (model + token usage) on the capability currently executing — exactly where
 * a Spring AI observation bridge or your own HTTP client would enrich the execution.
 *
 * <p>Capstead prices each invocation from {@code capstead.cost} (see {@code application.yml}), so the
 * scorecard shows real per-capability cost with no pricing code here.
 */
@Component
public class DemoModel {

    /** "Calls" the model: simulates latency + token usage and records the invocation. */
    public String call(String model, String prompt) {
        int inputTokens = 20 + prompt.length() / 4;
        int outputTokens = 40 + ThreadLocalRandom.current().nextInt(160);

        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(30, 140)); // simulate model latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Attribute this model call to the current @Capability execution (no-op if none in flight).
        CapabilityExecutionContext.recordModelInvocation(model, inputTokens, outputTokens, null);

        return "[" + model + "] " + prompt;
    }
}
