package org.neofabric.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/** Shared deterministic registry contract for Fabric, Forge, and NeoForge adapters. */
public final class NeoRegistry<T> {
    private final String registryId;
    private final Class<?> valueType;
    private final Map<String, T> entries = new LinkedHashMap<>();
    private final Map<String, Supplier<? extends T>> lazyEntries = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<RegistryEntryCallback<T>> callbacks = new CopyOnWriteArrayList<>();
    private boolean frozen;

    public NeoRegistry(String registryId) {
        this(registryId, Object.class);
    }

    /** Creates a registry that rejects values incompatible with its native value type. */
    public NeoRegistry(String registryId, Class<?> valueType) {
        this.registryId = Objects.requireNonNull(registryId, "registryId");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
    }

    public synchronized T register(String id, T value) {
        if (frozen) throw new IllegalStateException("Registry is frozen: " + registryId);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        if (!valueType.isInstance(value)) {
            throw new IllegalArgumentException("Value for " + registryId + " must be "
                    + valueType.getName() + " but was " + value.getClass().getName());
        }
        if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate " + registryId + " entry: " + id);
        entries.put(id, value);
        callbacks.forEach(callback -> callback.onRegistered(id, value, LoaderKind.UNKNOWN));
        return value;
    }

    /** Registers a factory that is evaluated by the native backend at registration time. */
    public synchronized void registerLazy(String id, Supplier<? extends T> factory) {
        if (frozen) throw new IllegalStateException("Registry is frozen: " + registryId);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (entries.containsKey(id) || lazyEntries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate " + registryId + " entry: " + id);
        }
        lazyEntries.put(id, factory);
    }

    public void onRegistered(RegistryEntryCallback<T> callback) {
        callbacks.add(Objects.requireNonNull(callback, "callback"));
    }

    void notifyRegistered(String id, T value, LoaderKind source) {
        callbacks.forEach(callback -> callback.onRegistered(id, value, source));
    }

    public synchronized T get(String id) {
        return entries.get(id);
    }

    public synchronized boolean contains(String id) {
        return entries.containsKey(id);
    }

    public synchronized Collection<T> values() {
        return java.util.List.copyOf(entries.values());
    }

    public synchronized Map<String, T> entries() {
        return Map.copyOf(entries);
    }

    public synchronized Map<String, Supplier<? extends T>> lazyEntries() {
        return Map.copyOf(lazyEntries);
    }

    /** Resolves one deferred value exactly once and promotes it into the visible registry entries. */
    public synchronized T resolveLazy(String id) {
        if (frozen) throw new IllegalStateException("Registry is frozen: " + registryId);
        T existing = entries.get(id);
        if (existing != null) return existing;
        Supplier<? extends T> factory = lazyEntries.remove(id);
        if (factory == null) throw new IllegalArgumentException("Unknown lazy " + registryId + " entry: " + id);
        T value = Objects.requireNonNull(factory.get(), "lazy registry value");
        if (!valueType.isInstance(value)) {
            throw new IllegalArgumentException("Value for " + registryId + " must be "
                    + valueType.getName() + " but was " + value.getClass().getName());
        }
        entries.put(id, value);
        callbacks.forEach(callback -> callback.onRegistered(id, value, LoaderKind.UNKNOWN));
        return value;
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean frozen() {
        return frozen;
    }

    public String id() {
        return registryId;
    }

    public Class<?> valueType() {
        return valueType;
    }
}
