package org.neofabric.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Validates the Fabric access-widener resource before transformation handoff. */
public final class FabricAccessWidenerDiscovery {
    private FabricAccessWidenerDiscovery() {
    }

    public static String validate(Path modJar, FabricModInfo info) throws IOException {
        String path = info.accessWidener();
        if (path == null || path.isBlank()) return "";
        if (path.startsWith("/") || path.contains("..") || path.contains("\\")) {
            throw new IOException("unsafe Fabric access widener path in " + modJar + ": " + path);
        }
        try (ZipFile zip = new ZipFile(modJar.toFile())) {
            var entry = zip.getEntry(path);
            if (entry == null) throw new IOException("Fabric access widener not found in " + modJar + ": " + path);
            String header = new String(zip.getInputStream(entry).readNBytes(256), StandardCharsets.UTF_8);
            if (!header.lines().anyMatch(line -> {
                String normalized = line.trim();
                return normalized.startsWith("accessWidener v") || (normalized.startsWith("classTweaker") && normalized.contains("v1"));
            })) {
                throw new IOException("Invalid Fabric access widener header in " + modJar + ": " + path);
            }
        }
        return path;
    }
}
