package org.neofabric.core;

/** Binds host APIs to the currently active compatibility session. */
public final class NeoFabricHostBridge {
    private static volatile EventBus events = new EventBus();
    private NeoFabricHostBridge() {}

    public static EventBus events() { return events; }
    public static void bind(EventBus eventBus) {
        events = java.util.Objects.requireNonNull(eventBus, "eventBus");
    }
}
