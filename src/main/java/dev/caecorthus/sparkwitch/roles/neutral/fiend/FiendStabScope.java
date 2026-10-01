package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Marks a hand-held knife stab (D5). Every hand-held stab resolves inside Wathe's {@code KnifeStabPayload} receiver:
 * Wathe's charged stab, the NoellesRoles Scavenger, and SparkStrength's Veteran, Silencer and Coroner HEAD handlers.
 * The main hand cannot prove it, because the Veteran's last stab removes the knife before {@code killPlayer}, and the
 * Black Raven feather-mark {@code wathe:knife_stab} runs from a server tick outside any receiver. Server thread only;
 * scoped only while the payload targets a Fiend.
 * 标记手持刀刺击（D5）。所有手持刺击都在 Wathe 的 {@code KnifeStabPayload} 接收器内结算：Wathe 蓄力刺、NoellesRoles
 * 拾荒者，以及 SparkStrength 老兵、沉默者、验尸官的 HEAD 处理器。主手无法证明这一点：老兵的最后一刺会在
 * {@code killPlayer} 前移除刀，而黑羽鸦羽毛标记的 {@code wathe:knife_stab} 由服务端刻触发、不在任何接收器内。
 * 仅服务端线程；只在数据包目标为魔人时建立作用域。
 */
public final class FiendStabScope {
    private static final ThreadLocal<UUID> STABBER = new ThreadLocal<>();

    private FiendStabScope() {
    }

    /**
     * Called only by {@code KnifeStabPayloadFiendStabScopeMixin}: runs the receiver exactly once, scoped when the target
     * is a Fiend. / 仅由 {@code KnifeStabPayloadFiendStabScopeMixin} 调用：恰好执行一次接收器，目标为魔人时建立作用域。
     */
    public static void runStab(ServerPlayerEntity attacker, int targetId, Runnable receiver) {
        Entity target = attacker.getServerWorld().getEntityById(targetId);
        if (!(target instanceof PlayerEntity victim) || !FiendParticipation.isFiend(victim)) {
            receiver.run();
            return;
        }
        UUID previous = STABBER.get();
        STABBER.set(attacker.getUuid());
        try {
            receiver.run();
        } finally {
            if (previous == null) {
                STABBER.remove();
            } else {
                STABBER.set(previous);
            }
        }
    }

    /** Whether this killer is resolving a hand-held knife stab right now. / 该击杀者当前是否正在结算手持刀刺击。 */
    public static boolean isStabbing(@Nullable PlayerEntity killer) {
        return killer != null && killer.getUuid().equals(STABBER.get());
    }
}
