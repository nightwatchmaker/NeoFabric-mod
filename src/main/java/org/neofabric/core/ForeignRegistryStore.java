package org.neofabric.core;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Shared supplier-backed registry store used by the Fabric Forge API shim. */
public final class ForeignRegistryStore {
    private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();
    private ForeignRegistryStore() {}

    public static <T> T resolve(String key, Supplier<? extends T> supplier) {
        Objects.requireNonNull(key, "key");
        return (T) VALUES.computeIfAbsent(key, ignored -> Objects.requireNonNull(supplier.get(), "registry value"));
    }

    public static boolean contains(String key) { return VALUES.containsKey(key); }
    public static Map<String, Object> snapshot() { return Map.copyOf(VALUES); }
}
