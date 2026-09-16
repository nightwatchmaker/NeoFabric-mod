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
