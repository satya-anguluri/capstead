package io.capstead.runtime;

import io.capstead.core.CapabilityException;
import io.capstead.core.CapabilityMetadata;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRegistryTest {

    static class LessonService {
        public Object generateLesson(String topic) {
            return null; // body would call Spring AI / LangChain4j; not Capstead's concern
        }
    }

    private static CapabilityDescriptor descriptor(String name, String version) throws Exception {
        Method method = LessonService.class.getMethod("generateLesson", String.class);
        CapabilityMetadata metadata = new CapabilityMetadata(
                name, "Generates a lesson", "EngineerPrep", "Content Team", version, List.of("lesson", "java"));
        return new CapabilityDescriptor(metadata, new LessonService(), method, new String[] {"topic"});
    }

    @Test
    void catalogExposesMetadataOnly() throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(descriptor("Generate Lesson", "1"));
        registry.register(descriptor("Generate Quiz", "1"));

        List<CapabilityMetadata> capabilities = new CapabilityCatalog(registry).capabilities();

        assertEquals(2, capabilities.size());
        assertEquals("Generate Lesson", capabilities.get(0).name());
        assertEquals("EngineerPrep", capabilities.get(0).domain());
        assertEquals("Generate Lesson@1", capabilities.get(0).coordinates());
    }

    @Test
    void sameNameDifferentVersionsCoexist() throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(descriptor("Generate Lesson", "1"));
        registry.register(descriptor("Generate Lesson", "2"));

        List<CapabilityMetadata> versions = new CapabilityCatalog(registry).versionsOf("Generate Lesson");

        assertEquals(2, versions.size());
        assertTrue(registry.find("Generate Lesson@2").isPresent());
    }

    @Test
    void duplicateCoordinatesAreRejected() throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(descriptor("Generate Lesson", "1"));

        CapabilityException ex = assertThrows(CapabilityException.class,
                () -> registry.register(descriptor("Generate Lesson", "1")));
        assertEquals(CapabilityException.Reason.DUPLICATE_COORDINATES, ex.reason());
    }
}
