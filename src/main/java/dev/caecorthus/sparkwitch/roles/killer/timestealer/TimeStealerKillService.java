package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Settles a lethal curse exactly once: a forced, SparkTraits-terminal {@code TIME_STOLEN} kill inside the Judge ledger,
 * then the confirmed-death time penalty and the stamp grant (plan D6).
 * 对致死诅咒只结算一次：在审判者账本内执行强制、SparkTraits 终结的 {@code TIME_STOLEN} 击杀，
 * 随后在确认死亡后扣除时间并发放邮票（计划 D6）。
 */
public final class TimeStealerKillService {
    private TimeStealerKillService() {
    }

    static void settle(ServerPlayerEntity victim, UUID stealerUuid) {
        // TODO(WP-02): plan §3.5 settle (SettleActor, runWith + killPlayer(force), isPlayerDead, time, grant).
    }
}
