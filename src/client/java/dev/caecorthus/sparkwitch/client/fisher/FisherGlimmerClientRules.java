package dev.caecorthus.sparkwitch.client.fisher;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;

import java.util.Locale;

/**
 * Pure client presentation rules for the Glimmerfish window. Owner Q3: the glimmering player's outlines use one colour
 * (the Angler theme colour) and never reveal a faction; a glimmering player is invisible, so other non-spectator viewers
 * lose both their instinct outline of them and the sight of anything in their hands.
 * 灵光鱼窗口的纯客户端展示规则。所有者 Q3：灵光中的玩家看到的描边统一使用钓鱼佬主题色，从不暴露阵营；
 * 灵光中的玩家处于隐身，其他非旁观者既看不到对他的本能描边，也看不到他手里的任何物品。
 */
public final class FisherGlimmerClientRules {
    public static final int OUTLINE_COLOR = FisherRules.COLOR;
    /**
     * Above ordinary role highlights, below Wathe/SparkWitch suppressors such as fear and obscure
     * ({@code PRIORITY_HIGH + 2}), so a suppressed viewer still sees nothing.
     * 高于普通职业高亮，低于恐惧、障眼等压制（{@code PRIORITY_HIGH + 2}），被压制的观察者依旧什么都看不到。
     */
    public static final int OUTLINE_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH - 10;
    /** Beats every role highlight of an invisible glimmering target (SparkStrength's serum uses 300 too). / 覆盖所有职业高亮。 */
    public static final int HIDE_PRIORITY = 300;
    public static final String HUD_KEY = "hud.sparkwitch.fisher.glimmer";

    private FisherGlimmerClientRules() {
    }

    /** The local glimmering player outlines every other living, non-hidden player. / 本地灵光玩家描边其他所有存活且未被隐藏的玩家。 */
    public static boolean outlines(boolean viewerGlimmering, boolean viewerSpectating, boolean targetIsViewer,
                                   boolean targetAliveAndSurvival, boolean targetGlimmering, boolean targetHidden) {
        return viewerGlimmering && !viewerSpectating && !targetIsViewer && targetAliveAndSurvival
                && !targetGlimmering && !targetHidden;
    }

    /** Spectators keep seeing everything; everyone else loses a glimmering target. / 旁观者照常可见，其他人看不到灵光目标。 */
    public static boolean hidesTarget(boolean targetGlimmering, boolean viewerSpectating, boolean targetIsViewer) {
        return targetGlimmering && !viewerSpectating && !targetIsViewer;
    }

    /** Held items vanish with the body for every non-spectator viewer. / 对所有非旁观者，手持物随身体一起消失。 */
    public static boolean hidesHeldItems(boolean holderGlimmering, boolean viewerSpectating) {
        return holderGlimmering && !viewerSpectating;
    }

    /** Remaining seconds with one decimal, never negative, e.g. {@code 7.8}. / 保留一位小数的剩余秒数。 */
    public static String seconds(int remainingTicks) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0, remainingTicks) / 20.0);
    }
}
