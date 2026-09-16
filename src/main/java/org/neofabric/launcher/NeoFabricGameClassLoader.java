package org.neofabric.launcher;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.neofabric.core.BytecodeTransformPipeline;
import org.neofabric.core.CompatibilityClassLoaderRegistry;

/** Keeps NeoFabric APIs shared while isolating Minecraft/mod implementation classes. */
public final class NeoFabricGameClassLoader extends URLClassLoader {
    static {
        registerAsParallelCapable();
    }

    private final CompatibilityClassLoaderRegistry modLoaders;
    private final BytecodeTransformPipeline transforms;

    public NeoFabricGameClassLoader(URL[] urls, ClassLoader parent) {
        this(urls, parent, null, new BytecodeTransformPipeline());
    }

    public NeoFabricGameClassLoader(URL[] urls, ClassLoader parent, CompatibilityClassLoaderRegistry modLoaders) {
        this(urls, parent, modLoaders, new BytecodeTransformPipeline());
    }

    public NeoFabricGameClassLoader(URL[] urls, ClassLoader parent,
            CompatibilityClassLoaderRegistry modLoaders, BytecodeTransformPipeline transforms) {
        super(urls, parent);
        this.modLoaders = modLoaders;
        this.transforms = transforms;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> alreadyLoaded = findLoadedClass(name);
            if (alreadyLoaded != null) return alreadyLoaded;
            String resourceName = name.replace('.', '/') + ".class";
            URL resource = findResource(resourceName);
            if (resource == null) throw new ClassNotFoundException(name);
            try (var stream = resource.openStream()) {
                byte[] bytecode = transforms.apply(name, stream.readAllBytes());
                Class<?> defined = findLoadedClass(name);
                if (defined != null) return defined;
                return defineClass(name, bytecode, 0, bytecode.length);
            } catch (IOException error) {
                throw new ClassNotFoundException("Could not read " + name, error);
            }
        }
    }

    public BytecodeTransformPipeline transforms() {
        return transforms;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (name.startsWith("org.neofabric.") || name.startsWith("net.fabricmc.loader.")) {
                return super.loadClass(name, resolve);
            }
            if (name.startsWith("it.unimi.dsi.fastutil.") || name.startsWith("com.google.")
                    || name.startsWith("org.apache.") || name.startsWith("org.slf4j.")
                    || name.startsWith("io.netty.") || name.startsWith("org.spongepowered.asm.")) {
                return super.loadClass(name, resolve);
            }
            if (modLoaders != null && !name.startsWith("java.") && !name.startsWith("javax.")
                    && !name.startsWith("jdk.")) {
                Class<?> modClass = modLoaders.loadModClass(name);
                if (modClass != null) return modClass;
            }
            if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.")
                    || (!name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("jdk."))) {
                try {
                    Class<?> local = findClass(name);
                    if (resolve) resolveClass(local);
                    return local;
                } catch (ClassNotFoundException ignored) {
                    // Libraries and shared APIs remain parent-resolved.
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
