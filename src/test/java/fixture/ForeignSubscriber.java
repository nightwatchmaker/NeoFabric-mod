package fixture;

import net.neoforged.bus.api.SubscribeEvent;
import org.neofabric.core.EntityDamageEvent;

public final class ForeignSubscriber {
    private ForeignSubscriber() {}

    @SubscribeEvent
    public static void onDamage(EntityDamageEvent event) {
        event.setAmount(event.amount() / 2.0f);
        event.setCanceled(true);
    }
}
