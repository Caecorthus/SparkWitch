package dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarSwallowC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Frozen contract: the Taotie car swallow (all NoellesRoles calls live here or in its bridge) and the car return when
 * that Taotie dies or loses the role.
 * TODO(WP-06): implement. / 待 WP-06 实现。
 * 冻结契约：饕餮吞车（所有 NoellesRoles 调用都在此处或其桥接中）以及该饕餮死亡或失去身份时的小车归还。
 */
public final class SeekerTaotieService {
    private SeekerTaotieService() {
    }

    public static void register() {
        // TODO(WP-06) / 待 WP-06 实现
    }

    public static void handleSwallow(ServerPlayerEntity taotie, SeekerCarSwallowC2SPacket packet) {
    }

    /** KillPlayer.AFTER for a Taotie victim (not intercepted). / 饕餮受害者的 KillPlayer.AFTER（未被拦截）。 */
    public static void onKill(ServerPlayerEntity victim) {
    }

    /** Owner-side polling every 20 ticks and PendingReturn retry. / 拥有者侧每 20 刻轮询并重试 PendingReturn。 */
    public static void tick(ServerPlayerEntity owner, SeekerStatusComponent component) {
    }
}
