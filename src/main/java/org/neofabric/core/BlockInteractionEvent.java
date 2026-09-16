package org.neofabric.core;

/** Common cancellable block interaction translated from a native host event. */
public final class BlockInteractionEvent implements CancellableEvent {
    private final String action;
    private final Object level;
    private final Object position;
    private final Object state;
    private final Object actor;
    private final Object nativeEvent;
    private boolean canceled;
    private boolean notifyClient = true;

    public BlockInteractionEvent(String action, Object level, Object position,
                                 Object state, Object actor, Object nativeEvent) {
        this.action = action;
        this.level = level;
        this.position = position;
        this.state = state;
        this.actor = actor;
        this.nativeEvent = nativeEvent;
    }
    public String action() { return action; }
    public Object level() { return level; }
    public Object position() { return position; }
    public Object state() { return state; }
    public Object actor() { return actor; }
    public Object nativeEvent() { return nativeEvent; }
    public boolean notifyClient() { return notifyClient; }
    public void setNotifyClient(boolean notifyClient) { this.notifyClient = notifyClient; }
    @Override public boolean isCanceled() { return canceled; }
    @Override public void setCanceled(boolean canceled) { this.canceled = canceled; }
}
