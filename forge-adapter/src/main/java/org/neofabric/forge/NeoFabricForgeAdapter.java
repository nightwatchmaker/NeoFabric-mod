package org.neofabric.forge;

import net.minecraftforge.fml.common.Mod;
import org.neofabric.core.LoaderKind;
import org.neofabric.core.MinecraftTarget;
import org.neofabric.core.NeoFabricCompatibilityLayer;

import java.nio.file.Path;

/** Forge host adapter: native Forge remains the host runtime. */
@Mod("neofabric")
public final class NeoFabricForgeAdapter {
    public NeoFabricForgeAdapter(net.minecraftforge.eventbus.api.IEventBus modBus) {
        Path gameDirectory = Path.of(System.getProperty("user.dir", "."));
        try {
            var layer = new NeoFabricCompatibilityLayer(MinecraftTarget.MC_26_2,
                    NeoFabricForgeAdapter.class.getClassLoader());
            var decisions = layer.start(gameDirectory, LoaderKind.FORGE);
            int fabricEntrypoints = layer.initializeFabricEntrypoints("CLIENT");
            modBus.addListener(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent.class,
                    event -> layer.fireHostPhase(org.neofabric.core.LifecyclePhase.COMMON_SETUP, event));
            modBus.addListener(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.class,
                    event -> layer.fireHostPhase(org.neofabric.core.LifecyclePhase.CLIENT_READY, event));
            modBus.addListener(net.minecraftforge.event.TickEvent.ServerTickEvent.class,
                    event -> layer.postHostEvent(new org.neofabric.core.ServerTickEvent(event, true)));
            System.out.println("[NeoFabric] Forge host compatibility layer initialized for Minecraft 26.2; translated Fabric entrypoints: " + fabricEntrypoints);
            decisions.forEach(decision -> System.out.println("[NeoFabric] " + decision.mod().id()
                    + " -> " + (decision.accepted() ? "translated" : "rejected")
                    + " (" + decision.reason() + ")"));
        } catch (Exception error) {
            System.err.println("[NeoFabric] compatibility layer failed: " + error.getMessage());
        }
    }
}
