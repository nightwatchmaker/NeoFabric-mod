package org.neofabric.core;

import java.util.Objects;

/** A loader-neutral mod dependency, optionally required by the declaring mod. */
public record ModDependency(String id, String requiredVersion, boolean optional) {
    public ModDependency(String id, String requiredVersion) {
        this(id, requiredVersion, false);
    }

    public ModDependency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requiredVersion, "requiredVersion");
    }
}
