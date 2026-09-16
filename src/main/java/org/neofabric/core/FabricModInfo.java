package org.neofabric.core;

import java.util.List;
import java.util.Map;

/** Normalized Fabric metadata needed by a future FML-backed adapter. */
public record FabricModInfo(
        String id,
        String version,
        Map<String, List<String>> entrypoints,
        Map<String, String> dependencies,
        List<String> mixins,
        String accessWidener
) {
}
