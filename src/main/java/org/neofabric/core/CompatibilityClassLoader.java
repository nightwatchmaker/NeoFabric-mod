package org.neofabric.core;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * Controlled mod classloader. Shared loader/API namespaces remain parent-first;
 * mod implementation classes are isolated and loaded child-first.
 */
public final class CompatibilityClassLoader extends URLClassLoader {
    private static final List<String> PARENT_FIRST = List.of(
            "java.", "javax.", "jdk.", "sun.",
            "org.neofabric.", "net.fabricmc.", "net.minecraftforge.", "net.neoforged.",
            "org.slf4j.", "org.apache.logging.", "org.spongepowered.asm.", "org.objectweb.asm.",
            "com.google.gson.", "com.google.common."
    );

    public CompatibilityClassLoader(Path modJar, ClassLoader parent) throws IOException {
        super(new URL[]{modJar.toAbsolutePath().normalize().toUri().toURL()}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (isParentFirst(name)) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException missingFromMod) {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private static boolean isParentFirst(String name) {
        return PARENT_FIRST.stream().anyMatch(name::startsWith);
    }
}
