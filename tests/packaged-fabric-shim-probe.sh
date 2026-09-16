#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
FABRIC_JAR="$ROOT/build/mods/NeoFabric-Fabric-0.3.0-dev.jar"
WORK="$ROOT/build/packaged-shim-probe"
rm -rf "$WORK"
mkdir -p "$WORK/src/fixture" "$WORK/classes"
cat > "$WORK/src/fixture/ExternalForgeMod.java" <<'JAVA'
package fixture;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.neofabric.core.EntityDamageEvent;
@Mod("externalforge")
public final class ExternalForgeMod {
    @SubscribeEvent
    public static void onDamage(EntityDamageEvent event) {
        event.setAmount(1.0f);
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
        var result = ForeignFmlEventBridge.register(Path.of(args[0]), Probe.class.getClassLoader(), bus);
        if (result.subscribers() != 1) throw new AssertionError(result.diagnostics());
        var event = new EntityDamageEvent("entity", "source", 9.0f, "native");
        bus.post(event);
        if (event.amount() != 1.0f || !event.isCanceled()) throw new AssertionError("round trip failed");
        DeferredRegister<String> registry = DeferredRegister.create(String.class, "externalforge");
        RegistryObject<String> value = registry.register("value", () -> "resolved");
        registry.register(MinecraftForge.EVENT_BUS);
        if (!"resolved".equals(value.get()) || !value.isPresent()) {
            throw new AssertionError("registry supplier did not resolve through packaged shim");
        }
        System.out.println("PackagedFabricShimProbe: PASS");
    }
}
JAVA
javac --release 17 -cp "$FABRIC_JAR" -d "$WORK/classes" "$WORK/Probe.java"
java -ea -cp "$FABRIC_JAR:$WORK/classes" Probe "$WORK/external-forge-mod.jar"
unzip -t "$FABRIC_JAR" >/dev/null
