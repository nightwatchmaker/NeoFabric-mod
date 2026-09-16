package net.minecraftforge.registries;

/** Common Forge registry identities exposed to translated mods. */
public final class ForgeRegistries {
    public static final IForgeRegistry<Object> ITEMS = named("minecraft:item");
    public static final IForgeRegistry<Object> BLOCKS = named("minecraft:block");
    public static final IForgeRegistry<Object> ENTITY_TYPES = named("minecraft:entity_type");
    public static final IForgeRegistry<Object> FLUIDS = named("minecraft:fluid");
    public static final IForgeRegistry<Object> MENUS = named("minecraft:menu");
    private ForgeRegistries() {}

    private static IForgeRegistry<Object> named(String name) {
        return () -> name;
    }
}
