package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.compat.NoellesTimekeeperPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateless Timekeeper counter on Wathe's {@code ShopPurchase.AFTER}: a committed time-reduction purchase by the real
 * Timekeeper (owner decision Q5) lifts every curse. It must never throw back into {@code PlayerShopComponent.tryBuy}.
 * 挂在 Wathe {@code ShopPurchase.AFTER} 上的无状态计时员反制：真正的计时员（所有者决定 Q5）成功购买减少时间后解除所有诅咒。
 * 绝不能把异常抛回 {@code PlayerShopComponent.tryBuy}。
 */
public final class TimekeeperCounter {
    private static final Logger LOGGER = LoggerFactory.getLogger("sparkwitch");
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private TimekeeperCounter() {
    }

    /**
     * Server-authoritative AFTER listener. Wathe fires AFTER only after {@code onBuy} returned true and the price was
     * debited, and NoellesRoles lists the entry only for the current Timekeeper. The decision is identity-based:
     * the SparkStrength Coroner copy is excluded by the exact role, the SparkTraits Impostor inversion by the
     * SparkFactionAPI effective faction. SparkWitch never touches {@code TimekeeperShopHandler$1} or
     * {@code addTime} and never measures the time change. Neither the AFTER invoker nor {@code tryBuy} catches
     * exceptions (an escape would skip the shop sync), so any failure is logged once and grants no rescue.
     * 服务端权威的 AFTER 监听器。Wathe 仅在 {@code onBuy} 返回 true 且已扣款后才触发 AFTER，NoellesRoles 也只为当前
     * 计时员列出该条目。判定完全基于身份：SparkStrength 验尸官的复制条目由精确职业排除，SparkTraits 内鬼的反转由
     * SparkFactionAPI 有效阵营排除。SparkWitch 从不触碰 {@code TimekeeperShopHandler$1} 或 {@code addTime}，也从不测量
     * 时间变化。AFTER 调用器与 {@code tryBuy} 都不捕获异常（逃逸会跳过商店同步），因此任何失败只记录一次日志且不救人。
     */
    public static void onPurchase(ServerPlayerEntity buyer, ShopEntry entry, int index, int pricePaid) {
        // Item test first so ordinary purchases never resolve factions. / 先判物品，普通购买永不解析阵营。
        if (!NoellesTimekeeperPurchase.isTimeReductionEntry(entry)) {
            return;
        }
        try {
            ServerWorld world = buyer.getServerWorld();
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            if (TimeStealerRules.countsAsTimekeeperRescue(true,
                    NoellesRoleIds.isTimekeeper(game.getRole(buyer)),
                    SparkFactionApi.resolveEffectiveFaction(buyer, game),
                    GameFunctions.isPlayerPlayingAndAlive(buyer),
                    game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)) {
                TimeTheftRuntime.purgeAll(world, TimeTheftRuntime.PurgeCause.TIMEKEEPER);
            }
        } catch (RuntimeException exception) {
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Time Stealer Timekeeper counter failed; no curse was lifted for this purchase", exception);
            }
        }
    }
}
