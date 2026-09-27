package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Stateless Timekeeper counter on Wathe's {@code ShopPurchase.AFTER}: a committed time-reduction purchase by the real
 * Timekeeper (owner decision Q5) lifts every curse. It must never throw back into {@code PlayerShopComponent.tryBuy}.
 * 挂在 Wathe {@code ShopPurchase.AFTER} 上的无状态计时员反制：真正的计时员（所有者决定 Q5）成功购买减少时间后解除所有诅咒。
 * 绝不能把异常抛回 {@code PlayerShopComponent.tryBuy}。
 */
public final class TimekeeperCounter {
    private TimekeeperCounter() {
    }

    public static void onPurchase(ServerPlayerEntity buyer, ShopEntry entry, int index, int pricePaid) {
        // TODO(WP-04): item check -> TimeStealerRules.countsAsTimekeeperRescue -> TimeTheftRuntime.purgeAll; catch RuntimeException.
    }
}
