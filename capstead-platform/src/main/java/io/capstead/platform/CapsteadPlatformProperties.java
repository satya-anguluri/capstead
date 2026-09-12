package io.capstead.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Configuration for the incident-intelligence slice (declared expectations + recording mode). */
public class CapsteadPlatformProperties {

    /** Declared execution contracts (the authoritative Phase-A expectation source). */
    private List<Declaration> expectations = new ArrayList<>();

    /**
     * Whether executions are recorded cross-instance/async. When true, child publications can lag
     * the root and completeness cannot be proven — the completeness policy abstains (SUSPECT).
     */
    private boolean crossInstanceRecording = false;

    public List<Declaration> declarations() {
        return expectations;
    }

    public void setExpectations(List<Declaration> expectations) {
        this.expectations = expectations == null ? new ArrayList<>() : expectations;
    }

    public boolean crossInstanceRecording() {
        return crossInstanceRecording;
    }

    public void setCrossInstanceRecording(boolean crossInstanceRecording) {
        this.crossInstanceRecording = crossInstanceRecording;
    }

    /** One declared expectation. */
    public static class Declaration {
        private String capability;
        private String version;
        private String expectedOperation;
        private boolean required = true;
        private Map<String, String> condition = Map.of();

        public String capability() { return capability; }
        public void setCapability(String capability) { this.capability = capability; }

        public String version() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String expectedOperation() { return expectedOperation; }
        public void setExpectedOperation(String expectedOperation) { this.expectedOperation = expectedOperation; }

        public boolean required() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public Map<String, String> condition() { return condition; }
        public void setCondition(Map<String, String> condition) {
            this.condition = condition == null ? Map.of() : condition;
        }
    }
}