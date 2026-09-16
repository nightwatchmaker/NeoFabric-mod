package org.neofabric.core;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Lazy foreign registry store with an optional native-host promotion callback. */
public final class ForeignRegistryStore {
    @FunctionalInterface
    public interface NativeRegistrar {
        Object register(String registryName, String id, Object value);
    }

    private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();
    private static volatile NativeRegistrar nativeRegistrar;
    private ForeignRegistryStore() {}

    public static void bindNativeRegistrar(NativeRegistrar registrar) {
        nativeRegistrar = registrar;
    }

    @SuppressWarnings("unchecked")
    public static <T> T resolve(String registryName, String key, Supplier<? extends T> supplier) {
        Objects.requireNonNull(key, "key");
        return (T) VALUES.computeIfAbsent(key, ignored -> {
            T value = Objects.requireNonNull(supplier.get(), "registry value");
            NativeRegistrar registrar = nativeRegistrar;
            return registrar == null ? value : registrar.register(registryName, key, value);
        });
    }

    public static boolean contains(String key) { return VALUES.containsKey(key); }
    public static Map<String, Object> snapshot() { return Map.copyOf(VALUES); }
}
