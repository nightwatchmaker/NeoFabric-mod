package org.neofabric.core;

/** Common cancellable damage signal translated from a native host event. */
public final class EntityDamageEvent implements CancellableEvent {
    private final Object entity;
    private final Object source;
    private float amount;
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
    public void setAmount(float amount) {
        if (!Float.isFinite(amount) || amount < 0.0f) {
            throw new IllegalArgumentException("Damage amount must be finite and non-negative");
        }
        this.amount = amount;
    }
    public Object nativeEvent() { return nativeEvent; }
    @Override public boolean isCanceled() { return canceled; }
    @Override public void setCanceled(boolean canceled) { this.canceled = canceled; }
}
