package org.neofabric.core;

/** Host-neutral server heartbeat translated from the native loader event bus. */
public record ServerTickEvent(Object nativeEvent, boolean endPhase) {
}
