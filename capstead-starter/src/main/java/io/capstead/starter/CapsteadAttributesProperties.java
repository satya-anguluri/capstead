package io.capstead.starter;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The execution attribute names this application may record.
 *
 * <p>Attributes let an execution record what it decided and which versioned inputs produced that decision.
 * They are allow-listed rather than free-form, because the map hangs off the atom that Actuator, Micrometer,
 * dashboards, cost reports and budgets all derive from, and a free-form map there becomes a dumping ground.
 *
 * <p>Example:
 * <pre>
 * capstead.attributes.allowed:
 *   - policy.authorization.outcome
 *   - policy.authorization.reason
 *   - evidence.state
 *   - evidence.sourceRevision
 *   - lab.fixture.version
 * </pre>
 *
 * <p><b>The default is empty, and empty allows nothing.</b> An application that has not declared any names
 * records no attributes — which is exactly how it behaved before attributes existed. The alternative, an
 * empty list meaning "anything goes", would make the allow-list something you have to remember to switch on.
 *
 * <p>Names must be namespaced and are matched exactly; see {@code io.capstead.core.ExecutionAttributes}. A
 * malformed name here is dropped rather than allowed, so a typo fails closed.
 */
@ConfigurationProperties("capstead.attributes")
public class CapsteadAttributesProperties {

    /** Attribute names this application may record. Empty by default, which permits none. */
    private Set<String> allowed = new LinkedHashSet<>();

    public Set<String> getAllowed() {
        return allowed;
    }

    public void setAllowed(Set<String> allowed) {
        this.allowed = allowed == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowed);
    }
}
