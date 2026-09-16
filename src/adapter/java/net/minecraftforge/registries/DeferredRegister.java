package net.minecraftforge.registries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraftforge.eventbus.api.IEventBus;

/** Minimal Forge DeferredRegister facade for translated mods on Fabric. */
public final class DeferredRegister<T> {
    private final String registryName;
    private final String modid;
    private final Map<String, RegistryObject<?>> entries = new LinkedHashMap<>();

    private DeferredRegister(String registryName, String modid) {
        this.registryName = registryName;
        this.modid = modid;
    }

    public static <T> DeferredRegister<T> create(Class<?> registryClass, String modid) {
        return new DeferredRegister<>(registryClass.getName(), modid);
    }

    public static <T> DeferredRegister<T> create(IForgeRegistry<?> registry, String modid) {
        return new DeferredRegister<>(registry.getRegistryName(), modid);
    }

    public static <T> DeferredRegister<T> create(String registryName, String modid) {
        return new DeferredRegister<>(registryName, modid);
    }
    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
        String key = modid + ":" + name;
        RegistryObject<I> object = new RegistryObject<>(registryName, key, supplier);
        entries.put(name, object);
        return object;
    }

    public void register(IEventBus eventBus) {
        // Registration is lazy; the host bridge resolves suppliers when handles are used.
    }

    public String registryName() { return registryName; }
    public String modid() { return modid; }
}
