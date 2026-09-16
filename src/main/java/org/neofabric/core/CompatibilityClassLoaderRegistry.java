package org.neofabric.core;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns backend classloaders for one NeoFabric loading session. */
public final class CompatibilityClassLoaderRegistry implements AutoCloseable {
    private final ClassLoader parent;
    private URLClassLoader sharedModClasspath;
    private final Map<String, CompatibilityClassLoader> loaders = new LinkedHashMap<>();

    public CompatibilityClassLoaderRegistry(ClassLoader parent) {
        this.parent = parent;
    }

    public void prepare(List<CompatibilityDecision> decisions) {
        List<Path> sources = decisions.stream().filter(CompatibilityDecision::accepted)
                .map(decision -> Path.of(decision.mod().source())).toList();
        try {
            URL[] urls = sources.stream().map(path -> {
                try { return path.toAbsolutePath().normalize().toUri().toURL(); }
                catch (IOException error) { throw new IllegalStateException("Invalid mod path: " + path, error); }
            }).toArray(URL[]::new);
            sharedModClasspath = new URLClassLoader(urls, parent);
        } catch (RuntimeException error) {
            throw error;
        }
        decisions.stream().filter(CompatibilityDecision::accepted).forEach(decision -> {
            try {
                Path source = Path.of(decision.mod().source());
                loaders.put(decision.mod().id(), new CompatibilityClassLoader(source, sharedModClasspath));
            } catch (IOException error) {
                throw new IllegalStateException("Could not create classloader for " + decision.mod().id(), error);
            }
        });
    }

    public ClassLoader get(String modId) {
        return loaders.get(modId);
    }

    public Class<?> loadModClass(String name) {
        for (CompatibilityClassLoader loader : loaders.values()) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
                // Try the next accepted mod loader.
            }
        }
        return null;
    }

    public int size() {
        return loaders.size();
    }

    @Override
    public void close() {
        loaders.values().forEach(loader -> {
            try {
                loader.close();
            } catch (IOException ignored) {
                // Closing is best-effort during loader shutdown.
            }
        });
        loaders.clear();
        if (sharedModClasspath != null) {
            try {
                sharedModClasspath.close();
            } catch (IOException ignored) {
                // Closing is best-effort during loader shutdown.
            }
            sharedModClasspath = null;
        }
    }
}
