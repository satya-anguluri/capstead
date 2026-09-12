package io.capstead.platform.web;

import io.capstead.platform.incident.Incident;
import io.capstead.platform.incident.IncidentStore;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.List;

/**
 * Read-only incident surface. Incidents carry {@code evidence.nativeExecutionId}, which links to
 * the existing Capstead execution endpoint for the native tree — this endpoint references the
 * native execution, it does not re-render or embed execution trees.
 */
@Endpoint(id = "incidents")
public class IncidentEndpoint {

    private final IncidentStore store;

    public IncidentEndpoint(IncidentStore store) {
        this.store = store;
    }

    /** {@code GET /actuator/incidents} — recent incidents, most-recent-first. */
    @ReadOperation
    public List<Incident> incidents() {
        return store.recent();
    }

    /** {@code GET /actuator/incidents/{id}} — one incident with its frozen evidence bundle. */
    @ReadOperation
    public Incident incident(@Selector String id) {
        return store.byId(id).orElse(null);
    }
}