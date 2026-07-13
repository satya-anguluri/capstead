package io.capstead.springai;

import io.capstead.annotation.CapabilityClient;
import io.capstead.annotation.ModelProfile;
import io.capstead.annotation.P;
import io.capstead.annotation.Prompt;
import io.capstead.annotation.SystemPrompt;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The runtime behind a declarative {@link CapabilityClient}: turns a bodyless capability method into
 * a Spring AI {@code ChatClient} call.
 *
 * <p>Per invocation it binds arguments to {@code {{name}}} placeholders, renders the {@link Prompt}
 * (and optional {@link SystemPrompt}), resolves the {@link ModelProfile} to {@code ChatOptions}, calls
 * the model, and binds the response to the method's return type ({@code String} verbatim, otherwise a
 * structured entity). Governance (execution recording, tokens, cost, budget, the execution tree) is
 * applied <em>around</em> this handler by the standard {@code @Capability} AOP advisor — this class
 * only supplies the model-calling body.
 */
final class CapabilityClientInvocationHandler implements InvocationHandler {

    private final Class<?> type;
    private final ChatClient.Builder chatClientBuilder;
    private final ModelProfileProperties profiles;
    private final String defaultProfile;

    CapabilityClientInvocationHandler(Class<?> type,
                                      ChatClient.Builder chatClientBuilder,
                                      ModelProfileProperties profiles) {
        this.type = type;
        this.chatClientBuilder = chatClientBuilder;
        this.profiles = profiles;
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
        String userMessage = PromptTemplateRenderer.render(prompt.value(), variables);

        ChatClient.ChatClientRequestSpec spec = chatClientBuilder.build().prompt();
        SystemPrompt system = AnnotatedElementUtils.findMergedAnnotation(method, SystemPrompt.class);
        if (system != null && !system.value().isBlank()) {
            spec = spec.system(PromptTemplateRenderer.render(system.value(), variables));
        }
        spec = spec.user(userMessage);
        ChatOptions options = resolveOptions(method);
        if (options != null) {
            spec = spec.options(options);
        }

        ChatClient.CallResponseSpec call = spec.call();
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            call.content();
            return null;
        }
        if (returnType == String.class) {
            return call.content();
        }
        return call.entity(returnType);
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

    private ChatOptions resolveOptions(Method method) {
        ModelProfile methodProfile = AnnotatedElementUtils.findMergedAnnotation(method, ModelProfile.class);
        String name = methodProfile != null && !methodProfile.value().isBlank()
                ? methodProfile.value()
                : defaultProfile;
        ModelProfileProperties.Profile profile = profiles == null ? null : profiles.profile(name);
        if (profile == null) {
            return null;
        }
        ChatOptions.Builder builder = ChatOptions.builder();
        boolean any = false;
        if (profile.getModel() != null && !profile.getModel().isBlank()) {
            builder.model(profile.getModel());
            any = true;
        }
        if (profile.getTemperature() != null) {
            builder.temperature(profile.getTemperature());
            any = true;
        }
        if (profile.getMaxTokens() != null) {
            builder.maxTokens(profile.getMaxTokens());
            any = true;
        }
        if (profile.getTopP() != null) {
            builder.topP(profile.getTopP());
            any = true;
        }
        return any ? builder.build() : null;
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
