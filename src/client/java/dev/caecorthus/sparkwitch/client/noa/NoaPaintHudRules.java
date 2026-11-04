package dev.caecorthus.sparkwitch.client.noa;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of Noa's owner-only painting line: "ready: press G" or "cooling down Ns", from the remaining
 * painting cooldown synced only to that Noa. Text only and only the local player's own cooldown, so it never leaks
 * another player, and it never carries her sanity (owner rule: Noa's exact sanity value is never shown). It takes the
 * Fiend Dash line's bottom-right layout as is (5 px padding, one row up over an occupied shared skill line, lifted
 * above the hotbar column at narrow GUI widths); Noa is never a Fiend or an Athlete, so those lines never meet hers.
 * Role-owned presentation; never the witch skill inventory panel and never the action bar.
 * 诺亚仅本人可见的作画行的纯展示规则：依据只同步给该诺亚的作画剩余冷却显示“就绪：按 G”或“冷却 N 秒”。只有文字，
 * 且只读本地玩家自己的冷却，因此绝不会泄露其他玩家，也从不带出她的理智（所有者规则：从不显示诺亚的具体理智值）。
 * 直接沿用魔人疾驰行的右下角布局（5 像素边距、共享技能行被占用时上移一行、GUI 较窄时抬升到快捷栏列之上）；诺亚不可能
 * 是魔人或运动员，这些行不会与她的行同时出现。属于职业自有展示，从不使用魔女技能背包面板，也从不使用动作栏。
 */
public final class NoaPaintHudRules {
    public static final String READY_KEY = "hud.sparkwitch.noa.paint.ready";
    public static final String COOLDOWN_KEY = "hud.sparkwitch.noa.paint.cooldown";
    /** Opaque Noa role colour. / 不透明的诺亚职业色。 */
    public static final int READY_COLOR = 0xFF000000 | NoaRules.COLOR;
    /** Same muted grey as the Blind's, the Fiend's and the Athlete's cooldown lines. / 与盲人、魔人、运动员冷却行相同的灰色。 */
    public static final int COOLDOWN_COLOR = FiendDashHudRules.COOLDOWN_COLOR;
    /** Shared bottom-right layout (Fiend Dash / Blind kit precedent). / 共享的右下角布局（参照魔人疾驰与盲人道具）。 */
    public static final int RIGHT_PADDING = FiendDashHudRules.RIGHT_PADDING;
    public static final int BOTTOM_PADDING = FiendDashHudRules.BOTTOM_PADDING;
    public static final int ROW_GAP = FiendDashHudRules.ROW_GAP;
    public static final int LIFTED_BOTTOM_OFFSET = FiendDashHudRules.LIFTED_BOTTOM_OFFSET;
    public static final int MEASURE_SECONDS = FiendDashHudRules.MEASURE_SECONDS;

    private NoaPaintHudRules() {
    }

    /**
     * The line's translation key, whether it takes the ability-key hint, its seconds argument (negative = none) and
     * colour.
     * 该行的翻译键、是否带技能按键提示、秒数参数（负数表示无）与颜色。
     */
    public record Line(String key, boolean keyHint, int seconds, int color) {
        public boolean hasSeconds() {
            return seconds >= 0;
        }
    }

    /**
     * Drawn only on Noa's own client while she is playing and alive (so never as a spectator), in an ACTIVE round once
     * Wathe's round-start fade is over, with Wathe's train HUD shown and F1 off, and never while a Taotie has swallowed
     * her (no "ready: press G" while she cannot paint).
     * 仅在诺亚自己的客户端上、其参与且存活（因此旁观时从不绘制）、回合为 ACTIVE 且 Wathe 开局淡入结束、Wathe 列车 HUD
     * 显示且未按 F1 时绘制；被饕餮吞下时从不绘制（无法作画时不显示“就绪：按 G”）。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean noa, boolean playingAndAlive, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && noa && playingAndAlive && !swallowed;
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return TickTime.ceilSeconds(ticks);
    }

    /**
     * Ready (with the key hint) when no cooldown remains, else the cooldown in whole seconds rounded up.
     * 无剩余冷却时为就绪（带按键提示），否则为向上取整的冷却秒数。
     */
    public static Line line(int paintCooldownTicks) {
        if (paintCooldownTicks > 0) {
            return new Line(COOLDOWN_KEY, false, seconds(paintCooldownTicks), COOLDOWN_COLOR);
        }
        return new Line(READY_KEY, true, -1, READY_COLOR);
    }

    /** Left x of a right-aligned line. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the line, with the shared corner layout (see {@link FiendDashHudRules#rowY}).
     * 该行的顶部 y，使用共享的角落布局（见 {@link FiendDashHudRules#rowY}）。
     */
    public static int rowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight,
                           boolean sharedLineOccupied) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, sharedLineOccupied);
    }

    /** The seconds the line is measured with (see {@link #MEASURE_SECONDS}). / 测量行宽所用的秒数。 */
    public static int measuredSeconds(int seconds) {
        return FiendDashHudRules.measuredSeconds(seconds);
    }
}
