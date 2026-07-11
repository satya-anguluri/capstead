package io.capstead.springai;

import io.capstead.runtime.CapabilityExecutionContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;

/**
 * Bridges Spring AI's per-model-call observations onto Capstead's capability execution.
 *
 * <p>Spring AI already emits {@code gen_ai} model, token and usage data for every {@code ChatModel}
 * call. This handler listens for those observations and attributes the model name and token usage to
 * the capability execution currently in flight (via {@link CapabilityExecutionContext}). The result:
 * token/model data on a business capability's scorecard with <em>zero</em> application code — we
 * consume Spring AI's measurements rather than reinventing them.
 *
 * <p>Because the model call happens on the same thread as the intercepted {@code @Capability} method,
 * the thread-local enrichment lands on the correct execution.
 */
public class CapabilityAiObservationHandler implements ObservationHandler<ChatModelObservationContext> {

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

    @Override
    public void onStop(ChatModelObservationContext context) {
        enrich(context.getResponse());
    }

    /** Package-visible for testing: applies a chat response's model/token data to the current execution. */
    void enrich(ChatResponse response) {
        if (response == null) {
            return;
        }
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata == null) {
            return;
        }

        String model = metadata.getModel();
        if (model != null && !model.isBlank()) {
            CapabilityExecutionContext.recordModel(model);
        }

        Usage usage = metadata.getUsage();
        if (usage != null) {
            Integer input = usage.getPromptTokens();
            Integer output = usage.getCompletionTokens();
            CapabilityExecutionContext.recordTokens(input == null ? 0 : input, output == null ? 0 : output);
        }
    }
}
