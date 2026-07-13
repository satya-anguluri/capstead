package io.capstead.starter.declarative;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRendererTest {

    @Test
    void bindsNamedPlaceholders() {
        assertThat(PromptTemplateRenderer.render("Generate a lesson for {{topic}}", Map.of("topic", "Java Streams")))
                .isEqualTo("Generate a lesson for Java Streams");
    }

    @Test
    void toleratesWhitespaceAndMultipleVars() {
        assertThat(PromptTemplateRenderer.render("{{ a }}-{{b}}", Map.of("a", "1", "b", 2))).isEqualTo("1-2");
    }

    @Test
    void leavesUnknownPlaceholderUntouched() {
        assertThat(PromptTemplateRenderer.render("Hi {{missing}}", Map.of())).isEqualTo("Hi {{missing}}");
    }

    @Test
    void rendersNullValueLiterally() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("x", null);
        assertThat(PromptTemplateRenderer.render("[{{x}}]", vars)).isEqualTo("[null]");
    }

    @Test
    void passesThroughNullAndEmptyTemplates() {
        assertThat(PromptTemplateRenderer.render(null, Map.of())).isNull();
        assertThat(PromptTemplateRenderer.render("", Map.of())).isEmpty();
    }
}
