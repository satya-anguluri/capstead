package io.capstead.starter.declarative;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.capstead.runtime.CapabilityModelInvoker;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Proxy;

/**
 * Creates the JDK dynamic proxy that backs a declarative {@link io.capstead.annotation.CapabilityClient}
 * interface. One factory bean is registered per discovered interface by {@link CapabilityClientRegistrar}.
 *
 * <p>The proxy is a normal Spring bean, so the standard {@code @Capability} AOP advisor wraps it and
 * records executions. Collaborators — including the application's {@link CapabilityModelInvoker} — are
 * resolved lazily, so the model backend can be defined in any module and in any order.
 */
public class CapabilityClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private Class<T> type;
    private ApplicationContext applicationContext;

    public void setType(Class<T> type) {
        this.type = type;
    }

    public Class<T> getType() {
        return type;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        ModelProfileProperties profiles = applicationContext.getBeanProvider(ModelProfileProperties.class)
                .getIfAvailable(ModelProfileProperties::new);
        StructuredOutputBinder binder = applicationContext.getBeanProvider(StructuredOutputBinder.class)
                .getIfAvailable(() -> new StructuredOutputBinder(new ObjectMapper()));
        CapabilityClientInvocationHandler handler = new CapabilityClientInvocationHandler(
                type,
                () -> applicationContext.getBeanProvider(CapabilityModelInvoker.class).getIfAvailable(),
                profiles,
                binder);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @Override
    public Class<?> getObjectType() {
        return type;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
