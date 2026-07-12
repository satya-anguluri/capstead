package io.capstead.starter;

import io.capstead.runtime.CapabilityDeclaration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit capability declarations — bind bean methods to capabilities with exact names, for client
 * projects whose method names can't be forced to match capability names:
 *
 * <pre>
 * capstead:
 *   capabilities:
 *     - name: "Generate Lesson"
 *       bean: adminLessonService
 *       method: generate
 *       domain: EngineerPrep
 *       owner: Content Team
 *     - name: "Ask Tutor"
 *       bean: lessonTutorService
 *       method: ask
 *       parameter-types: [java.lang.String, com.acme.AskRequest]   # only for overloads
 * </pre>
 */
@ConfigurationProperties("capstead")
public class CapsteadCapabilitiesProperties {

    private List<Declaration> capabilities = new ArrayList<>();

    public List<Declaration> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<Declaration> capabilities) {
        this.capabilities = capabilities;
    }

    /** Converts the bound declarations into runtime {@link CapabilityDeclaration}s. */
    public List<CapabilityDeclaration> toDeclarations() {
        List<CapabilityDeclaration> out = new ArrayList<>(capabilities.size());
        for (Declaration d : capabilities) {
            out.add(new CapabilityDeclaration(
                    d.bean, d.method, d.parameterTypes, d.name, d.domain, d.owner, d.version, d.tags, d.dailyBudget));
        }
        return out;
    }

    /** A single declaration bound from {@code capstead.capabilities[*]}. */
    public static class Declaration {

        private String name;
        private String bean;
        private String method;
        private List<String> parameterTypes = new ArrayList<>();
        private String domain = "";
        private String owner = "";
        private String version = "1";
        private List<String> tags = new ArrayList<>();
        private String dailyBudget;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBean() {
            return bean;
        }

        public void setBean(String bean) {
            this.bean = bean;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public List<String> getParameterTypes() {
            return parameterTypes;
        }

        public void setParameterTypes(List<String> parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getDailyBudget() {
            return dailyBudget;
        }

        public void setDailyBudget(String dailyBudget) {
            this.dailyBudget = dailyBudget;
        }
    }
}
