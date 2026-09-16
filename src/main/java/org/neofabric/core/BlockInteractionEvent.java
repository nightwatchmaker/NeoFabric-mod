package org.neofabric.core;

/** Common block-world interaction translated from a native host event. */
public record BlockInteractionEvent(String action, Object level, Object position,
                                    Object state, Object actor, Object nativeEvent) {
}
