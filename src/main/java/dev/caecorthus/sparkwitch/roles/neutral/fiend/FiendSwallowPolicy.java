package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * D4: the Taotie can never swallow a dormant Fiend; a moment Fiend is swallowable normally (its shield may block one).
 * Enforced by SparkWitch's own guard on NoellesRoles {@code TaotiePlayerComponent.swallowPlayer}, the only code that
 * marks a player swallowed. SparkFactionAPI's Taotie affect guard targets {@code lambda$registerPackets$40} with
 * {@code require = 0}, which does not exist in the pinned NoellesRoles 1.7.6 (the receiver is
 * {@code lambda$registerPackets$9}), so an affect policy would never run. Server only.
 * D4：饕餮永远不能吞下休眠魔人；时刻中的魔人照常可被吞（其护盾可抵挡一次）。由 SparkWitch 自有的守卫挂在 NoellesRoles
 * {@code TaotiePlayerComponent.swallowPlayer} 上实现，这是唯一把玩家标记为被吞的代码。SparkFactionAPI 的饕餮影响守卫以
 * {@code require = 0} 指向 {@code lambda$registerPackets$40}，而锁定的 NoellesRoles 1.7.6 中不存在该方法（接收器为
 * {@code lambda$registerPackets$9}），因此影响策略永远不会执行。仅服务端。
 */
public final class FiendSwallowPolicy {
    private FiendSwallowPolicy() {
    }

    public static boolean blocksSwallow(@Nullable ServerPlayerEntity target) {
        return blocks(FiendParticipation.isDormantFiend(target));
    }

    static boolean blocks(boolean dormantFiend) {
        return dormantFiend;
    }
}
