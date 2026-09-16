package net.minecraftforge.eventbus.api;

/** Minimal Forge event base for the Fabric-host compatibility shim. */
public class Event {
    private boolean canceled;
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
}
