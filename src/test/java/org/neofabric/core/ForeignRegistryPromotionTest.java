package org.neofabric.core;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Verifies the core-to-native registry promotion contract without Minecraft classes. */
public final class ForeignRegistryPromotionTest {
    private ForeignRegistryPromotionTest() {}

    public static void main(String[] args) {
        AtomicReference<String> registry = new AtomicReference<>();
        AtomicReference<String> id = new AtomicReference<>();
        AtomicReference<Object> value = new AtomicReference<>();
        ForeignRegistryStore.bindNativeRegistrar((registryName, key, object) -> {
            registry.set(registryName);
            id.set(key);
            value.set(object);
            return object;
        });
        String resolved = ForeignRegistryStore.resolve(
                "minecraft:item", "realitem:test_item", (Supplier<String>) () -> "native-value");
        if (!"minecraft:item".equals(registry.get())
                || !"realitem:test_item".equals(id.get())
                || !"native-value".equals(value.get())
                || !"native-value".equals(resolved)) {
            throw new AssertionError("native registry promotion contract failed");
        }
        ForeignRegistryStore.bindNativeRegistrar(null);
        System.out.println("ForeignRegistryPromotionTest: PASS");
    }
}
