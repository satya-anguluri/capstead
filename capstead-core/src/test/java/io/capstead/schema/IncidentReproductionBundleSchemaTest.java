package io.capstead.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentReproductionBundleSchemaTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonSchema schema;
    private static JsonNode fixture;

    @BeforeAll
    static void loadContract() throws IOException {
        try (InputStream schemaStream = resource("/schema/incident-reproduction-bundle-v1.json");
             InputStream fixtureStream = resource("/schema/incident-reproduction-bundle-v1.fixture.json")) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(schemaStream);
            fixture = JSON.readTree(fixtureStream);
        }
    }

    @Test
    void canonicalFixtureMatchesThePublishedSchema() {
        assertValid(fixture);
    }

    @Test
    void unknownFieldIsRejectedRatherThanSilentlyIgnored() {
        ObjectNode changed = fixture.deepCopy();
        changed.put("rawPrompt", "this field must never cross the boundary");

        assertInvalid(changed, "additionalProperties");
    }

    @Test
    void requiredUnknownFactsCannotBeOmitted() {
        ObjectNode changed = fixture.deepCopy();
        changed.remove("unknownFacts");

        assertInvalid(changed, "required");
    }

    @Test
    void dispositionIsAClosedEnum() {
        ObjectNode changed = fixture.deepCopy();
        changed.put("reproductionDisposition", "PRODUCTION_INCIDENT_REPRODUCED");

        assertInvalid(changed, "enum");
    }

    @Test
    void schemaVersionIsLoadBearing() {
        ObjectNode changed = fixture.deepCopy();
        changed.put("schemaVersion", 2);

        assertInvalid(changed, "const");
    }

    private static void assertValid(JsonNode value) {
        Set<ValidationMessage> errors = schema.validate(value);
        assertTrue(errors.isEmpty(), () -> "expected valid bundle, got: " + errors);
    }

    private static void assertInvalid(JsonNode value, String expectedKeyword) {
        Set<ValidationMessage> errors = schema.validate(value);
        assertFalse(errors.isEmpty(), "the schema accepted an invalid bundle");
        assertTrue(errors.stream().anyMatch(error -> error.getType().contains(expectedKeyword)),
                () -> "expected " + expectedKeyword + " refusal, got: " + errors);
    }

    private static InputStream resource(String path) {
        InputStream stream = IncidentReproductionBundleSchemaTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("missing test resource " + path);
        }
        return stream;
    }
}
