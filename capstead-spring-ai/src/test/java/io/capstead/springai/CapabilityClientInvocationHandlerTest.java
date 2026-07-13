package io.capstead.springai;

import io.capstead.annotation.Capability;
import io.capstead.annotation.CapabilityClient;
import io.capstead.annotation.ModelProfile;
import io.capstead.annotation.P;
import io.capstead.annotation.Prompt;
import io.capstead.annotation.SystemPrompt;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityClientInvocationHandlerTest {

    record Lesson(String title) {
    }

    @CapabilityClient
    @ModelProfile("reasoning")
    interface LessonClient {

        @Capability(name = "Generate Lesson", domain = "Learning", version = "1")
        @Prompt("Generate a Java lesson for {{topic}}")
        Lesson generate(String topic);

        @Capability(name = "Summarize", version = "1")
        @SystemPrompt("You are terse.")
        @Prompt("Summarize {{text}} in {{words}} words")
        String summarize(@P("text") String content, @P("words") int words);
    }

    private final ChatModel model = mock(ChatModel.class);

    private LessonClient client(ModelProfileProperties profiles) {
        when(model.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        ChatClient.Builder builder = ChatClient.builder(model);
        return proxy(LessonClient.class, builder, profiles);
    }

    private static ModelProfileProperties reasoningProfile() {
        ModelProfileProperties props = new ModelProfileProperties();
        ModelProfileProperties.Profile profile = new ModelProfileProperties.Profile();
        profile.setModel("test-model");
        profile.setTemperature(0.2);
        props.getProfiles().put("reasoning", profile);
        return props;
    }

    @Test
    void rendersPromptBindsEntityAndAppliesProfileModel() {
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(response("{\"title\":\"Java Streams\"}"));

        Lesson lesson = client(reasoningProfile()).generate("Java Streams");

        assertThat(lesson.title()).isEqualTo("Java Streams");

        org.springframework.ai.chat.prompt.Prompt sent = capturePrompt();
        assertThat(sent.getContents()).contains("Generate a Java lesson for Java Streams");
        assertThat(sent.getOptions().getModel()).isEqualTo("test-model");
        assertThat(sent.getOptions().getTemperature()).isEqualTo(0.2);
    }

    @Test
    void returnsRawStringAndBindsNamedParametersAndSystemPrompt() {
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(response("short summary"));

        String out = client(reasoningProfile()).summarize("a long document", 5);

        assertThat(out).isEqualTo("short summary");
        org.springframework.ai.chat.prompt.Prompt sent = capturePrompt();
        assertThat(sent.getContents()).contains("Summarize a long document in 5 words");
        assertThat(sent.getContents()).contains("You are terse.");
    }

    @Test
    void appliesNoOptionsWhenProfileUnconfigured() {
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(response("{\"title\":\"x\"}"));

        client(new ModelProfileProperties()).generate("Topic");

        org.springframework.ai.chat.prompt.Prompt sent = capturePrompt();
        assertThat(sent.getOptions().getModel()).isNull();
    }

    private org.springframework.ai.chat.prompt.Prompt capturePrompt() {
        ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> captor =
                ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(model).call(captor.capture());
        return captor.getValue();
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ChatClient.Builder builder, ModelProfileProperties profiles) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                new CapabilityClientInvocationHandler(type, builder, profiles));
    }
}
