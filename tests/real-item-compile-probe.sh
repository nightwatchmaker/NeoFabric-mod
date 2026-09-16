#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
FABRIC_JAR="$ROOT/build/mods/NeoFabric-Fabric-0.3.0-dev.jar"
MC_JAR="/root/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar"
WORK="$ROOT/build/real-item-compile-probe"
rm -rf "$WORK"
mkdir -p "$WORK/src/fixture" "$WORK/classes"
cat > "$WORK/src/fixture/RealItemForgeMod.java" <<'JAVA'
package fixture;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.Item;
@Mod("realitem")
public final class RealItemForgeMod {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, "realitem");
    public static final RegistryObject<Item> TEST_ITEM =
        ITEMS.register("test_item", () -> new Item(new Item.Properties()));
}
JAVA
javac --release 25 -cp "$FABRIC_JAR:$MC_JAR" -d "$WORK/classes" "$WORK/src/fixture/RealItemForgeMod.java"
printf 'RealItemForgeCompileProbe: PASS\n'
