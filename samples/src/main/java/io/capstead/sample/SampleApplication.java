package io.capstead.sample;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Capstead sample application.
 *
 * <p>Run it, then open the dashboard at <a href="http://localhost:8080/capstead">/capstead</a> and the
 * actuator endpoints — the capabilities below have already been exercised at startup, so scorecards
 * and execution history are populated.
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    /**
     * The {@code ChatClient.Builder} that declarative capabilities call through. Backed by the demo
     * {@link StubChatModel}, so the sample runs with no API keys.
     */
    @Bean
    ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
