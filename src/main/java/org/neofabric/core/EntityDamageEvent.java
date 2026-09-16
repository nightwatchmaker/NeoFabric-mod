package org.neofabric.core;

/** Common cancellable damage signal translated from a native host event. */
public final class EntityDamageEvent implements CancellableEvent {
    private final Object entity;
    private final Object source;
    private final float amount;
    private final Object nativeEvent;
    private boolean canceled;

    public EntityDamageEvent(Object entity, Object source, float amount, Object nativeEvent) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
        this.nativeEvent = nativeEvent;
    }

    public Object entity() { return entity; }
    public Object source() { return source; }
    public float amount() { return amount; }
    public Object nativeEvent() { return nativeEvent; }
    @Override public boolean isCanceled() { return canceled; }
    @Override public void setCanceled(boolean canceled) { this.canceled = canceled; }
}
