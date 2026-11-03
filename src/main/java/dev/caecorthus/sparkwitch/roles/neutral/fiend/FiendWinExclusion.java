package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenSettlement;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Owner decision D1: a dormant Fiend is invisible to every "last one standing" count, so a win or moment that waits
 * for everyone else to die never waits for an unkillable Fiend. Plan v2 §3.3 (2026-10-21): the Black Raven is never
 * alive for settlement either, so the same wrapped counts also skip a living raw Black Raven
 * ({@link BlackRavenSettlement}); the class name is kept. SparkWitch's own counts call
 * {@link FiendParticipation#isDormantFiend} and {@link BlackRavenSettlement#isExcluded} directly; the NoellesRoles
 * counts (Jester moment, Corrupt Cop, Taotie swallow-all, and the shared moment-trigger counter) reach this through
 * {@code mixin/fiend/} wrappers around their per-player alive check. A moment Fiend and every other player keep the
 * host's own answer, so each wrapped count is unchanged when no dormant Fiend or Black Raven is alive.
 * 所有者决定 D1：休眠魔人对所有“最后存活者”计数都不可见，因此等待其他人全部死亡的胜利或时刻不会等待无法击杀的魔人。
 * 计划 v2 §3.3（2026-10-21）：黑羽鸦在结算中同样永不算存活，因此这些被包装的计数也跳过存活的原始黑羽鸦
 * （{@link BlackRavenSettlement}）；类名保持不变。SparkWitch 自身计数直接调用 {@link FiendParticipation#isDormantFiend}
 * 与 {@link BlackRavenSettlement#isExcluded}；NoellesRoles 的计数（小丑时刻、黑警、饕餮吞尽、以及时刻触发共用计数器）
 * 通过 {@code mixin/fiend/} 对其逐人存活判断的包装接入此处。时刻中的魔人与其他所有玩家保持宿主原本的结果，因此没有
 * 存活的休眠魔人或黑羽鸦时，每个被包装的计数都不变。
 */
public final class FiendWinExclusion {
    private FiendWinExclusion() {
    }

    /**
     * Keeps the host's alive answer, except that a dormant Fiend and the Black Raven never count. Dead players skip
     * both lookups.
     * 保留宿主的存活判断结果，唯独休眠魔人与黑羽鸦永不计入。已死亡玩家不会进行这两项判断。
     */
    public static boolean countsAsAlive(boolean hostAlive, @Nullable PlayerEntity player) {
        return hostAlive && !FiendParticipation.isDormantFiend(player) && !BlackRavenSettlement.isExcluded(player);
    }
}
