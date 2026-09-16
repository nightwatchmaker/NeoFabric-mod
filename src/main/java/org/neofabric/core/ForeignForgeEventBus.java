package org.neofabric.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Minimal runtime facade used by the Fabric-host Forge API shim. */
public final class ForeignForgeEventBus {
    private final EventBus delegate;

    public ForeignForgeEventBus(EventBus delegate) {
        this.delegate = delegate;
    }

    public void register(Object listener) {
        Class<?> type = listener instanceof Class<?> c ? c : listener.getClass();
        Object target = listener instanceof Class<?> || listener == null ? null : listener;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() != 1 || !hasSubscribeAnnotation(method)) continue;
            if (target == null && !Modifier.isStatic(method.getModifiers())) continue;
            method.setAccessible(true);
            EventPriority priority = priorityOf(method);
            delegate.registerUntyped(method.getParameterTypes()[0], priority, event -> {
                try {
                    method.invoke(target, event);
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Foreign Forge event subscriber failed: " + method, error);
                }
            });
        }
    }

    public <T> void addListener(Class<T> eventType, java.util.function.Consumer<T> listener) {
        delegate.register(eventType, listener);
    }

    private static EventPriority priorityOf(Method method) {
        try {
            for (var annotation : method.getAnnotations()) {
                String name = annotation.annotationType().getName();
                if (!name.equals("net.minecraftforge.eventbus.api.SubscribeEvent")
                        && !name.equals("net.neoforged.bus.api.SubscribeEvent")) continue;
                Object value = annotation.annotationType().getMethod("priority").invoke(annotation);
                return EventPriority.valueOf(((Enum<?>) value).name());
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            // Unknown foreign priority metadata safely falls back to normal ordering.
        }
        return EventPriority.NORMAL;
    }

    private static boolean hasSubscribeAnnotation(Method method) {
        try {
            for (var annotation : method.getAnnotations()) {
                String name = annotation.annotationType().getName();
                if (name.equals("net.minecraftforge.eventbus.api.SubscribeEvent")
                        || name.equals("net.neoforged.bus.api.SubscribeEvent")) return true;
            }
        } catch (TypeNotPresentException ignored) {
            return false;
        }
        return false;
    }
}
