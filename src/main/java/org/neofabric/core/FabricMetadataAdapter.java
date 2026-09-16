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

/** Reads the small, stable subset of fabric.mod.json needed for backend translation. */
public final class FabricMetadataAdapter {
    private static final Pattern ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern VERSION = Pattern.compile("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ENTRYPOINT = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\[\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern OBJECT_ENTRYPOINT = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\[\\s*\\{\\s*\\\"value\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern DEPENDENCY = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MIXINS_BLOCK = Pattern.compile("\\\"mixins\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern MIXIN_CONFIG = Pattern.compile("\\\"(?:config|file)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern QUOTED_VALUE = Pattern.compile("\\\"([^\\\"]+\\.json)\\\"");
    private static final Pattern ACCESS_WIDENER = Pattern.compile("\\\"accessWidener\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private FabricMetadataAdapter() {}

    public static FabricModInfo read(Path modJar) throws IOException {
        try (ZipFile zip = new ZipFile(modJar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) throw new IOException("fabric.mod.json not found in " + modJar);
            String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            Map<String, List<String>> entrypoints = new LinkedHashMap<>();
            Matcher entryMatcher = ENTRYPOINT.matcher(json);
            while (entryMatcher.find()) {
                entrypoints.computeIfAbsent(entryMatcher.group(1), ignored -> new ArrayList<>()).add(entryMatcher.group(2));
            }
            Matcher objectEntrypointMatcher = OBJECT_ENTRYPOINT.matcher(json);
            while (objectEntrypointMatcher.find()) {
                List<String> values = entrypoints.computeIfAbsent(objectEntrypointMatcher.group(1), ignored -> new ArrayList<>());
                if (!values.contains(objectEntrypointMatcher.group(2))) values.add(objectEntrypointMatcher.group(2));
            }
            Map<String, String> dependencies = new LinkedHashMap<>();
            int dependencyStart = json.indexOf("\"depends\"");
            if (dependencyStart >= 0) {
                int end = json.indexOf('}', dependencyStart);
                String block = end >= 0 ? json.substring(dependencyStart, end) : json.substring(dependencyStart);
                Matcher dependencyMatcher = DEPENDENCY.matcher(block);
                while (dependencyMatcher.find()) dependencies.put(dependencyMatcher.group(1), dependencyMatcher.group(2));
            }
            List<String> mixins = new ArrayList<>();
            Matcher mixinsMatcher = MIXINS_BLOCK.matcher(json);
            if (mixinsMatcher.find()) {
                Matcher objects = MIXIN_CONFIG.matcher(mixinsMatcher.group(1));
                while (objects.find()) mixins.add(objects.group(1));
                Matcher strings = QUOTED_VALUE.matcher(mixinsMatcher.group(1));
                while (strings.find() && !mixins.contains(strings.group(1))) mixins.add(strings.group(1));
            }
            String accessWidener = find(ACCESS_WIDENER, json, "");
            return new FabricModInfo(find(ID, json, "unknown"), find(VERSION, json, "unknown"), entrypoints, dependencies, mixins, accessWidener);
        }
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
