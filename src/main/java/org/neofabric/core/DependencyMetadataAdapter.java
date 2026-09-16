package org.neofabric.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Reads required dependency declarations without loading mod classes. */
public final class DependencyMetadataAdapter {
    private static final Pattern FABRIC_DEPENDS = Pattern.compile("\\\"depends\\\"\\s*:\\s*\\{([^}]*)}");
    private static final Pattern JSON_PAIR = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern FORGE_BLOCK = Pattern.compile("(?s)\\[\\[dependencies\\.([^]]+)]](.*?)(?=\\[\\[|\\z)");
    private static final Pattern TOML_MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern TOML_VERSION_RANGE = Pattern.compile("(?m)^\\s*versionRange\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern TOML_MANDATORY = Pattern.compile("(?m)^\\s*mandatory\\s*=\\s*(true|false)");

    private DependencyMetadataAdapter() {}

    public static Map<String, List<ModDependency>> read(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String name = zip.getEntry("fabric.mod.json") != null ? "fabric.mod.json"
                    : zip.getEntry("META-INF/neoforge.mods.toml") != null ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
            var entry = zip.getEntry(name);
            if (entry == null) return Map.of();
            String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            Map<String, List<ModDependency>> result = new LinkedHashMap<>();
            if (name.endsWith("json")) {
                String owner = findJson(text, "id");
                List<ModDependency> deps = new ArrayList<>();
                Matcher block = FABRIC_DEPENDS.matcher(text);
                if (block.find()) {
                    Matcher pair = JSON_PAIR.matcher(block.group(1));
                    while (pair.find()) deps.add(new ModDependency(pair.group(1), pair.group(2)));
                }
                result.put(owner, List.copyOf(deps));
            } else {
                Matcher blocks = FORGE_BLOCK.matcher(text);
                while (blocks.find()) {
                    String owner = blocks.group(1);
                    Matcher dependency = TOML_MOD_ID.matcher(blocks.group(2));
                    Matcher version = TOML_VERSION_RANGE.matcher(blocks.group(2));
                    Matcher mandatory = TOML_MANDATORY.matcher(blocks.group(2));
                    if (dependency.find()) result.computeIfAbsent(owner, ignored -> new ArrayList<>())
                            .add(new ModDependency(dependency.group(1), version.find() ? version.group(1) : "*",
                                    mandatory.find() && !Boolean.parseBoolean(mandatory.group(1))));
                }
                if (result.isEmpty()) {
                    Matcher owner = TOML_MOD_ID.matcher(text);
                    if (owner.find()) result.put(owner.group(1), List.of());
                }
            }
            return result;
        }
    }

    private static String findJson(String text, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(text);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
