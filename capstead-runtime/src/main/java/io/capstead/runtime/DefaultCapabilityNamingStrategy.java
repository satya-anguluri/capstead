package io.capstead.runtime;

import java.lang.reflect.Method;

/**
 * Default naming: split the camelCase method name into Title Case words, e.g.
 * {@code generateScene → "Generate Scene"}, {@code plan → "Plan"}, {@code generateHTML → "Generate HTML"}.
 */
public class DefaultCapabilityNamingStrategy implements CapabilityNamingStrategy {

    @Override
    public String toName(Method method) {
        String name = method.getName();
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i == 0) {
                out.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c) && !Character.isUpperCase(name.charAt(i - 1))) {
                out.append(' ').append(c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
