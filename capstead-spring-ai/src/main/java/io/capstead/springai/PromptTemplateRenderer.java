package io.capstead.springai;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny, dependency-free renderer for {@code {{name}}} prompt templates.
 *
 * <p>Placeholders may contain surrounding whitespace ({@code {{ topic }}}) and dotted/underscored
 * names. A placeholder whose variable is not supplied is left untouched, so partially-bound templates
 * fail loudly and visibly rather than silently emitting blanks.
 */
final class PromptTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.$-]+)\\s*}}");

    private PromptTemplateRenderer() {
    }

    static String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = variables != null && variables.containsKey(key)
                    ? String.valueOf(variables.get(key))
                    : matcher.group(0);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
