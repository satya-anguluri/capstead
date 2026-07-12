package io.capstead.starter;

import io.capstead.runtime.CapabilityScanRule;

import org.springframework.aop.support.StaticMethodMatcherPointcut;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Advises every public bean method matched by a {@link CapabilityScanRule}, so configuration-driven
 * capabilities are intercepted (executions, tokens, cost, budget) exactly like annotated ones. The
 * match is purely structural (package + method-name glob), so it works at proxy-creation time
 * without instantiating any bean.
 */
final class ScanRuleMethodMatcherPointcut extends StaticMethodMatcherPointcut {

    private final List<CapabilityScanRule> rules;

    ScanRuleMethodMatcherPointcut(List<CapabilityScanRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (method.getDeclaringClass() == Object.class) {
            return false;
        }
        if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
            return false;
        }
        for (CapabilityScanRule rule : rules) {
            if (rule.matches(targetClass, method)) {
                return true;
            }
        }
        return false;
    }
}
