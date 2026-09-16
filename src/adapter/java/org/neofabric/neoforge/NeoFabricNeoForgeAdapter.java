package org.neofabric.neoforge;

import java.nio.file.Path;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.neofabric.core.CompatibilityCoordinator;
import org.neofabric.core.MinecraftTarget;
import org.neofabric.core.ModDiscovery;
import org.neofabric.core.NeoFabricLoader;

/** NeoForge 26.2 host adapter using the real FML @Mod bootstrap contract. */
@Mod("neofabric")
public final class NeoFabricNeoForgeAdapter {
    public NeoFabricNeoForgeAdapter(IEventBus modBus) {
        Path modsDirectory;
        try {
            modsDirectory = net.neoforged.fml.loading.FMLLoader.getCurrent().getGameDir().resolve("mods");
        } catch (Throwable unavailable) {
            modsDirectory = Path.of(System.getProperty("user.dir", "."), "mods");
        }
        NeoFabricLoader loader = net.neoforged.fml.neofabric.NeoFabricBootstrap.currentLoader();
        if (loader == null) loader = new NeoFabricLoader(MinecraftTarget.MC_26_2);
        try {
            var decisions = loader.loadCatalog(modsDirectory);
            final NeoFabricLoader fallbackLoader = loader;
            modBus.addListener(net.neoforged.neoforge.registries.RegisterEvent.class,
                    event -> {
                        NeoFabricLoader activeLoader = net.neoforged.fml.neofabric.NeoFabricBootstrap.currentLoader();
                        NeoFabricNeoForgeRegistryBridge.registerMatching(event,
                                activeLoader != null ? activeLoader : fallbackLoader);
                    });
            System.out.println("[NeoFabric] NeoForge compatibility adapter initialized for Minecraft 26.2");
            decisions.forEach(decision -> System.out.println("[NeoFabric] " + decision.mod().id()
                    + " -> " + (decision.accepted() ? "accepted" : "rejected")
                    + " (" + decision.reason() + ")"));
        } catch (Exception error) {
            System.err.println("[NeoFabric] compatibility scan failed: " + error.getMessage());
        }
    }
}
