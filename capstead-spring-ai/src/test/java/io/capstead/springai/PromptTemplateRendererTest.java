package io.capstead.springai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRendererTest {

    @Test
    void bindsNamedPlaceholders() {
        Map<String, Object> vars = Map.of("topic", "Java Streams");
        assertThat(PromptTemplateRenderer.render("Generate a lesson for {{topic}}", vars))
                .isEqualTo("Generate a lesson for Java Streams");
    }

    @Test
    void toleratesWhitespaceAndMultipleVars() {
        Map<String, Object> vars = Map.of("a", "1", "b", 2);
        assertThat(PromptTemplateRenderer.render("{{ a }}-{{b}}", vars)).isEqualTo("1-2");
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
