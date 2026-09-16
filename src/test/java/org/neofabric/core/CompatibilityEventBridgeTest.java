package org.neofabric.core;

/** Regression test for the two-way normalized event contract. */
public final class CompatibilityEventBridgeTest {
    private CompatibilityEventBridgeTest() {}

    public static void main(String[] args) {
        EventBus bus = new EventBus();
        var damage = new EntityDamageEvent("entity", "source", 8.0f, "native-damage");
        bus.register(EntityDamageEvent.class, event -> {
            event.setAmount(event.amount() / 2.0f);
            event.setCanceled(true);
        });
        bus.post(damage);
        if (damage.amount() != 4.0f || !damage.isCanceled()) {
            throw new AssertionError("damage mutation/cancellation did not cross shared bus");
        }

        var block = new BlockInteractionEvent("break", "level", "pos", "state", "actor", "native-block");
        bus.register(BlockInteractionEvent.class, event -> event.setNotifyClient(false));
        bus.post(block);
        if (block.notifyClient()) throw new AssertionError("block mutation did not cross shared bus");

        System.out.println("CompatibilityEventBridgeTest: PASS");
    }
}
