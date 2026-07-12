package io.capstead.mcp;

import io.capstead.annotation.Capability;
import io.capstead.core.CapabilityMetadata;
import io.capstead.runtime.CapabilityDescriptor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link CapabilityToolMapper} name sanitization and schema derivation. */
class CapabilityToolMapperTest {

    private final CapabilityToolMapper mapper = new CapabilityToolMapper();

    @Test
    void derivesMcpSafeToolNameFromCoordinates() {
        CapabilityMetadata metadata = new CapabilityMetadata(
                "Generate Lesson", "", "EngineerPrep", "Content", "2", List.of());

        assertThat(mapper.toolName(metadata)).isEqualTo("generate_lesson_v2");
    }

    @Test
    void buildsJsonSchemaFromParameterTypes() throws Exception {
        Method method = Sample.class.getDeclaredMethod("plan", String.class, int.class, boolean.class);
        CapabilityMetadata metadata = new CapabilityMetadata(
                "Plan", "Plans work", "Ops", "SRE", "1", List.of("planning"));
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                metadata, new Sample(), method, new String[] {"topic", "count", "verbose"});

        CapabilityTool tool = mapper.toTool(descriptor);

        assertThat(tool.name()).isEqualTo("plan_v1");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
        assertThat(properties.get("topic")).isEqualTo(Map.of("type", "string"));
        assertThat(properties.get("count")).isEqualTo(Map.of("type", "integer"));
        assertThat(properties.get("verbose")).isEqualTo(Map.of("type", "boolean"));
        assertThat(tool.inputSchema().get("required")).isEqualTo(List.of("topic", "count", "verbose"));
    }

    static class Sample {
        @Capability(name = "Plan", version = "1")
        public String plan(String topic, int count, boolean verbose) {
            return topic + count + verbose;
        }
    }
}
