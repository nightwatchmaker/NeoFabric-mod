package org.neofabric.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class FabricEntrypointBridgeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("neofabric-entrypoint-test");
        Path source = root.resolve("ModEntry.java");
        Files.writeString(source, "package fixture; public final class ModEntry implements org.neofabric.core.NeoFabricRegistryEntrypoint { public void onInitialize() { System.setProperty(\"neofabric.fixture\", \"ok\"); } public void register(org.neofabric.core.NeoRegistryCatalog registries, org.neofabric.core.LoaderRegistryAdapter adapter) { var items = registries.create(\"fixture:items\"); adapter.register(items, \"fixture:test_item\", \"registered\"); } public static final class ClientEntry { public void onInitializeClient() { System.setProperty(\"neofabric.client.fixture\", \"ok\"); } } }");
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null || compiler.run(null, null, null, "-cp", System.getProperty("java.class.path"), "-d", root.toString(), source.toString()) != 0) {
            throw new AssertionError("fixture compilation failed");
        }
        Path jar = root.resolve("fixture.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            write(output, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"fixture\",\"version\":\"1\",\"mixins\":[\"fixture.mixins.json\"],\"entrypoints\":{\"main\":[\"fixture.ModEntry\"],\"client\":[{\"value\":\"fixture.ModEntry$ClientEntry\",\"adapter\":\"default\"}],\"neofabric:registry\":[\"fixture.ModEntry\"]}}");
            write(output, "fixture.mixins.json", "{\"required\":true,\"package\":\"fixture.mixin\",\"compatibilityLevel\":\"JAVA_17\",\"mixins\":[]}");
            output.putNextEntry(new JarEntry("fixture/ModEntry.class"));
            output.write(Files.readAllBytes(root.resolve("fixture/ModEntry.class")));
            output.closeEntry();
            output.putNextEntry(new JarEntry("fixture/ModEntry$ClientEntry.class"));
            output.write(Files.readAllBytes(root.resolve("fixture/ModEntry$ClientEntry.class")));
            output.closeEntry();
        }
        FabricModInfo info = FabricMetadataAdapter.read(jar);
        assert info.mixins().equals(java.util.List.of("fixture.mixins.json"));
        assert FabricMixinConfigDiscovery.validate(jar, info).equals(java.util.List.of("fixture.mixins.json"));
        Path game = root.resolve("game");
        Files.createDirectories(game.resolve("mods"));
        Files.copy(jar, game.resolve("mods/fixture.jar"));
        System.clearProperty("neofabric.fixture");
        System.clearProperty("neofabric.client.fixture");
        var bootLoader = org.neofabric.launcher.NeoFabricLauncher.initializeCore(game);
        assert "ok".equals(System.getProperty("neofabric.fixture"));
        assert "ok".equals(System.getProperty("neofabric.client.fixture"));
        assert "registered".equals(bootLoader.registries().get("fixture:items").get("fixture:test_item"));
        try (var modLoader = new CompatibilityClassLoader(jar, FabricEntrypointBridgeTest.class.getClassLoader())) {
            var initialized = FabricEntrypointBridge.initialize(info, modLoader);
            assert initialized.equals(java.util.List.of("fixture.ModEntry", "fixture.ModEntry$ClientEntry"));
            var registry = new NeoRegistryCatalog();
            var registered = FabricEntrypointBridge.initializeRegistries(info, modLoader, registry,
                    new LoaderRegistryAdapter(LoaderKind.FABRIC));
            assert registered.equals(java.util.List.of("fixture.ModEntry"));
            assert "registered".equals(registry.get("fixture:items").get("fixture:test_item"));
            assert "ok".equals(System.getProperty("neofabric.fixture"));
        }
        System.out.println("FabricEntrypointBridgeTest: PASS");
    }

    private static void write(JarOutputStream output, String name, String text) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
