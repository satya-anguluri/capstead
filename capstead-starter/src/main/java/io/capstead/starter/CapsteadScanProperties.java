package io.capstead.starter;

import io.capstead.runtime.CapabilityScanRule;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Convention-based capability declaration. Instead of listing every capability, declare a few rules
 * that match many methods by package + method-name glob — the scalable way to instrument dozens of
 * capabilities without source changes:
 *
 * <pre>
 * capstead:
 *   scan:
 *     - base-package: com.acme.ai.services
 *       methods: "generate*"
 *       domain: Content
 *       owner: Platform Team
 * </pre>
 */
@ConfigurationProperties("capstead")
public class CapsteadScanProperties {

    private List<ScanRule> scan = new ArrayList<>();

    public List<ScanRule> getScan() {
        return scan;
    }

    public void setScan(List<ScanRule> scan) {
        this.scan = scan;
    }

    /** Converts the bound rules into runtime {@link CapabilityScanRule}s. */
    public List<CapabilityScanRule> toRules() {
        List<CapabilityScanRule> rules = new ArrayList<>(scan.size());
        for (ScanRule rule : scan) {
            rules.add(new CapabilityScanRule(
                    rule.basePackage, rule.methods, rule.domain, rule.owner, rule.version, rule.tags, rule.dailyBudget));
        }
        return rules;
    }

    /** A single scan rule bound from {@code capstead.scan[*]}. */
    public static class ScanRule {

        private String basePackage;
        private String methods = "*";
        private String domain = "";
        private String owner = "";
        private String version = "1";
        private List<String> tags = new ArrayList<>();
        private String dailyBudget;

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }

        public String getMethods() {
            return methods;
        }

        public void setMethods(String methods) {
            this.methods = methods;
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
