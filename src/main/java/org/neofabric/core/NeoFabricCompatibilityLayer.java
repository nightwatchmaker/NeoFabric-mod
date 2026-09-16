package org.neofabric.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Wine-style compatibility layer for Minecraft mod ecosystems.
 *
 * <p>The host loader remains in charge of Minecraft startup. NeoFabric does
 * not start a VM, replace the host loader, or launch another Minecraft
 * process. It translates the discovered mod contracts into the host session
 * through the existing backend abstractions.</p>
 */
public final class NeoFabricCompatibilityLayer implements AutoCloseable {
    private final NeoFabricLoader loader;
    private final CompatibilityClassLoaderRegistry classLoaders;
    private final EventBus events = new EventBus();
    private List<CompatibilityDecision> decisions = List.of();
    private boolean started;

    public NeoFabricCompatibilityLayer(MinecraftTarget target, ClassLoader hostClassLoader) {
        loader = new NeoFabricLoader(Objects.requireNonNull(target, "target"));
        classLoaders = new CompatibilityClassLoaderRegistry(
                Objects.requireNonNull(hostClassLoader, "hostClassLoader"));
    }

    /**
     * Starts translation inside the already-running host-loader session.
     * This method never invokes a launcher or creates a child JVM.
     */
    public synchronized List<CompatibilityDecision> start(Path gameDirectory) throws IOException {
        return start(gameDirectory, null);
    }

    /**
     * Starts translation for foreign-loader mods only. Native mods remain
     * owned by the host loader, exactly like native Linux components remain
     * outside Wine's translation path.
     */
    public synchronized List<CompatibilityDecision> start(Path gameDirectory, LoaderKind hostLoader) throws IOException {
        if (started) return decisions;
        List<CompatibilityDecision> discovered = loader.loadCatalog(
                Objects.requireNonNull(gameDirectory, "gameDirectory").resolve("mods"));
        decisions = discovered.stream()
                .filter(decision -> hostLoader == null
                        || (decision.mod().loader() != hostLoader
                        && !decision.mod().id().equalsIgnoreCase("neofabric")))
                .toList();
        classLoaders.prepare(decisions);
        started = true;
        return decisions;
    }

    /** Initializes Fabric entrypoints for translated Fabric mods only. */
    public synchronized int initializeFabricEntrypoints(String distribution) throws IOException {
        if (!started) throw new IllegalStateException("Compatibility layer has not started");
        int initialized = 0;
        for (CompatibilityDecision decision : decisions) {
            if (!decision.accepted() || decision.mod().loader() != LoaderKind.FABRIC) continue;
            Path source = Path.of(decision.mod().source());
            FabricModInfo metadata = FabricMetadataAdapter.read(source);
            initialized += FabricEntrypointBridge.initialize(metadata,
                    classLoaders.get(decision.mod().id()), distribution).size();
        }
        return initialized;
    }

    /** Initializes safe Forge-family entrypoints when the host is not FML. */
    public synchronized ForeignFmlModBridge.Result initializeForeignFmlMods() throws IOException {
        if (!started) throw new IllegalStateException("Compatibility layer has not started");
        int constructed = 0;
        int deferred = 0;
        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>();
        for (CompatibilityDecision decision : decisions) {
            if (!decision.accepted()) continue;
            if (decision.mod().loader() != LoaderKind.FORGE
                    && decision.mod().loader() != LoaderKind.NEOFORGE) continue;
            var result = ForeignFmlModBridge.initialize(Path.of(decision.mod().source()),
                    classLoaders.get(decision.mod().id()));
            constructed += result.constructed();
            deferred += result.deferred();
            diagnostics.addAll(result.diagnostics());
        }
        return new ForeignFmlModBridge.Result(constructed, deferred, List.copyOf(diagnostics));
    }

    public ForeignFmlEventBridge.Result registerForeignFmlEvents() throws IOException {
        if (!started) throw new IllegalStateException("Compatibility layer has not started");
        int subscribers = 0;
        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>();
        for (CompatibilityDecision decision : decisions) {
            if (!decision.accepted()) continue;
            if (decision.mod().loader() != LoaderKind.FORGE
                    && decision.mod().loader() != LoaderKind.NEOFORGE) continue;
            var result = ForeignFmlEventBridge.register(Path.of(decision.mod().source()),
                    classLoaders.get(decision.mod().id()), events);
            subscribers += result.subscribers();
            diagnostics.addAll(result.diagnostics());
        }
        return new ForeignFmlEventBridge.Result(subscribers, List.copyOf(diagnostics));
    }

    public EventBus events() {
        return events;
    }

    public NeoFabricLoader loader() {
        return loader;
    }

    public CompatibilityClassLoaderRegistry classLoaders() {
        return classLoaders;
    }

    public List<CompatibilityDecision> decisions() {
        return decisions;
    }

    public boolean started() {
        return started;
    }

    @Override
    public synchronized void close() {
        classLoaders.close();
        started = false;
        decisions = List.of();
    }
}
