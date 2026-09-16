package org.neofabric.fabric;

import org.neofabric.core.ForeignRegistryStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reflective 26.2 native-registry adapter; keeps the common core Minecraft-free. */
public final class FabricNativeRegistryBridge {
    private FabricNativeRegistryBridge() {}

    public static void install() {
        ForeignRegistryStore.bindNativeRegistrar(FabricNativeRegistryBridge::register);
    }

    private static Object register(String registryName, String id, Object value) {
        try {
            String fieldName = switch (registryName) {
                case "minecraft:item" -> "ITEM";
                case "minecraft:block" -> "BLOCK";
                case "minecraft:entity_type" -> "ENTITY_TYPE";
                case "minecraft:fluid" -> "FLUID";
                case "minecraft:menu" -> "MENU";
                default -> throw new IllegalArgumentException("Unsupported native registry: " + registryName);
            };
            Class<?> builtIns = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object registry = builtIns.getField(fieldName).get(null);
            String[] parts = id.split(":", 2);
            String namespace = parts.length == 2 ? parts[0] : "minecraft";
            String path = parts.length == 2 ? parts[1] : parts[0];
            Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
            Object key = identifier.getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, namespace, path);
            Class<?> registryType = Class.forName("net.minecraft.core.Registry");
            Method register = registryType.getMethod("register", registryType, identifier, Object.class);
            return register.invoke(null, registry, key, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native Fabric registry promotion failed for " + registryName + ":" + id, error);
        }
    }
}
