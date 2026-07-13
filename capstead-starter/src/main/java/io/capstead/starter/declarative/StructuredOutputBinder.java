package io.capstead.starter.declarative;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;

/**
 * Turns a declarative capability's raw model text into its typed return value — provider-neutrally.
 *
 * <p>For non-{@code String} return types Capstead appends a {@link #formatInstruction} to the prompt
 * (asking the model for JSON), then {@link #bind}s the response: markdown fences are stripped, the JSON
 * body is extracted, and Jackson maps it to the target type. This replaces Spring AI's structured-output
 * converter so declarative capabilities work with any model backend.
 */
public class StructuredOutputBinder {

    private final ObjectMapper objectMapper;

    public StructuredOutputBinder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** An instruction, appended to the prompt, telling the model to return JSON matching the return type. */
    public String formatInstruction(Type returnType) {
        Class<?> raw = rawClass(returnType);
        boolean array = raw != null && (raw.isArray() || Iterable.class.isAssignableFrom(raw));
        String skeleton = skeleton(raw);
        StringBuilder sb = new StringBuilder("Respond with ONLY a single JSON ")
                .append(array ? "array" : "object")
                .append(" — no prose, no explanation, no markdown code fences");
        if (skeleton != null) {
            sb.append(", matching this shape:\n").append(skeleton);
        } else {
            sb.append('.');
        }
        return sb.toString();
    }

    /** Parses the model text into an instance of {@code returnType}. */
    public Object bind(String text, Type returnType) throws Exception {
        String json = extractJson(text);
        return objectMapper.readValue(json, objectMapper.constructType(returnType));
    }

    private static Class<?> rawClass(Type type) {
        return type instanceof Class<?> c ? c : null;
    }

    private static String skeleton(Class<?> raw) {
        if (raw == null || !raw.isRecord()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        RecordComponent[] components = raw.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(components[i].getName()).append("\": ").append(sample(components[i].getType()));
        }
        return sb.append('}').toString();
    }

    private static String sample(Class<?> type) {
        if (type == String.class) {
            return "\"...\"";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "true";
        }
        if (type.isPrimitive() || Number.class.isAssignableFrom(type)) {
            return "0";
        }
        if (type.isArray() || Iterable.class.isAssignableFrom(type)) {
            return "[...]";
        }
        return "{...}";
    }

    /** Strips markdown fences and returns the outermost JSON object/array in the text. */
    static String extractJson(String text) {
        if (text == null) {
            return "null";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
            t = t.trim();
        }
        int obj = t.indexOf('{');
        int arr = t.indexOf('[');
        int start = arr >= 0 && (obj < 0 || arr < obj) ? arr : obj;
        if (start < 0) {
            return t;
        }
        char close = t.charAt(start) == '{' ? '}' : ']';
        int end = t.lastIndexOf(close);
        return end > start ? t.substring(start, end + 1) : t.substring(start);
    }
}
