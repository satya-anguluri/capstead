package io.capstead.starter.declarative;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named model profiles for declarative capabilities, bound from {@code capstead.ai.profiles.*}.
 *
 * <p>A {@code @ModelProfile("reasoning")} on a capability method resolves to the {@code reasoning}
 * entry here, and its {@code model}/{@code temperature}/… are passed to the
 * {@link io.capstead.runtime.CapabilityModelInvoker} for that call. Profiles decouple capability code
 * from concrete model names, so routing can change via configuration without touching the capability.
 *
 * <pre>{@code
 * capstead:
 *   ai:
 *     profiles:
 *       reasoning: { model: us.anthropic.claude-sonnet-4-6, temperature: 0.2 }
 *       fast:      { model: amazon.nova-pro, max-tokens: 512 }
 * }</pre>
 */
@ConfigurationProperties("capstead.ai")
public class ModelProfileProperties {

    /** Profiles keyed by name (the value used in {@code @ModelProfile}). */
    private Map<String, Profile> profiles = new LinkedHashMap<>();

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, Profile> profiles) {
        this.profiles = profiles == null ? new LinkedHashMap<>() : profiles;
    }

    /** Looks up a profile by name; {@code null} if the name is blank or unconfigured. */
    Profile profile(String name) {
        return name == null || name.isBlank() ? null : profiles.get(name);
    }

    /** A single model profile: the concrete model and optional sampling/limit overrides. */
    public static class Profile {

        /** The concrete model identifier passed to the invoker (e.g. {@code amazon.nova-pro}). */
        private String model;

        /** Optional sampling temperature. */
        private Double temperature;

        /** Optional maximum output tokens. */
        private Integer maxTokens;

        /** Optional nucleus-sampling top-p. */
        private Double topP;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }
    }
}
