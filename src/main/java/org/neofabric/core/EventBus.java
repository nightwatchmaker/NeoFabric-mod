package org.neofabric.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe event hub with assignable-type dispatch and deterministic priorities. */
public final class EventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Listener>> handlers = new ConcurrentHashMap<>();
    private long sequence;

    public <T> void register(Class<T> eventType, Consumer<T> handler) {
        register(eventType, EventPriority.NORMAL, handler);
    }

    public <T> void register(Class<T> eventType, EventPriority priority, Consumer<T> handler) {
        long order;
        synchronized (this) {
            order = sequence++;
        }
        handlers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>())
                .add(new Listener(priority, order, event -> handler.accept(eventType.cast(event))));
    }

    public void registerUntyped(Class<?> eventType, Consumer<Object> handler) {
        registerUntyped(eventType, EventPriority.NORMAL, handler);
    }

    public void registerUntyped(Class<?> eventType, EventPriority priority, Consumer<Object> handler) {
        registerInternal(eventType, priority, handler);
    }

    private void registerInternal(Class<?> eventType, EventPriority priority, Consumer<Object> handler) {
        long order;
        synchronized (this) {
            order = sequence++;
        }
        handlers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>())
                .add(new Listener(priority, order, handler));
    }

    public void post(Object event) {
        List<Listener> matching = new ArrayList<>();
        handlers.forEach((eventType, listeners) -> {
            if (eventType.isInstance(event)) matching.addAll(listeners);
        });
        matching.sort(Comparator.comparing((Listener listener) -> listener.priority)
                .thenComparingLong(listener -> listener.order));
        for (Listener listener : matching) {
            if (event instanceof CancellableEvent cancellable && cancellable.isCanceled()) break;
            listener.handler.accept(event);
        }
    }

    private record Listener(EventPriority priority, long order, Consumer<Object> handler) {
    }
}
