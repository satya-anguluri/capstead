package io.capstead.starter.declarative;

import io.capstead.annotation.CapabilityClient;
import io.capstead.annotation.ModelProfile;
import io.capstead.annotation.P;
import io.capstead.annotation.Prompt;
import io.capstead.annotation.SystemPrompt;
import io.capstead.runtime.CapabilityModelInvoker;
import io.capstead.runtime.CapabilityModelRequest;

import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The runtime behind a declarative {@link CapabilityClient}: turns a bodyless capability method into a
 * single {@link CapabilityModelInvoker} call — provider-neutrally.
 *
 * <p>Per invocation it binds arguments to {@code {{name}}} placeholders, renders the {@link Prompt}
 * (and optional {@link SystemPrompt}), resolves the {@link ModelProfile}, appends an output-format
 * instruction for structured returns, calls the application's {@code CapabilityModelInvoker}, and binds
 * the response to the method's return type ({@code String}/{@code void} verbatim, otherwise via
 * {@link StructuredOutputBinder}). Governance (execution recording, tokens, cost, budget, the execution
 * tree) is applied <em>around</em> this handler by the standard {@code @Capability} AOP advisor.
 */
final class CapabilityClientInvocationHandler implements InvocationHandler {

    private final Class<?> type;
    private final Supplier<CapabilityModelInvoker> invoker;
    private final ModelProfileProperties profiles;
    private final StructuredOutputBinder binder;
    private final String defaultProfile;

    CapabilityClientInvocationHandler(Class<?> type,
                                      Supplier<CapabilityModelInvoker> invoker,
                                      ModelProfileProperties profiles,
                                      StructuredOutputBinder binder) {
        this.type = type;
        this.invoker = invoker;
        this.profiles = profiles;
        this.binder = binder;
        this.defaultProfile = resolveInterfaceProfile(type);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        Prompt prompt = AnnotatedElementUtils.findMergedAnnotation(method, Prompt.class);
        if (prompt == null) {
            throw new IllegalStateException(
                    "@Prompt is required on declarative capability method " + method.getName()
                            + " of " + type.getName());
        }

        Map<String, Object> variables = bindVariables(method, args);
        String systemMessage = null;
        SystemPrompt system = AnnotatedElementUtils.findMergedAnnotation(method, SystemPrompt.class);
        if (system != null && !system.value().isBlank()) {
            systemMessage = PromptTemplateRenderer.render(system.value(), variables);
        }
        String userMessage = PromptTemplateRenderer.render(prompt.value(), variables);

        Class<?> returnType = method.getReturnType();
        boolean structured = returnType != String.class && returnType != void.class && returnType != Void.class;
        if (structured) {
            userMessage = userMessage + "\n\n" + binder.formatInstruction(returnType);
        }

        ModelProfileProperties.Profile profile = resolveProfile(method);
        CapabilityModelRequest request = new CapabilityModelRequest(
                systemMessage,
                userMessage,
                profile != null ? profile.getModel() : null,
                profile != null ? profile.getTemperature() : null,
                profile != null ? profile.getMaxTokens() : null,
                profile != null ? profile.getTopP() : null,
                returnType,
                capabilityName(method));

        CapabilityModelInvoker modelInvoker = invoker.get();
        if (modelInvoker == null) {
            throw new IllegalStateException(
                    "No CapabilityModelInvoker bean is available for declarative capability "
                            + type.getName() + "#" + method.getName()
                            + ". Declare a CapabilityModelInvoker bean, or add capstead-spring-ai for a "
                            + "Spring AI ChatClient-backed default.");
        }
        String text = modelInvoker.invoke(request);

        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType == String.class) {
            return text;
        }
        return binder.bind(text, returnType);
    }

    private Map<String, Object> bindVariables(Method method, Object[] args) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (args == null) {
            return variables;
        }
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            P named = parameters[i].getAnnotation(P.class);
            String name = named != null && !named.value().isBlank()
                    ? named.value()
                    : parameters[i].getName();
            variables.put(name, args[i]);
        }
        return variables;
    }

    private ModelProfileProperties.Profile resolveProfile(Method method) {
        ModelProfile methodProfile = AnnotatedElementUtils.findMergedAnnotation(method, ModelProfile.class);
        String name = methodProfile != null && !methodProfile.value().isBlank()
                ? methodProfile.value()
                : defaultProfile;
        return profiles == null ? null : profiles.profile(name);
    }

    private static String capabilityName(Method method) {
        io.capstead.annotation.Capability capability =
                AnnotatedElementUtils.findMergedAnnotation(method, io.capstead.annotation.Capability.class);
        return capability != null ? capability.name() : method.getName();
    }

    private static String resolveInterfaceProfile(Class<?> type) {
        ModelProfile modelProfile = type.getAnnotation(ModelProfile.class);
        if (modelProfile != null && !modelProfile.value().isBlank()) {
            return modelProfile.value();
        }
        CapabilityClient client = type.getAnnotation(CapabilityClient.class);
        if (client != null && !client.profile().isBlank()) {
            return client.profile();
        }
        return null;
    }

    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "toString":
                return type.getName() + "$CapabilityClient";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == (args == null ? null : args[0]);
            default:
                throw new IllegalStateException("Unsupported Object method: " + method);
        }
    }
}
