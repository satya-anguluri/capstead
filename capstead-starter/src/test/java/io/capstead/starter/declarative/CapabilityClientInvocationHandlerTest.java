package io.capstead.starter.declarative;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.capstead.annotation.Capability;
import io.capstead.annotation.CapabilityClient;
import io.capstead.annotation.ModelProfile;
import io.capstead.annotation.P;
import io.capstead.annotation.Prompt;
import io.capstead.annotation.SystemPrompt;
import io.capstead.runtime.CapabilityModelInvoker;
import io.capstead.runtime.CapabilityModelRequest;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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

    private final AtomicReference<CapabilityModelRequest> captured = new AtomicReference<>();

    private LessonClient client(String cannedResponse, ModelProfileProperties profiles) {
        CapabilityModelInvoker invoker = request -> {
            captured.set(request);
            return cannedResponse;
        };
        StructuredOutputBinder binder = new StructuredOutputBinder(new ObjectMapper());
        return proxy(LessonClient.class, () -> invoker, profiles, binder);
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
    void rendersPromptBindsEntityAndPassesProfileToInvoker() {
        Lesson lesson = client("{\"title\":\"Java Streams\"}", reasoningProfile()).generate("Java Streams");

        assertThat(lesson.title()).isEqualTo("Java Streams");
        CapabilityModelRequest request = captured.get();
        assertThat(request.userPrompt()).contains("Generate a Java lesson for Java Streams");
        assertThat(request.userPrompt()).contains("JSON object"); // structured-output instruction appended
        assertThat(request.model()).isEqualTo("test-model");
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.capabilityName()).isEqualTo("Generate Lesson");
    }

    @Test
    void returnsRawStringBindsNamedParametersAndSystemPrompt() {
        String out = client("short summary", reasoningProfile()).summarize("a long document", 5);

        assertThat(out).isEqualTo("short summary");
        CapabilityModelRequest request = captured.get();
        assertThat(request.userPrompt()).isEqualTo("Summarize a long document in 5 words");
        assertThat(request.userPrompt()).doesNotContain("JSON object"); // String return -> no format instruction
        assertThat(request.systemPrompt()).isEqualTo("You are terse.");
    }

    @Test
    void appliesNoModelWhenProfileUnconfigured() {
        client("{\"title\":\"x\"}", new ModelProfileProperties()).generate("Topic");
        assertThat(captured.get().model()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,
                               java.util.function.Supplier<CapabilityModelInvoker> invoker,
                               ModelProfileProperties profiles,
                               StructuredOutputBinder binder) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                new CapabilityClientInvocationHandler(type, invoker, profiles, binder));
    }
}
