package io.capstead.starter.declarative;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputBinderTest {

    record Lesson(String title, int minutes) {
    }

    private final StructuredOutputBinder binder = new StructuredOutputBinder(new ObjectMapper());

    @Test
    void formatInstructionDescribesTheRecordShape() {
        String instruction = binder.formatInstruction(Lesson.class);
        assertThat(instruction).contains("JSON object");
        assertThat(instruction).contains("\"title\"");
        assertThat(instruction).contains("\"minutes\"");
    }

    @Test
    void bindsPlainJson() throws Exception {
        Object bound = binder.bind("{\"title\":\"Streams\",\"minutes\":5}", Lesson.class);
        assertThat(bound).isEqualTo(new Lesson("Streams", 5));
    }

    @Test
    void stripsMarkdownFencesBeforeBinding() throws Exception {
        String fenced = "```json\n{\"title\":\"Records\",\"minutes\":8}\n```";
        assertThat(binder.bind(fenced, Lesson.class)).isEqualTo(new Lesson("Records", 8));
    }

    @Test
    void extractsJsonEmbeddedInProse() throws Exception {
        String messy = "Sure! Here you go: {\"title\":\"Loops\",\"minutes\":3} — hope that helps.";
        assertThat(binder.bind(messy, Lesson.class)).isEqualTo(new Lesson("Loops", 3));
    }
}
