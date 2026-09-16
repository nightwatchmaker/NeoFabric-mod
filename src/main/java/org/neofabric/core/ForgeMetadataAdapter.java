package org.neofabric.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Reads the stable Forge/NeoForge TOML fields needed before native FML construction. */
public final class ForgeMetadataAdapter {
    private static final Pattern MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern MOD_LOADER = Pattern.compile("(?m)^\\s*modLoader\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern LOADER_VERSION = Pattern.compile("(?m)^\\s*loaderVersion\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern DISPLAY_NAME = Pattern.compile("(?m)^\\s*displayName\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern MOD_SECTION = Pattern.compile("(?s)\\[\\[mods]](.*?)(?=\\[\\[|\\z)");

    private ForgeMetadataAdapter() {
    }

    public static ForgeModInfo read(Path modJar) throws IOException {
        try (ZipFile zip = new ZipFile(modJar.toFile())) {
            String resource = zip.getEntry("META-INF/neoforge.mods.toml") != null
                    ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
            var entry = zip.getEntry(resource);
            if (entry == null) throw new IOException("Forge metadata not found in " + modJar);
            String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            List<String> ids = new ArrayList<>();
            List<String> versions = new ArrayList<>();
            Matcher sections = MOD_SECTION.matcher(text);
            while (sections.find()) {
                Matcher id = MOD_ID.matcher(sections.group(1));
                if (id.find()) ids.add(id.group(1));
                Matcher version = VERSION.matcher(sections.group(1));
                if (version.find()) versions.add(version.group(1));
            }
            if (ids.isEmpty()) ids = matches(MOD_ID, text);
            if (versions.isEmpty()) versions = matches(VERSION, text);
            if (ids.isEmpty()) throw new IOException("Forge metadata has no mod entries in " + modJar);
            return new ForgeModInfo(ids, versions, find(MOD_LOADER, text, "unknown"),
                    find(LOADER_VERSION, text, "unknown"), find(DISPLAY_NAME, text, ids.get(0)));
        }
    }

    private static List<String> matches(Pattern pattern, String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) result.add(matcher.group(1));
        return List.copyOf(result);
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
