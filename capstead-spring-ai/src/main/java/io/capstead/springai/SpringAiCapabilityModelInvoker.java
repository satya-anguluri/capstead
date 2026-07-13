package io.capstead.springai;

import io.capstead.runtime.CapabilityModelInvoker;
import io.capstead.runtime.CapabilityModelRequest;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * The default {@link CapabilityModelInvoker} for applications that use Spring AI: dispatches a
 * declarative capability's model call through a {@code ChatClient}.
 *
 * <p>It applies the resolved profile as {@code ChatOptions} and returns the model's raw text — Capstead
 * performs prompt rendering and output binding, so this adapter stays thin. Applications not using
 * Spring AI provide their own {@code CapabilityModelInvoker} bean instead (this one is
 * {@code @ConditionalOnMissingBean}).
 */
public class SpringAiCapabilityModelInvoker implements CapabilityModelInvoker {

    private final ChatClient.Builder chatClientBuilder;

    public SpringAiCapabilityModelInvoker(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @Override
    public String invoke(CapabilityModelRequest request) {
        ChatClient.ChatClientRequestSpec spec = chatClientBuilder.build().prompt();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            spec = spec.system(request.systemPrompt());
        }
        spec = spec.user(request.userPrompt());
        ChatOptions options = options(request);
        if (options != null) {
            spec = spec.options(options);
        }
        return spec.call().content();
    }

    private static ChatOptions options(CapabilityModelRequest request) {
        if (request.model() == null && request.temperature() == null
                && request.maxTokens() == null && request.topP() == null) {
            return null;
        }
        ChatOptions.Builder builder = ChatOptions.builder();
        if (request.model() != null && !request.model().isBlank()) {
            builder.model(request.model());
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        if (request.maxTokens() != null) {
            builder.maxTokens(request.maxTokens());
        }
        if (request.topP() != null) {
            builder.topP(request.topP());
        }
        return builder.build();
    }
}
