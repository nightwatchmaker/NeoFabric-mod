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
            var foreignFml = layer.registerForeignFmlEvents();
            modBus.addListener(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent.class,
                    event -> layer.fireHostPhase(org.neofabric.core.LifecyclePhase.COMMON_SETUP, event));
            modBus.addListener(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.class,
                    event -> layer.fireHostPhase(org.neofabric.core.LifecyclePhase.CLIENT_READY, event));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    net.minecraftforge.event.TickEvent.ServerTickEvent.class,
                    event -> layer.postHostEvent(new org.neofabric.core.ServerTickEvent(event, true)));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    net.minecraftforge.event.entity.living.LivingHurtEvent.class,
                    event -> {
                        var translated = new org.neofabric.core.EntityDamageEvent(
                                event.getEntity(), event.getSource(), event.getAmount(), event);
                        layer.postHostEvent(translated);
                        event.setAmount(translated.amount());
                        if (translated.isCanceled()) event.setCanceled(true);
                    });
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    net.minecraftforge.event.level.BlockEvent.BreakEvent.class,
                    event -> {
                        var translated = new org.neofabric.core.BlockInteractionEvent(
                                "break", event.getLevel(), event.getPos(), event.getState(), event.getPlayer(), event);
                        layer.postHostEvent(translated);
                        if (translated.isCanceled()) event.setCanceled(true);
                    });
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.class,
                    event -> {
                        var translated = new org.neofabric.core.BlockInteractionEvent(
                                "place", event.getLevel(), event.getPos(), event.getPlacedBlock(), event.getEntity(), event);
                        layer.postHostEvent(translated);
                        if (translated.isCanceled()) event.setCanceled(true);
                    });
            System.out.println("[NeoFabric] Forge host compatibility layer initialized for Minecraft 26.2; translated Fabric entrypoints: " + fabricEntrypoints);
            decisions.forEach(decision -> System.out.println("[NeoFabric] " + decision.mod().id()
                    + " -> " + (decision.accepted() ? "translated" : "rejected")
                    + " (" + decision.reason() + ")"));
        } catch (Exception error) {
            System.err.println("[NeoFabric] compatibility layer failed: " + error.getMessage());
        }
    }
}
