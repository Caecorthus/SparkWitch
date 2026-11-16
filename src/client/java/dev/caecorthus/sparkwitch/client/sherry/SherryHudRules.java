package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of 橘雪莉's owner-only two-row readout: 推演 (shared ability key) on the upper row and her
 * own 巨力魔法 (Role Skill 2 key) on the lower row. Each row mirrors the order in which the server refuses a press, so
 * the HUD never promises a press the server would turn down for a reason the client can see: 推演 is cooldown → coins →
 * ready, 巨力魔法 is forfeited → live window → cooldown → mana → ready (the corpse and the silence / fear gates stay
 * server-only). Both rows sit in the shared bottom-right corner slot with the Charon's layout
 * ({@link FiendDashHudRules#rowX}, {@link FiendDashHudRules#rowY}: 5 px padding, lifted above the hotbar column at
 * narrow GUI widths), and {@code SaintHudLayoutRules} reserves both rows so the Karma line stacks above. Role-owned
 * presentation: never the witch skill inventory panel ({@code gui.sparkwitch.skills}) and never the action bar.
 * 橘雪莉仅本人可见的两行读数的纯展示规则：上行为推演（共享技能键），下行为她自己的巨力魔法（职业技能 2 键）。每一行都按服务端
 * 拒绝按键的顺序排列，HUD 不会许诺一次因客户端可见的原因而被服务端拒绝的按键：推演为 冷却 → 金币 → 就绪，巨力魔法为
 * 失效 → 生效窗口 → 冷却 → 魔力 → 就绪（尸体以及沉默 / 恐惧等门槛仍只在服务端）。两行都位于共享右下角位置，沿用卡戎的布局
 * （{@link FiendDashHudRules#rowX}、{@link FiendDashHudRules#rowY}：5 像素边距，GUI 较窄时抬升到快捷栏列之上）；
 * {@code SaintHudLayoutRules} 为她预留这两行，业障行因此叠放在其上方。属于职业自有展示：从不进入魔女技能背包面板
 * （{@code gui.sparkwitch.skills}），也从不使用动作栏。
 */
public final class SherryHudRules {
    public static final String DEDUCTION_COOLDOWN_KEY = "hud.sparkwitch.sherry.deduction.cooldown";
    public static final String DEDUCTION_NO_COINS_KEY = "hud.sparkwitch.sherry.deduction.no_coins";
    public static final String DEDUCTION_READY_KEY = "hud.sparkwitch.sherry.deduction.ready";
    public static final String MIGHTY_FORFEITED_KEY = "hud.sparkwitch.sherry.mighty.forfeited";
    public static final String MIGHTY_ACTIVE_KEY = "hud.sparkwitch.sherry.mighty.active";
    public static final String MIGHTY_COOLDOWN_KEY = "hud.sparkwitch.sherry.mighty.cooldown";
    public static final String MIGHTY_NO_MANA_KEY = "hud.sparkwitch.sherry.mighty.no_mana";
    public static final String MIGHTY_READY_KEY = "hud.sparkwitch.sherry.mighty.ready";

    /** Her identity colour, opaque: a usable skill. / 不透明的身份色：技能可用。 */
    public static final int READY_COLOR = 0xFF000000 | SherryRules.COLOR;
    /** The Mighty Force colour, opaque: her window is live. / 不透明的巨力颜色：窗口生效中。 */
    public static final int ACTIVE_COLOR = 0xFF000000 | MightyForceAbility.COLOR;
    /** Same muted grey as the Charon's, the Fiend's and the Blind's waiting lines. / 与卡戎、魔人、盲人等待行相同的灰色。 */
    public static final int WAITING_COLOR = FiendDashHudRules.COOLDOWN_COLOR;
    public static final int ROW_GAP = FiendDashHudRules.ROW_GAP;

    private SherryHudRules() {
    }

    /**
     * One row. {@code keyHint} puts the key name first; {@code number} (negative = none) follows it and is either a
     * countdown in whole seconds ({@code countdown}) or a fixed cost.
     * 一行。{@code keyHint} 表示第一个参数为按键名；其后是 {@code number}（负数表示没有），它要么是整秒倒计时
     * （{@code countdown}），要么是固定的花费。
     */
    public record Line(String key, boolean keyHint, int number, boolean countdown, int color) {
        public boolean hasNumber() {
            return number >= 0;
        }

        /**
         * The number this row is measured with: a countdown is measured at two digits so the hotbar lift does not flip
         * when it drops to one (see {@link FiendDashHudRules#MEASURE_SECONDS}); a cost is measured as is.
         * 测量行宽所用的数字：倒计时按两位数测量，避免少一位数字时抬升来回跳动；花费按原值测量。
         */
        public int measuredNumber() {
            return countdown ? FiendDashHudRules.measuredSeconds(number) : number;
        }
    }

    /**
     * The view gates: her own client on a confirmed SparkWitch server, an ACTIVE round, playing and alive, never an
     * alive spectator (Rift occupant), creative or Taotie-swallowed, with Wathe's train HUD shown, F1 off and no
     * screen open (the chat box and her own 推演 panel cover this corner).
     * 视图门槛：已确认的 SparkWitch 服务器上她自己的客户端、ACTIVE 回合、在局存活，不是存活旁观者（裂隙门内）、创造模式
     * 或被饕餮吞下；Wathe 列车 HUD 显示、未按 F1，且没有打开任何界面（聊天框与她自己的推演面板会盖住这个角落）。
     */
    public static boolean showsHud(boolean activeSherry, boolean hudHidden, boolean trainHudActive,
                                   boolean screenOpen) {
        return activeSherry && !hudHidden && trainHudActive && !screenOpen;
    }

    /**
     * 推演 row: the cooldown in whole seconds rounded up, else the price she cannot pay, else ready with the key and
     * the price.
     * 推演行：向上取整的冷却秒数，否则为付不起的价格，否则为带按键与价格的就绪提示。
     */
    public static Line deductionLine(int cooldownTicks, int balance) {
        if (cooldownTicks > 0) {
            return new Line(DEDUCTION_COOLDOWN_KEY, false, TickTime.ceilSeconds(cooldownTicks), true, WAITING_COLOR);
        }
        if (balance < SherryRules.DEDUCTION_COST) {
            return new Line(DEDUCTION_NO_COINS_KEY, false, SherryRules.DEDUCTION_COST, false, WAITING_COLOR);
        }
        return new Line(DEDUCTION_READY_KEY, true, SherryRules.DEDUCTION_COST, false, READY_COLOR);
    }

    /**
     * 巨力魔法 row. A forfeit ends the window on the server, so it is shown first; a live window hides the cooldown
     * that only starts when it ends.
     * 巨力魔法行。失效时服务端会同时结束窗口，因此最先显示；窗口生效期间不显示冷却（冷却在窗口结束后才开始）。
     */
    public static Line mightyLine(boolean forfeited, int windowTicks, int cooldownTicks, int mana) {
        if (forfeited) {
            return new Line(MIGHTY_FORFEITED_KEY, false, -1, false, WAITING_COLOR);
        }
        if (windowTicks > 0) {
            return new Line(MIGHTY_ACTIVE_KEY, false, TickTime.ceilSeconds(windowTicks), true, ACTIVE_COLOR);
        }
        if (cooldownTicks > 0) {
            return new Line(MIGHTY_COOLDOWN_KEY, false, TickTime.ceilSeconds(cooldownTicks), true, WAITING_COLOR);
        }
        if (mana < MightyForceAbility.MANA_COST) {
            return new Line(MIGHTY_NO_MANA_KEY, false, MightyForceAbility.MANA_COST, false, WAITING_COLOR);
        }
        return new Line(MIGHTY_READY_KEY, true, MightyForceAbility.MANA_COST, false, READY_COLOR);
    }

    /** Left x of a right-aligned row. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the lower (巨力魔法) row. Both rows lift together: {@code measuredWidth} is the wider of the two rows
     * measured with {@link Line#measuredNumber}. She has no witch skill line and the Karma line is reserved above, so
     * the shared corner row is never occupied by another line.
     * 下行（巨力魔法）的顶部 y。两行一同抬升：{@code measuredWidth} 是两行按 {@link Line#measuredNumber} 测量后的较宽者。
     * 她没有魔女技能行，业障行又预留在上方，因此共享角落行不会被其他行占用。
     */
    public static int lowerRowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, false);
    }

    /** Top y of the upper (推演) row, one row and the gap above the lower one. / 上行（推演）的顶部 y：位于下行上方一行加间距。 */
    public static int upperRowY(int lowerRowY, int fontHeight) {
        return lowerRowY - fontHeight - ROW_GAP;
    }
}
