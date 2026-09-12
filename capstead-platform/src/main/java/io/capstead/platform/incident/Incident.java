package io.capstead.platform.incident;

import java.time.Instant;

/**
 * One produced incident — the six-field Phase-A record. Deliberately minimal: no fingerprint, no
 * first/last-seen grouping, no occurrence counts (CAP-INC-050/051 concepts, deferred to Phase B).
 *
 * @param incidentId  unique id of this incident
 * @param incidentType e.g. EXPECTED_TOOL_NOT_OBSERVED
 * @param ruleId      e.g. CAP-INC-031
 * @param ruleVersion e.g. 1
 * @param severity    Phase A: always HIGH for EXPECTED_TOOL_NOT_OBSERVED
 * @param detectedAt  when the incident was produced
 * @param evidence    the frozen deterministic proof
 */
public record Incident(
        String incidentId,
        String incidentType,
        String ruleId,
        int ruleVersion,
        Severity severity,
        Instant detectedAt,
        EvidenceBundle evidence) {
}