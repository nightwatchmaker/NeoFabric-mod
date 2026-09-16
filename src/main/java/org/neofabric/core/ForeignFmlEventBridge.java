package org.neofabric.core;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Translates Forge-family @SubscribeEvent methods onto NeoFabric's shared bus. */
public final class ForeignFmlEventBridge {
    private ForeignFmlEventBridge() {}

    public record Result(int subscribers, List<String> diagnostics) {}

    public static Result register(Path jar, ClassLoader modLoader, EventBus bus) throws IOException {
        int subscribers = 0;
        List<String> diagnostics = new ArrayList<>();
        try (JarFile archive = new JarFile(jar.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.contains("module-info")) continue;
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    Class<?> type = Class.forName(className, false, modLoader);
                    Object instance = null;
                    for (Method method : type.getDeclaredMethods()) {
                        if (!isSubscribeEvent(method) || method.getParameterCount() != 1) continue;
                        if (!Modifier.isStatic(method.getModifiers()) && instance == null) {
                            try {
                                instance = ModConstructorBridge.construct(type, null);
                            } catch (IllegalStateException ignored) {
                                diagnostics.add("deferred subscriber " + className + ": requires native FML construction");
                                break;
                            }
                        }
                        Class<?> eventType = method.getParameterTypes()[0];
                        method.setAccessible(true);
                        Object target = Modifier.isStatic(method.getModifiers()) ? null : instance;
                        bus.registerUntyped(eventType, event -> {
                            try {
                                method.invoke(target, event);
                            } catch (ReflectiveOperationException error) {
                                throw new IllegalStateException("Foreign event subscriber failed: " + method, error);
                            }
                        });
                        subscribers++;
                    }
                } catch (LinkageError | ReflectiveOperationException error) {
                    diagnostics.add("skipped " + className + ": " + error.getClass().getSimpleName());
                }
            }
        }
        return new Result(subscribers, List.copyOf(diagnostics));
    }

    private static boolean isSubscribeEvent(Method method) {
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
