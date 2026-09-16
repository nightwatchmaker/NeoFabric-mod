package org.neofabric.core;

/** Common damage signal translated from a native Forge-family gameplay event. */
public record EntityDamageEvent(Object entity, Object source, float amount, Object nativeEvent) {
}
