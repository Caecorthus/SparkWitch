package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Owner decision D1: a dormant Fiend is invisible to every "last one standing" count, so a win or moment that waits
 * for everyone else to die never waits for an unkillable Fiend. SparkWitch's own counts call
 * {@link FiendParticipation#isDormantFiend} directly; the NoellesRoles counts (Jester moment, Corrupt Cop,
 * Taotie swallow-all, and the shared moment-trigger counter) reach this through {@code mixin/fiend/} wrappers around
 * their per-player alive check. A moment Fiend and every non-Fiend player keep the host's own answer, so each wrapped
 * count is unchanged when no dormant Fiend is alive.
 * 所有者决定 D1：休眠魔人对所有“最后存活者”计数都不可见，因此等待其他人全部死亡的胜利或时刻不会等待无法击杀的魔人。
 * SparkWitch 自身计数直接调用 {@link FiendParticipation#isDormantFiend}；NoellesRoles 的计数（小丑时刻、黑警、饕餮吞尽、
 * 以及时刻触发共用计数器）通过 {@code mixin/fiend/} 对其逐人存活判断的包装接入此处。时刻中的魔人与所有非魔人玩家保持
 * 宿主原本的结果，因此没有存活的休眠魔人时，每个被包装的计数都不变。
 */
public final class FiendWinExclusion {
    private FiendWinExclusion() {
    }

    /**
     * Keeps the host's alive answer, except that a dormant Fiend never counts. Dead players skip the Fiend lookup.
     * 保留宿主的存活判断结果，唯独休眠魔人永不计入。已死亡玩家不会进行魔人判断。
     */
    public static boolean countsAsAlive(boolean hostAlive, @Nullable PlayerEntity player) {
        return hostAlive && !FiendParticipation.isDormantFiend(player);
    }
}
