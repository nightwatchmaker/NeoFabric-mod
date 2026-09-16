package org.neofabric.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.neofabric.core.LoaderKind;
import org.neofabric.core.MinecraftTarget;
import org.neofabric.core.NeoFabricCompatibilityLayer;

import java.nio.file.Path;

/** Fabric host adapter: NeoFabric runs inside Fabric, like Wine inside Linux. */
public final class NeoFabricFabricAdapter implements ModInitializer {
    private static NeoFabricCompatibilityLayer layer;

    @Override
    public void onInitialize() {
        Path gameDirectory = FabricLoader.getInstance().getGameDir();
        try {
            layer = new NeoFabricCompatibilityLayer(MinecraftTarget.MC_26_2,
                    NeoFabricFabricAdapter.class.getClassLoader());
            var decisions = layer.start(gameDirectory, LoaderKind.FABRIC);
            int translatedEntrypoints = layer.initializeFabricEntrypoints("CLIENT");
            var foreignFml = layer.initializeForeignFmlMods();
            System.out.println("[NeoFabric] Fabric host compatibility layer initialized for Minecraft 26.2; translated Fabric entrypoints: " + translatedEntrypoints
                    + "; foreign FML constructed: " + foreignFml.constructed() + ", deferred: " + foreignFml.deferred());
            decisions.forEach(decision -> System.out.println("[NeoFabric] " + decision.mod().id()
                    + " -> " + (decision.accepted() ? "translated" : "rejected")
                    + " (" + decision.reason() + ")"));
        } catch (Exception error) {
            System.err.println("[NeoFabric] compatibility layer failed: " + error.getMessage());
        }
    }
}
