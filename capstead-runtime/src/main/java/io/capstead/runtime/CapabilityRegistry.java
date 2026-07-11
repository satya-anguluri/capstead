package io.capstead.runtime;

import io.capstead.core.CapabilityException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The in-memory catalog of every discovered capability, keyed by {@code name@version}
 * coordinates.
 *
 * <p>Populated once at startup by discovery. Enforces coordinate uniqueness so that versioned
 * routing is unambiguous, and backs the {@code /actuator/capabilities} discovery view.
 * Thread-safe for concurrent reads after startup registration completes.
 */
public final class CapabilityRegistry {

    private final Map<String, CapabilityDescriptor> byCoordinates = new LinkedHashMap<>();

    /**
     * Registers a capability.
     *
     * @throws CapabilityException with {@link CapabilityException.Reason#DUPLICATE_COORDINATES}
     *         if another capability is already registered under the same {@code name@version}
     */
    public synchronized void register(CapabilityDescriptor descriptor) {
        CapabilityDescriptor existing = byCoordinates.putIfAbsent(descriptor.coordinates(), descriptor);
        if (existing != null) {
            throw new CapabilityException(
                    CapabilityException.Reason.DUPLICATE_COORDINATES,
                    descriptor.name(),
                    "Duplicate capability '" + descriptor.coordinates() + "' declared on "
                            + describe(existing) + " and " + describe(descriptor));
        }
    }

    /** Looks up a capability by its {@code name@version} coordinates. */
    public Optional<CapabilityDescriptor> find(String coordinates) {
        return Optional.ofNullable(byCoordinates.get(coordinates));
    }

    /** All registered capabilities, in registration order. */
    public Collection<CapabilityDescriptor> all() {
        return Collections.unmodifiableCollection(byCoordinates.values());
    }

    /** Number of registered capabilities. */
    public int size() {
        return byCoordinates.size();
    }

    private static String describe(CapabilityDescriptor descriptor) {
        return descriptor.bean().getClass().getName() + "#" + descriptor.method().getName();
    }
}
