package io.capstead.runtime;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A convention-based rule that turns many bean methods into governed capabilities without touching
 * their source: any public method in {@code basePackage} (or a sub-package) whose name matches
 * {@code methods} (a glob, e.g. {@code "generate*"}) becomes a capability, inheriting this rule's
 * governance defaults. The capability name is derived from the method name by a
 * {@link CapabilityNamingStrategy}.
 */
public final class CapabilityScanRule {

    private final String basePackage;
    private final Pattern methodPattern;
    private final String domain;
    private final String owner;
    private final String version;
    private final List<String> tags;
    private final String dailyBudget;

    public CapabilityScanRule(String basePackage, String methodGlob, String domain, String owner,
                              String version, List<String> tags, String dailyBudget) {
        this.basePackage = basePackage == null ? "" : basePackage.trim();
        this.methodPattern = globToPattern(methodGlob == null || methodGlob.isBlank() ? "*" : methodGlob.trim());
        this.domain = domain == null ? "" : domain;
        this.owner = owner == null ? "" : owner;
        this.version = version == null || version.isBlank() ? "1" : version;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.dailyBudget = dailyBudget;
    }

    /** Whether the given method on the given target class is covered by this rule. */
    public boolean matches(Class<?> targetClass, Method method) {
        if (basePackage.isEmpty()) {
            return false;
        }
        String className = targetClass.getName();
        boolean inPackage = className.startsWith(basePackage + ".") || className.equals(basePackage);
        return inPackage && methodPattern.matcher(method.getName()).matches();
    }

    public String domain() {
        return domain;
    }

    public String owner() {
        return owner;
    }

    public String version() {
        return version;
    }

    public List<String> tags() {
        return tags;
    }

    public String dailyBudget() {
        return dailyBudget;
    }

    private static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder(glob.length() + 4);
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
        }
        return Pattern.compile(regex.toString());
    }
}
