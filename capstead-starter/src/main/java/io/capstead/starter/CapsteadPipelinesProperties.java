package io.capstead.starter;

import io.capstead.core.PipelineDefinition;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Declared business pipelines — group existing capabilities into an ordered, end-to-end flow whose
 * runs are assembled from recorded executions at read time. Config only: no code or schema changes
 * in the application, and historical executions are grouped retroactively.
 *
 * <pre>
 * capstead:
 *   pipelines:
 *     - name: "Generate Lesson Pipeline"
 *       domain: EngineerPrep
 *       owner: Content Team
 *       steps: ["Author Lesson", "Write Episode", "Synthesize Episode Audio",
 *               "Generate Assessments", "Generate GitHub Repo"]
 *       max-gap: 5m          # optional; default 10m
 * </pre>
 */
@ConfigurationProperties("capstead")
public class CapsteadPipelinesProperties {

    private List<Pipeline> pipelines = new ArrayList<>();

    public List<Pipeline> getPipelines() {
        return pipelines;
    }

    public void setPipelines(List<Pipeline> pipelines) {
        this.pipelines = pipelines;
    }

    /** Converts the bound declarations into {@link PipelineDefinition}s (fail-fast on bad config). */
    public List<PipelineDefinition> toDefinitions() {
        List<PipelineDefinition> out = new ArrayList<>(pipelines.size());
        for (Pipeline p : pipelines) {
            out.add(new PipelineDefinition(p.name, p.domain, p.owner, p.description, p.steps, p.maxGap));
        }
        return out;
    }

    /** A single declaration bound from {@code capstead.pipelines[*]}. */
    public static class Pipeline {

        private String name;
        private String domain = "";
        private String owner = "";
        private String description = "";
        private List<String> steps = new ArrayList<>();
        private Duration maxGap;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getSteps() {
            return steps;
        }

        public void setSteps(List<String> steps) {
            this.steps = steps;
        }

        public Duration getMaxGap() {
            return maxGap;
        }

        public void setMaxGap(Duration maxGap) {
            this.maxGap = maxGap;
        }
    }
}
