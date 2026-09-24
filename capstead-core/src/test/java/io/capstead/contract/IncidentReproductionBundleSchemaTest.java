package io.capstead.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IncidentReproductionBundleSchemaTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCHEMA = "/schema/incident-reproduction-bundle-v1.json";

    @Test
    void validFixtureIsAccepted() throws Exception {
        assertTrue(validate("/schema/incident-reproduction-bundle-v1-valid.json").isEmpty());
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        Set<ValidationMessage> errors =
                validate("/schema/incident-reproduction-bundle-v1-invalid-extra-field.json");
        assertFalse(errors.isEmpty());
    }

    @Test
    void missingUnknownFactsIsRejected() throws Exception {
        Set<ValidationMessage> errors =
                validate("/schema/incident-reproduction-bundle-v1-invalid-missing-required.json");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.getMessage().contains("unknownFacts")));
    }

    @Test
    void dispositionIsClosedAndObservedFactsStaySeparateFromCandidatePatterns() throws Exception {
        JsonNode node = read("/schema/incident-reproduction-bundle-v1-valid.json");
        assertTrue(node.has("observedFacts"));
        assertTrue(node.has("candidatePatternReferences"));
        assertFalse(node.get("observedFacts").equals(node.get("candidatePatternReferences")));

        ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                .put("reproductionDisposition", "NEW_UNREVIEWED_STATE");
        assertFalse(schema().validate(node).isEmpty());
    }

    private static Set<ValidationMessage> validate(String resource) throws Exception {
        return schema().validate(read(resource));
    }

    private static JsonNode read(String resource) throws Exception {
        try (InputStream input = IncidentReproductionBundleSchemaTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing test resource " + resource);
            return MAPPER.readTree(input);
        }
    }

    private static JsonSchema schema() throws Exception {
        try (InputStream input = IncidentReproductionBundleSchemaTest.class.getResourceAsStream(SCHEMA)) {
            assertNotNull(input, "missing schema resource");
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(input);
        }
    }
}
