package io.capstead.sample;

import io.capstead.runtime.CapabilityExecutionContext;
import io.capstead.runtime.CapabilityModelInvoker;
import io.capstead.runtime.CapabilityModelRequest;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A canned {@link CapabilityModelInvoker} so the declarative-capability demo runs with no API keys —
 * and, notably, <strong>without Spring AI</strong>. This is exactly the one-method bean any project
 * (LangChain4j, a provider SDK, a plain HTTP client) implements to power {@code @CapabilityClient}.
 *
 * <p>It returns fixed JSON (Capstead binds it to {@link Quiz}) and records the model name + token usage
 * on the in-flight capability execution — so the scorecard shows real routing and cost.
 */
@Component
public class StubModelInvoker implements CapabilityModelInvoker {

    @Override
    public String invoke(CapabilityModelRequest request) {
        String model = request.model() != null ? request.model() : "demo-model";
        String prompt = request.userPrompt() == null ? "" : request.userPrompt();
        int inputTokens = 30 + prompt.length() / 4;
        int outputTokens = 60 + ThreadLocalRandom.current().nextInt(120);
        simulateLatency();
        CapabilityExecutionContext.recordModelInvocation(model, inputTokens, outputTokens, null);
        return "{\"question\":\"Which Java feature lets a Stream process elements lazily?\","
                + "\"answer\":\"Intermediate operations — they are only evaluated when a terminal operation runs.\"}";
    }

    private static void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(40, 160));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
