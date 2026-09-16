package org.neofabric.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Detects loader metadata without loading mod classes and scans jars in parallel. */
public final class ModDiscovery {
    private static final Pattern JSON_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern JSON_VERSION = Pattern.compile("\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern TOML_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern TOML_VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final ConcurrentHashMap<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private ModDiscovery() {}

    public static Optional<ModDescriptor> inspect(Path modJar) throws IOException {
        Path normalized = modJar.toAbsolutePath().normalize();
        var attributes = Files.readAttributes(normalized, java.nio.file.attribute.BasicFileAttributes.class);
        CacheEntry cached = CACHE.get(normalized);
        if (cached != null && cached.size == attributes.size() && cached.modified == attributes.lastModifiedTime().toMillis()) {
            return cached.descriptor;
        }
        Optional<ModDescriptor> result = inspectZip(normalized);
        CACHE.put(normalized, new CacheEntry(attributes.size(), attributes.lastModifiedTime().toMillis(), result));
        return result;
    }

    public static List<ModDescriptor> scanDirectory(Path modsDirectory) throws IOException {
        if (!Files.isDirectory(modsDirectory)) return List.of();
        try (Stream<Path> paths = Files.list(modsDirectory)) {
            List<Path> jars = paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
            return jars.parallelStream().flatMap(path -> {
                try { return inspectAll(path).stream(); }
                catch (IOException error) { return Stream.<ModDescriptor>empty(); }
            }).filter(java.util.Objects::nonNull).sorted(Comparator.comparing(ModDescriptor::id)).toList();
        }
    }

    private static List<ModDescriptor> inspectAll(Path modJar) throws IOException {
        List<ModDescriptor> result = new java.util.ArrayList<>();
        try (ZipFile zip = new ZipFile(modJar.toFile())) {
            if (zip.getEntry("fabric.mod.json") != null) {
                inspect(modJar).ifPresent(result::add);
            } else if (zip.getEntry("META-INF/neoforge.mods.toml") != null || zip.getEntry("META-INF/mods.toml") != null) {
                ForgeModInfo info = ForgeMetadataAdapter.read(modJar);
                LoaderKind loader = zip.getEntry("META-INF/neoforge.mods.toml") != null
                        ? LoaderKind.NEOFORGE : LoaderKind.FORGE;
                for (int i = 0; i < info.modIds().size(); i++) {
                    String version = i < info.versions().size() ? info.versions().get(i) : "unknown";
                    result.add(new ModDescriptor(info.modIds().get(i), version, loader, modJar.toString()));
                }
            }
        }
        try (ZipFile zip = new ZipFile(modJar.toFile())) {
            var nested = zip.stream().filter(entry -> !entry.isDirectory()
                    && entry.getName().startsWith("META-INF/jars/")
                    && entry.getName().endsWith(".jar")).toList();
            if (nested.isEmpty()) return List.copyOf(result);
            Path cacheRoot = Path.of(System.getProperty("java.io.tmpdir"), "neofabric-nested",
                    Integer.toHexString(modJar.toAbsolutePath().normalize().toString().hashCode()));
            Files.createDirectories(cacheRoot);
            for (var entry : nested) {
                String fileName = Path.of(entry.getName()).getFileName().toString();
                Path extracted = cacheRoot.resolve(fileName);
                if (!Files.exists(extracted) || Files.size(extracted) != entry.getSize()) {
                    try (var input = zip.getInputStream(entry)) {
                        Files.copy(input, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                inspect(extracted).ifPresent(result::add);
            }
        }
        return List.copyOf(result);
    }

    private static Optional<ModDescriptor> inspectZip(Path modJar) throws IOException {
        try (ZipFile zip = new ZipFile(modJar.toFile())) {
            var fabricEntry = zip.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                String text = new String(zip.getInputStream(fabricEntry).readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(new ModDescriptor(find(JSON_ID, text, "unknown"), find(JSON_VERSION, text, "unknown"), LoaderKind.FABRIC, modJar.toString()));
            }
            String metadataName = zip.getEntry("META-INF/neoforge.mods.toml") != null
                    ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml";
            var entry = zip.getEntry(metadataName);
            if (entry != null) {
                String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                LoaderKind loader = metadataName.contains("neoforge") || text.toLowerCase(Locale.ROOT).contains("neoforge")
                        ? LoaderKind.NEOFORGE : LoaderKind.FORGE;
                return Optional.of(new ModDescriptor(find(TOML_ID, text, "unknown"), find(TOML_VERSION, text, "unknown"), loader, modJar.toString()));
            }
            return Optional.empty();
        }
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private record CacheEntry(long size, long modified, Optional<ModDescriptor> descriptor) {}
}
