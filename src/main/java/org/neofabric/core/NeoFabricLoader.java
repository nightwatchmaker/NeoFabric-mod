package org.neofabric.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Top-level NeoFabric runtime; compatibility backends are implementation details. */
public final class NeoFabricLoader {
    private final MinecraftTarget target;
    private final LifecycleDispatcher lifecycle = new LifecycleDispatcher();
    private final EventBus events = new EventBus();
    private final LifecycleCallbackRegistry callbacks = new LifecycleCallbackRegistry();
    private final NeoRegistryCatalog registries = new NeoRegistryCatalog();
    private final Map<LoaderKind, CompatibilityBackend> backends = new EnumMap<>(LoaderKind.class);
    private final Map<LoaderKind, LoaderRegistryAdapter> registryAdapters = new EnumMap<>(LoaderKind.class);
    private boolean initialized;

    public NeoFabricLoader(MinecraftTarget target) {
        this.target = target;
        register(new FabricCompatibilityBackend());
        register(new ForgeCompatibilityBackend());
        register(new NeoForgeCompatibilityBackend());
        for (LoaderKind kind : LoaderKind.values()) registryAdapters.put(kind, new LoaderRegistryAdapter(kind));
    }

    public void initialize() {
        if (!initialized) {
            initialized = true;
            fireLifecycle(LifecyclePhase.LOADER_READY, this);
        }
    }

    public void register(CompatibilityBackend backend) {
        backends.put(backend.kind(), backend);
    }

    public int discoverBackends(ClassLoader source) {
        int before = backends.size();
        ServiceLoader.load(CompatibilityBackend.class, Objects.requireNonNull(source, "source"))
                .forEach(this::register);
        return backends.size() - before;
    }

    public List<CompatibilityDecision> loadCatalog(Path modsDirectory) throws IOException {
        initialize();
        List<ModDescriptor> discovered = ModDiscovery.scanDirectory(modsDirectory);
        fireLifecycle(LifecyclePhase.MODS_DISCOVERED, discovered);
        Map<String, List<ModDependency>> dependencies = new java.util.LinkedHashMap<>();
        final var providedRuntime = java.util.Set.of("minecraft", "java", "fabricloader",
                "forge", "neoforge", "neofabric-loader");
        for (ModDescriptor mod : discovered) {
            Map<String, List<ModDependency>> metadataDependencies = DependencyMetadataAdapter.read(Path.of(mod.source()));
            dependencies.put(mod.id(), metadataDependencies.getOrDefault(mod.id(), List.of()).stream()
                    // These identifiers are supplied by the active NeoFabric
                    // runtime, not separate entries in mods/.
                    .filter(dependency -> !providedRuntime.contains(dependency.id().toLowerCase(java.util.Locale.ROOT)))
                    .toList());
        }
        Map<String, String> versions = discovered.stream().collect(java.util.stream.Collectors.toMap(
                ModDescriptor::id, ModDescriptor::version, (left, right) -> left, java.util.LinkedHashMap::new));
        List<String> order;
        try {
            order = new ModDependencyResolver().resolve(dependencies, versions);
        } catch (IllegalArgumentException error) {
            return discovered.stream().map(mod -> new CompatibilityDecision(mod, false,
                    "dependency resolution failed: " + error.getMessage())).toList();
        }
        Map<String, ModDescriptor> byId = discovered.stream().collect(java.util.stream.Collectors.toMap(
                ModDescriptor::id, java.util.function.Function.identity(), (left, right) -> left, java.util.LinkedHashMap::new));
        return order.stream().map(byId::get).filter(java.util.Objects::nonNull).map(this::translate).toList();
    }

    public CompatibilityDecision translate(ModDescriptor mod) {
        CompatibilityBackend backend = backends.get(mod.loader());
        if (backend == null) {
            return new CompatibilityDecision(mod, false, "no compatibility backend registered for " + mod.loader());
        }
        return backend.translate(mod, target);
    }

    public MinecraftTarget target() {
        return target;
    }

    public LifecycleDispatcher lifecycle() {
        return lifecycle;
    }

    public EventBus events() {
        return events;
    }

    public LifecycleCallbackRegistry callbacks() {
        return callbacks;
    }

    public LoaderRegistryAdapter registryAdapter(LoaderKind source) {
        return registryAdapters.get(Objects.requireNonNull(source, "source"));
    }

    public NeoRegistryCatalog registries() {
        return registries;
    }

    public void firePhase(LifecyclePhase phase, Object context) {
        fireLifecycle(phase, context);
    }

    private void fireLifecycle(LifecyclePhase phase, Object context) {
        lifecycle.fire(phase);
        callbacks.fire(phase, context);
    }
}
