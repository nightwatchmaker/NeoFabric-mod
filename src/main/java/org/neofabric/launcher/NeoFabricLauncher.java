package org.neofabric.launcher;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;

import org.neofabric.core.BytecodeTransformPipeline;
import org.neofabric.core.CompatibilityClassLoaderRegistry;
import org.neofabric.core.FabricEntrypointBridge;
import org.neofabric.core.FabricMetadataAdapter;
import org.neofabric.core.FabricMixinConfigDiscovery;
import org.neofabric.core.LoaderKind;
import org.neofabric.core.MinecraftTarget;
import org.neofabric.core.NeoFabricLoader;

/** Official-launcher entrypoint that delegates to Minecraft after loader setup. */
public final class NeoFabricLauncher {
    private static CompatibilityClassLoaderRegistry activeModLoaders;
    private static BytecodeTransformPipeline activeTransforms;

    private NeoFabricLauncher() {
    }

    public static void main(String[] args) {
        // Tell embedded FML that NeoFabric owns Fabric-family discovery.
        System.setProperty("neofabric.compatibility", "true");
        // FML's production GameLocator needs the shared launcher library root;
        // Prism does not provide this NeoForge-specific system property.
        configureFmlLibraryDirectory();
        // FML's production path expects its version coordinates on the command
        // line; Prism supplies only vanilla arguments for this custom loader.
        String[] fmlArgs = withFmlVersionArguments(args);
        // Enter the native FML startup sequence. It performs transformation,
        // dependency ordering, Forge/NeoForge construction, lifecycle events,
        // and then calls the vanilla client.
        net.neoforged.fml.startup.Client.main(fmlArgs);
    }

    private static void configureFmlLibraryDirectory() {
        if (System.getProperty("libraryDirectory") != null) return;
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(java.io.File.pathSeparator)) {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
                if ("libraries".equals(cursor.getFileName() == null ? "" : cursor.getFileName().toString())) {
                    System.setProperty("libraryDirectory", cursor.toString());
                    return;
                }
            }
        }
    }

    private static String[] withFmlVersionArguments(String[] args) {
        var result = new java.util.ArrayList<String>(java.util.Arrays.asList(args));
        addFmlArgument(result, "fml.mcVersion", "26.2");
        addFmlArgument(result, "fml.neoForgeVersion", "26.2.0.86");
        addFmlArgument(result, "fml.neoFormVersion", "1.21.11");
        return result.toArray(String[]::new);
    }

    private static void addFmlArgument(java.util.List<String> args, String key, String value) {
        if (!args.contains("--" + key)) {
            args.add("--" + key);
            args.add(value);
        }
    }

    public static NeoFabricLoader initializeCore(Path gameDirectory) throws java.io.IOException {
        NeoFabricLoader loader = new NeoFabricLoader(MinecraftTarget.MC_26_2);
        int externalBackends = loader.discoverBackends(NeoFabricLauncher.class.getClassLoader());
        Path effectiveGameDirectory = gameDirectory;
        // Prism and the official launcher may set user.dir independently of
        // --gameDir. Once FML is alive, its game directory is authoritative.
        try {
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Object current = fmlLoader.getMethod("getCurrent").invoke(null);
            Path fmlGameDirectory = (Path) current.getClass().getMethod("getGameDir").invoke(current);
            if (java.nio.file.Files.isDirectory(fmlGameDirectory.resolve("mods"))) {
                effectiveGameDirectory = fmlGameDirectory;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Core tests and non-FML bootstraps use the supplied directory.
        }
        var decisions = loader.loadCatalog(effectiveGameDirectory.resolve("mods"));
        activeModLoaders = new CompatibilityClassLoaderRegistry(NeoFabricLauncher.class.getClassLoader());
        activeModLoaders.prepare(decisions);
        activeTransforms = new BytecodeTransformPipeline();
        for (var decision : decisions) {
            if (!decision.accepted() || decision.mod().loader() != LoaderKind.FABRIC) continue;
            var info = FabricMetadataAdapter.read(Path.of(decision.mod().source()));
            var rules = org.neofabric.core.FabricClassTweakerRule.parse(Path.of(decision.mod().source()), info);
            if (!rules.isEmpty()) activeTransforms.add(new org.neofabric.core.FabricClassTweakerTransformer(rules));
        }
        String distribution = System.getProperty("neofabric.distribution", "CLIENT");
        for (var decision : decisions) {
            if (!decision.accepted()) {
                System.err.println("[NeoFabric] SKIPPED " + decision.mod().id() + " ["
                        + decision.mod().loader() + "]: " + decision.reason());
                continue;
            }
            System.out.println("[NeoFabric] DISCOVERED " + decision.mod().id() + " ["
                    + decision.mod().loader() + "]: " + decision.reason());
            if (decision.mod().loader() != LoaderKind.FABRIC) {
                System.out.println("[NeoFabric] DELEGATED " + decision.mod().id() + " ["
                        + decision.mod().loader() + "]: native FML owns construction and lifecycle");
                continue;
            }
            Path modJar = Path.of(decision.mod().source());
            var metadata = FabricMetadataAdapter.read(modJar);
            var mixinConfigs = FabricMixinConfigDiscovery.validate(modJar, metadata);
            var modLoader = activeModLoaders.get(decision.mod().id());
            var initialized = FabricEntrypointBridge.initialize(metadata, modLoader, distribution);
            var registries = FabricEntrypointBridge.initializeRegistries(metadata, modLoader,
                    loader.registries(), loader.registryAdapter(LoaderKind.FABRIC));
            System.out.println("[NeoFabric] LOADED " + decision.mod().id() + " [FABRIC]: "
                    + initialized.size() + " entrypoints, " + registries.size()
                    + " registry entrypoints, " + mixinConfigs.size() + " validated Mixin configs");
        }
        System.out.println("[NeoFabric] external compatibility backends discovered: " + externalBackends);
        System.out.println("[NeoFabric] official-launcher bootstrap initialized for Minecraft 26.2 with "
                + activeModLoaders.size() + " accepted mod classloaders");
        return loader;
    }

    public static void launch(String targetMainClass, String[] args) throws Exception {
        URL[] runtimeUrls = Arrays.stream(System.getProperty("java.class.path", "").split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .filter(path -> java.nio.file.Files.exists(path))
                .map(path -> {
                    try {
                        return path.toUri().toURL();
                    } catch (java.net.MalformedURLException error) {
                        throw new IllegalArgumentException("Invalid runtime classpath entry: " + path, error);
                    }
                })
                .toArray(URL[]::new);
        BytecodeTransformPipeline transforms = activeTransforms != null ? activeTransforms : new BytecodeTransformPipeline();
        try (var gameLoader = new NeoFabricGameClassLoader(runtimeUrls, NeoFabricLauncher.class.getClassLoader(), activeModLoaders, transforms)) {
            Class<?> target = Class.forName(targetMainClass, true, gameLoader);
            Method main = target.getMethod("main", String[].class);
            System.out.println("[NeoFabric] delegating official-launcher bootstrap to " + targetMainClass
                    + " through isolated game classloader with " + args.length + " arguments");
            main.invoke(null, (Object) Arrays.copyOf(args, args.length));
        }
    }
}
