package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * D4: the Taotie can never swallow a dormant Fiend; a moment Fiend is swallowable normally (its shield may block one).
 * Enforced by SparkWitch's own guard on NoellesRoles {@code TaotiePlayerComponent.swallowPlayer}, the only code that
 * marks a player swallowed. SparkFactionAPI 0.1.5.11's Taotie affect guard targets the real receiver
 * {@code lambda$registerPackets$9} with {@code require = 1}, but it only runs the generic {@code canAffectPlayer}
 * policies, so the SparkWitch guard remains the only dormant-Fiend-specific block. Server only.
 * D4：饕餮永远不能吞下休眠魔人；时刻中的魔人照常可被吞（其护盾可抵挡一次）。由 SparkWitch 自有的守卫挂在 NoellesRoles
 * {@code TaotiePlayerComponent.swallowPlayer} 上实现，这是唯一把玩家标记为被吞的代码。SparkFactionAPI 0.1.5.11 的饕餮
 * 影响守卫以 {@code require = 1} 指向真实接收器 {@code lambda$registerPackets$9}，但只执行通用的
 * {@code canAffectPlayer} 策略，因此 SparkWitch 的守卫仍是唯一针对休眠魔人的拦截。仅服务端。
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
