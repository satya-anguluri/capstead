package io.capstead.springai;

import io.capstead.runtime.CapabilityModelRequest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiCapabilityModelInvokerTest {

    private final ChatModel model = mock(ChatModel.class);

    @Test
    void callsChatClientWithPromptAndProfileOptionsAndReturnsText() {
        when(model.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("hello world")))));

        SpringAiCapabilityModelInvoker invoker = new SpringAiCapabilityModelInvoker(ChatClient.builder(model));
        String out = invoker.invoke(new CapabilityModelRequest(
                "be terse", "say hi", "test-model", 0.3, 256, null, String.class, "Greeter"));

        assertThat(out).isEqualTo("hello world");

        ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> captor =
                ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(model).call(captor.capture());
        org.springframework.ai.chat.prompt.Prompt sent = captor.getValue();
        assertThat(sent.getContents()).contains("say hi");
        assertThat(sent.getContents()).contains("be terse");
        assertThat(sent.getOptions().getModel()).isEqualTo("test-model");
        assertThat(sent.getOptions().getTemperature()).isEqualTo(0.3);
    }
}
