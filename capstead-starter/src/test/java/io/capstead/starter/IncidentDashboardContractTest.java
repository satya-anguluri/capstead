package io.capstead.starter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentDashboardContractTest {

    @Test
    void incidentPageKeepsObservedFactsSeparateFromUnassessedCauses() throws IOException {
        String page;
        try (var stream = getClass().getResourceAsStream("/static/capstead/incidents.html")) {
            assertThat(stream).isNotNull();
            page = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(page).contains("/actuator/incidentdetails", "Observed incident", "What broke",
                "Evidence quality", "Possible causes", "Recommended checks");
        assertThat(page).contains("Not evaluated. Capstead is not claiming a root cause");
        assertThat(page).contains("Open native execution evidence", "aria-live=\"polite\"");
        assertThat(page).contains("safeNativeExecutionEndpoint", "url.origin !== location.origin",
                "'/actuator/capabilityexecutions/'");
        assertThat(page).contains("response.status === 404 && notFoundIsNull",
                "getJson(DETAILS_URL + '/' + encodeURIComponent(id), true)",
                "if (!Array.isArray(incidents))");
        assertThat(page).doesNotContain("href=\"${esc(e.nativeExecutionEndpoint)}\"",
                "renderList(incidents || [])", "Confidence: High", "Root cause confirmed");
    }
}
