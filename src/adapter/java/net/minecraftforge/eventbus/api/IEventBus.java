package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

public interface IEventBus {
    void register(Object listener);
    <T> void addListener(Class<T> eventType, Consumer<T> listener);
    Event post(Event event);
}
