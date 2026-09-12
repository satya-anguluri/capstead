package io.capstead.platform.incident;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Phase-A default incident store: thread-safe, in-memory, most-recent-first reads. */
public final class InMemoryIncidentStore implements IncidentStore {

    private final List<Incident> incidents = new CopyOnWriteArrayList<>();

    @Override
    public void save(Incident incident) {
        incidents.add(incident);
    }

    @Override
    public Optional<Incident> byId(String incidentId) {
        return incidents.stream()
                .filter(i -> i.incidentId().equals(incidentId))
                .findFirst();
    }

    @Override
    public List<Incident> recent() {
        return incidents.stream()
                .sorted(Comparator.comparing(Incident::detectedAt).reversed())
                .toList();
    }
}