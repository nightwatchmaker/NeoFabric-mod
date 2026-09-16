package org.neofabric.neoforge;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.particles.ParticleType;
import org.neofabric.core.NeoFabricLoader;
import org.neofabric.core.NeoRegistry;

/** NeoForge API-specific registry bridge kept outside dependency-free NeoFabric Core. */
public final class NeoFabricNeoForgeRegistryBridge {
    private NeoFabricNeoForgeRegistryBridge() {
    }

    public static void registerMatching(RegisterEvent event, NeoFabricLoader loader) {
        String registryId = event.getRegistryKey().identifier().toString();
        NeoRegistry<?> shared = loader.registries().get(registryId);
        if (shared == null) return;
        registerEntries(event, shared);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerEntries(RegisterEvent event, NeoRegistry<?> shared) {
        Class<?> expectedType = expectedType(event.getRegistryKey().identifier().toString());
        for (Map.Entry<String, ?> entry : shared.entries().entrySet()) {
            event.register((net.minecraft.resources.ResourceKey) event.getRegistryKey(),
                    Identifier.parse(entry.getKey()), valueSupplier(expectedType, shared.id(), entry.getKey(), () -> entry.getValue()));
        }
        for (String entryId : shared.lazyEntries().keySet()) {
            event.register((net.minecraft.resources.ResourceKey) event.getRegistryKey(),
                    Identifier.parse(entryId), valueSupplier(expectedType, shared.id(), entryId, () -> shared.resolveLazy(entryId)));
        }
    }

    private static java.util.function.Supplier<?> valueSupplier(Class<?> expectedType, String registryId,
            String entryId, java.util.function.Supplier<?> source) {
        java.util.concurrent.atomic.AtomicReference<Object> cached = new java.util.concurrent.atomic.AtomicReference<>();
        return () -> {
            Object existing = cached.get();
            if (existing != null) return existing;
            synchronized (cached) {
                existing = cached.get();
                if (existing != null) return existing;
                Object value = source.get();
                if (value == null || (expectedType != null && !expectedType.isInstance(value))) {
                    throw new IllegalArgumentException("NeoFabric registry " + registryId + " requires "
                            + (expectedType == null ? "a non-null value" : expectedType.getName()) + " but "
                            + entryId + " produced " + (value == null ? "null" : value.getClass().getName()));
                }
                cached.set(value);
                return value;
            }
        };
    }

    private static Class<?> expectedType(String registryId) {
        return switch (registryId) {
            case "minecraft:item" -> Item.class;
            case "minecraft:block" -> Block.class;
            case "minecraft:entity_type" -> EntityType.class;
            case "minecraft:fluid" -> Fluid.class;
            case "minecraft:menu" -> MenuType.class;
            case "minecraft:mob_effect" -> MobEffect.class;
            case "minecraft:potion" -> Potion.class;
            case "minecraft:recipe_serializer" -> RecipeSerializer.class;
            case "minecraft:recipe_type" -> RecipeType.class;
            case "minecraft:sound_event" -> SoundEvent.class;
            case "minecraft:particle_type" -> ParticleType.class;
            default -> null;
        };
    }
}
