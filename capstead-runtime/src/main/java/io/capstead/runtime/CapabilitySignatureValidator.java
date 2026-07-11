package io.capstead.runtime;

import io.capstead.core.CapabilityException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Governance guard: keeps AI-provider types out of the public capability surface at startup.
 *
 * <p>A {@code @Capability} method should be expressed purely in business terms — the provider
 * (Spring AI, LangChain4j, a vendor SDK) is an implementation detail of the method body. This
 * validator rejects any capability whose return type or parameters reference a known provider
 * type, failing fast with {@link CapabilityException.Reason#CONFIGURATION_ERROR}.
 *
 * <p>The blocklist is package/name based and intentionally conservative; applications can
 * supply additional prefixes.
 */
public final class CapabilitySignatureValidator {

    private static final List<String> DEFAULT_FORBIDDEN_PREFIXES = Arrays.asList(
            "org.springframework.ai",
            "com.openai",
            "com.anthropic",
            "software.amazon.awssdk.services.bedrock",
            "software.amazon.awssdk.services.bedrockruntime");

    private static final List<String> DEFAULT_FORBIDDEN_SIMPLE_NAMES = Arrays.asList(
            "ChatClient", "ChatModel", "StreamingChatModel");

    private final List<String> forbiddenPrefixes;
    private final List<String> forbiddenSimpleNames;

    public CapabilitySignatureValidator() {
        this(DEFAULT_FORBIDDEN_PREFIXES, DEFAULT_FORBIDDEN_SIMPLE_NAMES);
    }

    public CapabilitySignatureValidator(List<String> forbiddenPrefixes,
                                        List<String> forbiddenSimpleNames) {
        this.forbiddenPrefixes = forbiddenPrefixes;
        this.forbiddenSimpleNames = forbiddenSimpleNames;
    }

    /**
     * Validates a capability method's signature.
     *
     * @throws CapabilityException if the return type or any parameter is a provider type
     */
    public void validate(String capabilityName, Method method) {
        check(capabilityName, method, method.getReturnType(), "return type");
        for (Class<?> parameterType : method.getParameterTypes()) {
            check(capabilityName, method, parameterType, "parameter");
        }
    }

    private void check(String capabilityName, Method method, Class<?> type, String role) {
        Class<?> resolved = type.isArray() ? type.getComponentType() : type;
        if (resolved.isPrimitive()) {
            return;
        }
        String fqn = resolved.getName();
        String simpleName = resolved.getSimpleName();

        boolean forbidden = forbiddenPrefixes.stream().anyMatch(fqn::startsWith)
                || forbiddenSimpleNames.contains(simpleName);

        if (forbidden) {
            throw new CapabilityException(
                    CapabilityException.Reason.CONFIGURATION_ERROR,
                    capabilityName,
                    "Capability '" + capabilityName + "' exposes a provider type in its " + role
                            + " (" + fqn + ") on " + method.getDeclaringClass().getName() + "#"
                            + method.getName() + ". Capabilities must be provider-agnostic.");
        }
    }
}
