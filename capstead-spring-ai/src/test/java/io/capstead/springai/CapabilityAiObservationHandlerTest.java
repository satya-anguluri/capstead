package io.capstead.springai;

import io.capstead.core.CapabilityExecution;
import io.capstead.runtime.CapabilityExecutionContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityAiObservationHandlerTest {

    private final CapabilityAiObservationHandler handler = new CapabilityAiObservationHandler();

    @AfterEach
    void clear() {
        CapabilityExecutionContext.clear();
    }

    @Test
    void enrichesInFlightExecutionWithModelAndTokens() {
        CapabilityExecution.Builder builder = CapabilityExecution.builder("Generate Lesson", "1");
        CapabilityExecutionContext.begin(builder);

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("claude-sonnet")
                .usage(new DefaultUsage(1240, 2980))
                .build();
        ChatResponse response = new ChatResponse(List.<Generation>of(), metadata);

        handler.enrich(response);

        CapabilityExecution execution = builder.build();
        assertThat(execution.model()).isEqualTo("claude-sonnet");
        assertThat(execution.inputTokens()).isEqualTo(1240);
        assertThat(execution.outputTokens()).isEqualTo(2980);
        assertThat(execution.totalTokens()).isEqualTo(4220);
    }

    @Test
    void noOpWhenNoExecutionInFlight() {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("claude-sonnet")
                .usage(new DefaultUsage(10, 20))
                .build();
        // No begin() — enrichment must be a safe no-op.
        handler.enrich(new ChatResponse(List.<Generation>of(), metadata));
        assertThat(CapabilityExecutionContext.current()).isEmpty();
    }
}
