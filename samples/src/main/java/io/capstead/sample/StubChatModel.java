package io.capstead.sample;

import io.capstead.runtime.CapabilityExecutionContext;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A canned Spring AI {@link ChatModel} so the declarative-capability demo runs with no API keys.
 *
 * <p>It returns fixed JSON (bound to {@link Quiz}) and records the model name and token usage on the
 * in-flight capability execution — exactly where a real provider client, or Spring AI's observation
 * bridge, would enrich it. The model name comes from the profile selected by
 * {@code @ModelProfile("reasoning")}, so the scorecard shows real routing and cost.
 */
@Component
public class StubChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        String model = prompt.getOptions() != null && prompt.getOptions().getModel() != null
                ? prompt.getOptions().getModel()
                : "demo-model";
        int inputTokens = 30 + prompt.getContents().length() / 4;
        int outputTokens = 60 + ThreadLocalRandom.current().nextInt(120);
        simulateLatency();
        CapabilityExecutionContext.recordModelInvocation(model, inputTokens, outputTokens, null);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(cannedQuizJson()))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().build();
    }

    private static String cannedQuizJson() {
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
