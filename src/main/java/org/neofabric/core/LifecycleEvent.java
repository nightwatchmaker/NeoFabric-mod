package org.neofabric.core;

import java.util.Objects;

/** Stable lifecycle envelope delivered through the shared compatibility bus. */
public record LifecycleEvent(LifecyclePhase phase, Object context) {
    public LifecycleEvent {
        Objects.requireNonNull(phase, "phase");
    }
}
