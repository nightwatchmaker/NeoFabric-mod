package org.neofabric.core;

import java.io.IOException;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Conservative bridge for Forge-family mods hosted by a non-FML loader.
 *
 * <p>FML normally supplies an event bus and owns construction. A Fabric host
 * cannot safely fabricate that API, so this bridge only constructs public,
 * concrete, no-argument @Mod classes. Event-bus constructors are reported as
 * deferred instead of being instantiated with an incompatible object.</p>
 */
public final class ForeignFmlModBridge {
    private static final String FORGE_MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private static final String NEOFORGE_MOD = "Lnet/neoforged/fml/common/Mod;";

    private ForeignFmlModBridge() {}

    public record Result(int constructed, int deferred, List<String> diagnostics) {}

    public static Result initialize(Path jar, ClassLoader modLoader) throws IOException {
        int constructed = 0;
        int deferred = 0;
        List<String> diagnostics = new ArrayList<>();
        try (JarFile archive = new JarFile(jar.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.contains("module-info")) continue;
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                try {
                    Class<?> type = Class.forName(className, false, modLoader);
                    if (!isForeignMod(type)) continue;
                    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) continue;
                    try {
                        ModConstructorBridge.construct(type, null);
                        constructed++;
                        diagnostics.add("constructed " + className);
                    } catch (IllegalStateException error) {
                        deferred++;
                        diagnostics.add("deferred " + className + ": requires native FML construction/event bus");
                    }
                } catch (LinkageError | ClassNotFoundException error) {
                    diagnostics.add("skipped " + className + ": " + error.getClass().getSimpleName());
                }
            }
        }
        return new Result(constructed, deferred, List.copyOf(diagnostics));
    }

    private static boolean isForeignMod(Class<?> type) {
        try {
            for (var annotation : type.getAnnotations()) {
                String name = annotation.annotationType().getName();
                if (name.equals("net.minecraftforge.fml.common.Mod")
                        || name.equals("net.neoforged.fml.common.Mod")) return true;
            }
            return false;
        } catch (TypeNotPresentException | GenericSignatureFormatError error) {
            return false;
        }
    }
}
