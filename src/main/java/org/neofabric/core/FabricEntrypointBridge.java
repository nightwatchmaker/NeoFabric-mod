package org.neofabric.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Executes Fabric lifecycle entrypoints through a NeoFabric compatibility classloader. */
public final class FabricEntrypointBridge {
    private FabricEntrypointBridge() {}

    public static List<String> initialize(FabricModInfo metadata, ClassLoader modLoader) {
        return initialize(metadata, modLoader, "CLIENT");
    }

    public static List<String> initialize(FabricModInfo metadata, ClassLoader modLoader, String distribution) {
        List<String> initialized = new ArrayList<>();
        initializeKey(metadata, modLoader, "main", initialized);
        initializeKey(metadata, modLoader, "CLIENT".equals(distribution) ? "client" : "server", initialized);
        return List.copyOf(initialized);
    }

    public static List<String> initializeRegistries(FabricModInfo metadata, ClassLoader modLoader,
            NeoRegistryCatalog registries, LoaderRegistryAdapter adapter) {
        List<String> initialized = new ArrayList<>();
        for (String className : metadata.entrypoints().getOrDefault("neofabric:registry", List.of())) {
            try {
                Class<?> entrypointClass = Class.forName(className, true, modLoader);
                Object entrypoint = entrypointClass.getDeclaredConstructor().newInstance();
                if (!(entrypoint instanceof NeoFabricRegistryEntrypoint registryEntrypoint)) {
                    throw new IllegalStateException("Registry entrypoint does not implement NeoFabricRegistryEntrypoint: " + className);
                }
                registryEntrypoint.register(registries, adapter);
                initialized.add(className);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not initialize NeoFabric registry entrypoint " + className
                        + " for mod " + metadata.id(), error);
            }
        }
        return List.copyOf(initialized);
    }

    private static void initializeKey(FabricModInfo metadata, ClassLoader modLoader, String key, List<String> initialized) {
        for (String className : metadata.entrypoints().getOrDefault(key, List.of())) {
            try {
                Class<?> entrypointClass = Class.forName(className, true, modLoader);
                String methodName = switch (key) {
                    case "client" -> "onInitializeClient";
                    case "server" -> "onInitializeServer";
                    default -> "onInitialize";
                };
                Method initialize = entrypointClass.getMethod(methodName);
                initialize.invoke(entrypointClass.getDeclaredConstructor().newInstance());
                initialized.add(className);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not initialize Fabric " + key + " entrypoint " + className
                        + " for mod " + metadata.id(), error);
            }
        }
    }
}
