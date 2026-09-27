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

    /**
     * KillPlayer.AFTER for a Taotie victim (not intercepted); registered by this service's own {@link #register()}.
     * 饕餮受害者的 KillPlayer.AFTER（未被拦截）；由本服务自己的 {@link #register()} 注册。
     */
    public static void onKill(ServerPlayerEntity victim) {
    }

    /**
     * Called by the owner's component tick every 20 ticks while the car is SWALLOWED or PendingReturn is set. Sole
     * owner of both the return poll (that Taotie dead or no longer a Taotie) and the PendingReturn retry; the return
     * itself is {@code SeekerDeviceService.returnSwallowedCar}.
     * 当小车处于 SWALLOWED 或设置了 PendingReturn 时，由拥有者组件刻每 20 刻调用一次。独自负责归还轮询
     * （该饕餮死亡或不再是饕餮）与 PendingReturn 重试；归还本身由 {@code SeekerDeviceService.returnSwallowedCar} 执行。
     */
    public static void tick(ServerPlayerEntity owner, SeekerStatusComponent component) {
    }
}
