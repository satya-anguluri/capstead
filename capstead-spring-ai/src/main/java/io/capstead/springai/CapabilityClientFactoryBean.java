package io.capstead.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Proxy;

/**
 * Creates the JDK dynamic proxy that backs a declarative {@link io.capstead.annotation.CapabilityClient}
 * interface. One factory bean is registered per discovered interface by {@link CapabilityClientRegistrar}.
 *
 * <p>The proxy is a normal Spring bean, so the standard {@code @Capability} AOP advisor wraps it and
 * records executions — the factory only wires the model-calling {@link CapabilityClientInvocationHandler}.
 * Collaborators are looked up lazily so this factory imposes no ordering constraints during context
 * startup.
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
        ChatClient.Builder builder = applicationContext.getBean(ChatClient.Builder.class);
        ModelProfileProperties props = applicationContext.getBeanProvider(ModelProfileProperties.class)
                .getIfAvailable(ModelProfileProperties::new);
        CapabilityClientInvocationHandler handler =
                new CapabilityClientInvocationHandler(type, builder, props);
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
