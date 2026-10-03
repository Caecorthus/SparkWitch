package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * D4: the Taotie can never swallow a dormant Fiend; a moment Fiend is swallowable normally (its shield may block one).
 * Enforced by SparkWitch's own guard on NoellesRoles {@code TaotiePlayerComponent.swallowPlayer}, the only code that
 * marks a player swallowed. SparkFactionAPI 0.1.5.12's Taotie affect guard matches the pinned NoellesRoles 1.7.6
 * receiver {@code lambda$registerPackets$9} ({@code require = 1}) but only runs affect policies; this refusal is not
 * an affect policy, so it stays here. Server only.
 * D4：饕餮永远不能吞下休眠魔人；时刻中的魔人照常可被吞（其护盾可抵挡一次）。由 SparkWitch 自有的守卫挂在 NoellesRoles
 * {@code TaotiePlayerComponent.swallowPlayer} 上实现，这是唯一把玩家标记为被吞的代码。SparkFactionAPI 0.1.5.12 的饕餮
 * 影响守卫以 {@code require = 1} 匹配锁定 NoellesRoles 1.7.6 的接收器 {@code lambda$registerPackets$9}，但只执行影响
 * 策略；本拒绝不是影响策略，因此仍在此处实现。仅服务端。
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
