package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * External seam (SparkFactionAPI {@code PlayerAffectPolicy}, any denial wins): no player-affect action may target a
 * player inside a Rift Gate (D3: occupants are untouchable), except {@code noellesroles:swapper}, which must reach
 * P9's portal crush (D13), and {@code wathe:poison}, the poisoner-attributed poison death that D3 keeps lethal. This
 * closes the NoellesRoles packets that ignore spectators (Assassin guess, Swapper, Morph/Voodoo, Reporter, Detective,
 * Silencer, Party Animal) and every killer-attributed {@code killPlayer} (SFA uses the death reason as the action).
 * SFA does not catch policy exceptions, so this one fails closed itself: an error while the target may be inside
 * denies. Side-neutral: on a client only the owner's own state is known, the server decides.
 * 外部接缝（SparkFactionAPI {@code PlayerAffectPolicy}，任一拒绝即生效）：任何玩家影响行为都不能作用于位于裂隙门内的玩家
 * （D3：门内的人打不死），只放行必须到达 P9 传送门夹死逻辑的 {@code noellesroles:swapper}（D13）与 D3 规定仍会致死、
 * 可归因到下毒者的毒杀 {@code wathe:poison}。这同时封住了不检查旁观者的 NoellesRoles 数据包（刺客猜测、交换者、变形/巫毒、
 * 记者、侦探、沉默者、派对动物）以及所有归因到击杀者的 {@code killPlayer}（SFA 以死因作为行为 id）。SFA 不捕获策略异常，
 * 因此本策略自行失败即关闭：目标可能在门内时出错一律拒绝。两端通用：客户端只知道拥有者自己的状态，由服务端裁决。
 */
final class RiftSessionAffectPolicy {
    private static final AtomicBoolean ERROR_LOGGED = new AtomicBoolean();
    private static boolean registered;

    private RiftSessionAffectPolicy() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerPlayerAffectPolicy((actor, target, actionId, gameComponent) -> {
            try {
                return RiftSessionRules.occupantPolicyAllows(RiftSessionService.isInside(target),
                        isSelf(actor, target), actionId);
            } catch (RuntimeException | LinkageError error) {
                if (ERROR_LOGGED.compareAndSet(false, true)) {
                    SparkWitch.LOGGER.error("Rift Gate occupant affect policy failed; denying", error);
                }
                return false;
            }
        });
    }

    private static boolean isSelf(PlayerEntity actor, PlayerEntity target) {
        return actor != null && target != null && actor.getUuid().equals(target.getUuid());
    }
}
