#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
FABRIC_JAR="$ROOT/build/mods/NeoFabric-Fabric-0.3.0-dev.jar"
WORK="$ROOT/build/packaged-shim-probe"
rm -rf "$WORK"
mkdir -p "$WORK/src/fixture" "$WORK/classes"
cat > "$WORK/src/fixture/ExternalForgeMod.java" <<'JAVA'
package fixture;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.neofabric.core.EntityDamageEvent;
@Mod("externalforge")
public final class ExternalForgeMod {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void first(EntityDamageEvent event) {
        event.setAmount(2.0f);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void last(EntityDamageEvent event) {
        event.setAmount(event.amount() * 10.0f);
        event.setCanceled(true);
    }
}
JAVA
javac --release 17 -cp "$FABRIC_JAR" -d "$WORK/classes" "$WORK/src/fixture/ExternalForgeMod.java"
jar --create --file "$WORK/external-forge-mod.jar" -C "$WORK/classes" fixture/ExternalForgeMod.class
cat > "$WORK/Probe.java" <<'JAVA'
import java.nio.file.Path;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.neofabric.core.*;
public final class Probe {
    public static void main(String[] args) throws Exception {
        EventBus bus = new EventBus();
        NeoFabricHostBridge.bind(bus);
        Class<?> foreignClass = Class.forName("fixture.ExternalForgeMod");
        MinecraftForge.EVENT_BUS.register(foreignClass);
        var event = new EntityDamageEvent("entity", "source", 9.0f, "native");
        bus.post(event);
        if (event.amount() != 20.0f || !event.isCanceled()) throw new AssertionError("priority round trip failed");
        DeferredRegister<String> registry = DeferredRegister.create(String.class, "externalforge");
        RegistryObject<String> value = registry.register("value", () -> "resolved");
        registry.register(MinecraftForge.EVENT_BUS);
        if (value.isPresent()) throw new AssertionError("registry resolved before common setup");
        bus.post(new LifecycleEvent(LifecyclePhase.COMMON_SETUP, "probe"));
        if (!value.isPresent() || !"resolved".equals(value.get())) {
            throw new AssertionError("registry supplier did not resolve at common setup");
        }
        System.out.println("PackagedFabricShimProbe: PASS");
    }
}
JAVA
javac --release 17 -cp "$FABRIC_JAR" -d "$WORK/classes" "$WORK/Probe.java"
java -ea -cp "$FABRIC_JAR:$WORK/classes" Probe "$WORK/external-forge-mod.jar"
unzip -t "$FABRIC_JAR" >/dev/null
