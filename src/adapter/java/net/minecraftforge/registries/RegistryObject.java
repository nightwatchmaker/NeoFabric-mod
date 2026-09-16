package net.minecraftforge.registries;

import java.util.Objects;
import java.util.function.Supplier;
import org.neofabric.core.ForeignRegistryStore;

/** Forge-compatible lazy registry handle backed by NeoFabric's shared store. */
public final class RegistryObject<T> implements Supplier<T> {
    private final String registryName;
    private final String key;
    private final Supplier<? extends T> supplier;
    RegistryObject(String registryName, String key, Supplier<? extends T> supplier) {
        this.registryName = registryName;
        this.key = key;
        this.supplier = supplier;
    }
    @Override public T get() { return ForeignRegistryStore.resolve(registryName, key, supplier); }
    public boolean isPresent() { return ForeignRegistryStore.contains(key); }
    public String getId() { return key; }
    public T orElse(T fallback) { return isPresent() ? get() : Objects.requireNonNull(fallback); }
}
