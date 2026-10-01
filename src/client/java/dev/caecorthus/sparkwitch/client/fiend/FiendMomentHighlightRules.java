package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendRules;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Fiend Moment outline decision (owner decision D8). While a moment is active, the moment Fiend sees every other
 * playing, living player, and every other viewer (spectators included) sees the moment Fiend; both pairs use the
 * Fiend's own color, so no role identity is revealed. {@code null} means "no Fiend opinion": the caller falls through
 * to the other outline rules unchanged.
 * 纯魔人时刻描边判定（所有者决定 D8）。时刻进行中，时刻魔人可看到其他所有参与且存活的玩家，其他所有观察者（含旁观者）
 * 都能看到时刻魔人；两种配对都使用魔人自己的颜色，因此不暴露任何身份。返回 {@code null} 表示魔人规则不表态，
 * 调用方原样落回其他描边规则。
 */
public final class FiendMomentHighlightRules {
    private FiendMomentHighlightRules() {
    }

    /**
     * @param momentActive          the client world's synced Fiend Moment is active / 客户端世界同步的魔人时刻进行中
     * @param viewerIsMomentFiend   the local viewer is the moment Fiend / 本地观察者是时刻魔人
     * @param targetIsMomentFiend   the target player is the moment Fiend / 目标玩家是时刻魔人
     * @param targetIsViewer        the target is the local viewer itself / 目标就是本地观察者自己
     * @param targetPlayingAndAlive the target plays this round, is alive per Wathe, and is not spectating or creative /
     *                              目标参与本局、按 Wathe 判定存活，且不是旁观或创造模式
     */
    public static @Nullable Integer highlight(
            boolean momentActive,
            boolean viewerIsMomentFiend,
            boolean targetIsMomentFiend,
            boolean targetIsViewer,
            boolean targetPlayingAndAlive
    ) {
        if (!momentActive || targetIsViewer) {
            return null;
        }
        if (viewerIsMomentFiend) {
            return targetPlayingAndAlive ? FiendRules.COLOR : null;
        }
        return targetIsMomentFiend ? FiendRules.COLOR : null;
    }
}
