package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import org.jetbrains.annotations.Nullable;

/**
 * Pure decision of 橘雪莉's private outlines: a player inside a live reveal window (a deduced killer, or the killer of
 * a badge holder) is drawn in her identity colour, otherwise a living badge holder in gold, otherwise nothing. The
 * reveal wins because it is the rarer, timed information: a deduced killer who was also handed a badge still flashes.
 * 橘雪莉私有描边的纯判定：处于揭示窗口内的玩家（被推演出的杀手，或杀死徽章持有者的凶手）以她的身份色描边，否则存活的徽章
 * 持有者为金色，否则不描边。揭示优先，因为它是更稀有、有时限的信息：被推演出、又收下徽章的杀手依然会闪现。
 */
public final class SherryHighlightRules {
    /**
     * One above the Seeker / Dominator mark and Savior domain slot (95), so inside a Savior's domain her own colour
     * replaces the domain's generic white instead of depending on listener order; above SparkWitch's 90 faction
     * outlines and below {@code skip()} ({@link GetInstinctHighlight.HighlightResult#PRIORITY_HIGH}, 100), the Black
     * Raven (101) and the Grand Witch suppression (102), so every hiding skip and veto still wins.
     * 比搜寻者 / 支配者标记与救世主领域所在的 95 高一级，因此在救世主领域内由她自己的颜色取代领域的通用白色，而不取决于
     * 监听器注册顺序；高于 SparkWitch 的 90 级阵营描边，低于 {@code skip()}（100）、黑羽鸦（101）与大魔女压制（102），
     * 因此所有隐藏用的 skip 与否决仍然优先。
     */
    public static final int OUTLINE_PRIORITY = 96;

    private SherryHighlightRules() {
    }

    /**
     * The RGB outline colour, or {@code null} for "no opinion". Never a transparent or zero colour: Wathe would draw
     * that as a black outline.
     * RGB 描边颜色；{@code null} 表示不作答。绝不返回透明或零颜色：Wathe 会把它画成黑色描边。
     */
    public static @Nullable Integer outlineColor(boolean revealed, boolean badgeHolder) {
        if (revealed) {
            return SherryRules.COLOR;
        }
        return badgeHolder ? SherryRules.BADGE_GLOW : null;
    }
}
