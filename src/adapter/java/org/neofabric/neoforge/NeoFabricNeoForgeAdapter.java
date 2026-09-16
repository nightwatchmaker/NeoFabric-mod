package org.neofabric.neoforge;

import java.nio.file.Path;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.neofabric.core.LoaderKind;
import org.neofabric.core.MinecraftTarget;
import org.neofabric.core.NeoFabricCompatibilityLayer;
import org.neofabric.core.NeoFabricLoader;

/** NeoForge host adapter: native NeoForge remains the host runtime. */
@Mod("neofabric")
public final class NeoFabricNeoForgeAdapter {
    private static NeoFabricCompatibilityLayer layer;

    public NeoFabricNeoForgeAdapter(IEventBus modBus) {
        Path gameDirectory;
        try {
            gameDirectory = net.neoforged.fml.loading.FMLLoader.getCurrent().getGameDir();
        } catch (Throwable unavailable) {
            gameDirectory = Path.of(System.getProperty("user.dir", "."));
        }
        try {
            layer = new NeoFabricCompatibilityLayer(MinecraftTarget.MC_26_2,
                    NeoFabricNeoForgeAdapter.class.getClassLoader());
            var decisions = layer.start(gameDirectory, LoaderKind.NEOFORGE);
            int fabricEntrypoints = layer.initializeFabricEntrypoints("CLIENT");
            modBus.addListener(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent.class,
                    event -> layer.fireHostPhase(org.neofabric.core.LifecyclePhase.COMMON_SETUP, event));
            modBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class,
                    event -> layer.fireHostPhase(org.neofabric.core.LifecyclePhase.CLIENT_READY, event));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,
                    event -> layer.postHostEvent(new org.neofabric.core.ServerTickEvent(event, true)));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class,
                    event -> {
                        var translated = new org.neofabric.core.EntityDamageEvent(
                                event.getEntity(), event.getSource(), event.getAmount(), event);
                        layer.postHostEvent(translated);
                        event.setAmount(translated.amount());
                        if (translated.isCanceled()) event.setCanceled(true);
                    });
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.neoforge.event.level.block.BreakBlockEvent.class,
                    event -> {
                        var translated = new org.neofabric.core.BlockInteractionEvent(
                                "break", event.getLevel(), event.getPos(), event.getState(), event.getPlayer(), event);
                        layer.postHostEvent(translated);
                        event.setNotifyClient(translated.notifyClient());
                        if (translated.isCanceled()) event.setCanceled(true);
                    });
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class,
                    event -> {
                        var translated = new org.neofabric.core.BlockInteractionEvent(
                                "place", event.getLevel(), event.getPos(), event.getPlacedBlock(), event.getEntity(), event);
                        layer.postHostEvent(translated);
                        if (translated.isCanceled()) event.setCanceled(true);
                    });
            NeoFabricLoader loader = layer.loader();
            modBus.addListener(net.neoforged.neoforge.registries.RegisterEvent.class,
                    event -> NeoFabricNeoForgeRegistryBridge.registerMatching(event, loader));
            System.out.println("[NeoFabric] NeoForge host compatibility layer initialized for Minecraft 26.2; translated Fabric entrypoints: " + fabricEntrypoints);
            decisions.forEach(decision -> System.out.println("[NeoFabric] " + decision.mod().id()
                    + " -> " + (decision.accepted() ? "translated" : "rejected")
                    + " (" + decision.reason() + ")"));
        } catch (Exception error) {
            System.err.println("[NeoFabric] compatibility layer failed: " + error.getMessage());
        }
    }
}
