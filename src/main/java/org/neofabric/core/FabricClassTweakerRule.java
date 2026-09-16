package org.neofabric.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/** Parsed Fabric class-tweaker/access-widener rule. */
public record FabricClassTweakerRule(String action, String kind, String owner, String name, String descriptor) {
    public FabricClassTweakerRule {
        if (!List.of("accessible", "mutable", "extendable").contains(action))
            throw new IllegalArgumentException("Unsupported class-tweaker action: " + action);
        if (!List.of("class", "field", "method").contains(kind))
            throw new IllegalArgumentException("Unsupported class-tweaker target: " + kind);
    }

    public static List<FabricClassTweakerRule> parse(Path modJar, FabricModInfo info) throws IOException {
        String resource = info.accessWidener();
        if (resource == null || resource.isBlank()) return List.of();
        try (ZipFile zip = new ZipFile(modJar.toFile())) {
            var entry = zip.getEntry(resource);
            if (entry == null) throw new IOException("Class-tweaker resource not found: " + resource);
            List<FabricClassTweakerRule> rules = new ArrayList<>();
            boolean header = false;
            for (String raw : new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!header) {
                    if (!line.startsWith("classTweaker\t v1") && !line.startsWith("classTweaker\tv1")
                            && !line.startsWith("accessWidener v"))
                        throw new IOException("Invalid class-tweaker header in " + modJar);
                    header = true;
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) throw new IOException("Malformed class-tweaker rule: " + raw);
                String action = parts[0], kind = parts[1], owner = parts[2];
                String name = kind.equals("class") ? "" : parts.length >= 4 ? parts[3] : "";
                String descriptor = kind.equals("class") ? "" : parts.length >= 5 ? parts[4] : "";
                if (kind.equals("class") && parts.length != 3) throw new IOException("Malformed class rule: " + raw);
                if (!kind.equals("class") && parts.length != 5) throw new IOException("Malformed member rule: " + raw);
                rules.add(new FabricClassTweakerRule(action, kind, owner, name, descriptor));
            }
            if (!header) throw new IOException("Missing class-tweaker header in " + modJar);
            return List.copyOf(rules);
        }
    }
}
