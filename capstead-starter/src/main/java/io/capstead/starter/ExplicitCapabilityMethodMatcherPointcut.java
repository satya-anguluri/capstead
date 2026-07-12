package io.capstead.starter;

import org.springframework.aop.support.StaticMethodMatcherPointcut;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Advises exactly the methods bound by explicit {@code capstead.capabilities} declarations, so they
 * are intercepted (executions, tokens, cost, budget) like annotated capabilities.
 */
final class ExplicitCapabilityMethodMatcherPointcut extends StaticMethodMatcherPointcut {

    private final Set<Method> methods;

    ExplicitCapabilityMethodMatcherPointcut(Set<Method> methods) {
        this.methods = Set.copyOf(methods);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return methods.contains(method);
    }
}
