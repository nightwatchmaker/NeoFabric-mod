package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.IEventBus;
import org.neofabric.core.ForeignForgeEventBus;
import org.neofabric.core.NeoFabricHostBridge;

/** Minimal Forge API surface routed into NeoFabric when Fabric is the host. */
public final class MinecraftForge {
    public static final IEventBus EVENT_BUS = new Shim(NeoFabricHostBridge.events());
    private MinecraftForge() {}

    private static final class Shim implements IEventBus {
        private final ForeignForgeEventBus delegate;
        private Shim(org.neofabric.core.EventBus events) {
            delegate = new ForeignForgeEventBus(events);
        }
        @Override public void register(Object listener) { delegate.register(listener); }
        @Override public <T> void addListener(Class<T> eventType,
                                               java.util.function.Consumer<T> listener) {
            delegate.addListener(eventType, listener);
        }
        @Override public net.minecraftforge.eventbus.api.Event post(net.minecraftforge.eventbus.api.Event event) {
            delegate.post(event);
            return event;
        }
    }
}
