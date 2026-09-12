package io.capstead.platform.incident;

import java.util.List;
import java.util.Optional;

/** Write/read side for produced incidents. Phase A: in-memory only. */
public interface IncidentStore {

    void save(Incident incident);

    Optional<Incident> byId(String incidentId);

    /** Most-recent-first. */
    List<Incident> recent();
}