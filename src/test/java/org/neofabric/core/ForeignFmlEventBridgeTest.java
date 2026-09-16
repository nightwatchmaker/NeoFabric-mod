package org.neofabric.core;

import fixture.ForeignSubscriber;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.jar.JarEntry;

/** Verifies discovery and invocation from an external-style subscriber JAR. */
public final class ForeignFmlEventBridgeTest {
    private ForeignFmlEventBridgeTest() {}

    public static void main(String[] args) throws Exception {
        Path jar = Files.createTempFile("neofabric-foreign-subscriber", ".jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("fixture/ForeignSubscriber.class"));
            out.write(ForeignSubscriberBytes.bytes());
            out.closeEntry();
        }
        EventBus bus = new EventBus();
        var result = ForeignFmlEventBridge.register(jar,
                ForeignFmlEventBridgeTest.class.getClassLoader(), bus);
        if (result.subscribers() != 1) throw new AssertionError(result.diagnostics());
        var event = new EntityDamageEvent("entity", "source", 10.0f, "native");
        bus.post(event);
        if (event.amount() != 5.0f || !event.isCanceled()) {
            throw new AssertionError("external subscriber did not mutate/cancel event");
        }
        Files.deleteIfExists(jar);
        System.out.println("ForeignFmlEventBridgeTest: PASS");
    }

    private static final class ForeignSubscriberBytes {
        static byte[] bytes() throws Exception {
            try (var input = ForeignSubscriber.class.getResourceAsStream("/fixture/ForeignSubscriber.class")) {
                return input.readAllBytes();
            }
        }
    }
}
