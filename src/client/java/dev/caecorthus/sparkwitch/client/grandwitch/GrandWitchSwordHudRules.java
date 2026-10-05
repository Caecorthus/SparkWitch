package dev.caecorthus.sparkwitch.client.grandwitch;

import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Pure layout and state rules for the Ceremonial Sword cooldown HUD (owner pick A2 + B2, 2026-10-05): a sword glyph
 * left of the crosshair for the kill cooldown, a dash chevron right of it, and a badge above whichever hotbar slot (or
 * the off-hand slot) holds the sword, with the kill seconds and five dash pips. Every gauge fills as its cooldown
 * recovers and is full when ready.
 * 仪礼剑冷却 HUD 的纯布局与状态规则（所有者 2026-10-05 选定 A2 + B2）：准星左侧剑形表示击杀冷却、右侧双箭头表示冲刺冷却，
 * 剑所在的任意物品栏格（或副手格）上方显示击杀秒数与 5 个冲刺点。所有刻度随冷却恢复而填充，就绪时为满。
 */
public final class GrandWitchSwordHudRules {
    /** Kill glyph, filled from hilt to tip. / 击杀剑形，从剑柄向剑尖填充。 */
    public static final String[] KILL_GLYPH = {
            "..#..", ".###.", ".###.", ".###.", ".###.", ".###.", ".###.", "#####", "..#..", "..#..", ".###."
    };
    /** Dash chevrons, filled left to right. / 冲刺双箭头，从左向右填充。 */
    public static final String[] DASH_GLYPH = {
            "#..#...", "##.##..", ".##.##.", "..##.##", ".##.##.", "##.##..", "#..#..."
    };
    /** The slot badge's smaller kill glyph, one text line tall. / 剑槽标记中一行字高的小剑形。 */
    public static final String[] BADGE_GLYPH = {
            "..#..", ".###.", ".###.", ".###.", ".###.", "#####", "..#..", ".###."
    };

    /** Glyph offsets from the screen centre, mirrored around Wathe's 3x3 crosshair. / 相对屏幕中心的偏移，围绕 Wathe 3x3 准星对称。 */
    public static final int KILL_GLYPH_X = -12;
    public static final int KILL_GLYPH_Y = -5;
    public static final int DASH_GLYPH_X = 8;
    public static final int DASH_GLYPH_Y = -3;

    /** Attack recharge bar under the crosshair, on Wathe's knife-glyph rows. / 准星下方攻击蓄力条，位于 Wathe 刀图标所在行。 */
    public static final int ATTACK_BAR_X = -5;
    public static final int ATTACK_BAR_Y = 6;
    public static final int ATTACK_BAR_WIDTH = 10;
    public static final int ATTACK_BAR_HEIGHT = 2;

    public static final int HOTBAR_SLOTS = 9;
    public static final int DASH_PIPS = 5;
    public static final int PIP_SIZE = 2;
    public static final int PIP_STEP = 3;
    public static final int BADGE_GLYPH_GAP = 2;
    private static final float CELL_EPSILON = 1.0E-4F;
    /** Ready flash length in ticks (0.4 s). / 就绪闪白时长（0.4 秒）。 */
    public static final int FLASH_TICKS = 8;

    public static final int KILL_READY = 0xFFF48686;
    public static final int KILL_COOLING = 0xFF8E6565;
    public static final int DASH_READY = 0xFFF2DFF7;
    public static final int DASH_COOLING = 0xFF83728F;
    /** Wathe's knife-glyph background grey. / Wathe 刀图标的灰色底。 */
    public static final int GLYPH_TRACK = 0xFF3B3C3C;
    public static final int GLYPH_SHADOW = 0xD9140608;
    public static final int PIP_PLATE = 0xBF100804;
    public static final int PIP_EMPTY = 0xFF2E1408;
    public static final int ATTACK_TRACK = 0xAA222222;
    public static final int ATTACK_FILL = 0xFFF2DFF7;
    public static final int WHITE = 0xFFFFFFFF;

    private GrandWitchSwordHudRules() {
    }

    /** Recovered share of a cooldown, 1 when ready; an unknown or extended total never overflows. / 冷却已恢复比例，就绪为 1。 */
    public static float progress(float remainingTicks, float totalTicks) {
        if (remainingTicks <= 0) {
            return 1.0F;
        }
        if (totalTicks <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, 1.0F - remainingTicks / totalTicks));
    }

    /**
     * Filled rows or columns out of {@code cells}; only a ready gauge is full. The epsilon keeps exact boundaries
     * (3 s of a 5 s dash) from losing a cell to float rounding.
     * 已填充的行或列数；仅就绪时全满。epsilon 防止整格边界（5 秒冲刺剩 3 秒）因浮点舍入少算一格。
     */
    public static int filledCells(int cells, float progress) {
        return progress >= 1.0F ? cells : Math.max(0, Math.min(cells - 1, (int) (progress * cells + CELL_EPSILON)));
    }

    /**
     * Whether a glyph pixel is coloured: rows fill bottom to top when {@code vertical}, columns left to right otherwise.
     * 像素是否着色：{@code vertical} 时由下往上逐行填充，否则由左往右逐列填充。
     */
    public static boolean isFilled(String[] glyph, int row, int column, float progress, boolean vertical) {
        if (vertical) {
            return glyph.length - 1 - row < filledCells(glyph.length, progress);
        }
        return column < filledCells(glyph[0].length(), progress);
    }

    /** Wathe's own cooldown text: whole seconds from 10 s, one decimal below. / 与 Wathe 物品冷却同格式。 */
    public static String timer(float remainingTicks) {
        float seconds = Math.max(0.0F, remainingTicks) / 20.0F;
        if (seconds >= 60.0F) {
            int minutes = (int) (seconds / 60.0F);
            int rest = (int) Math.ceil(seconds % 60.0F);
            if (rest == 60) {
                minutes++;
                rest = 0;
            }
            return String.format(Locale.ROOT, "%d:%02d", minutes, rest);
        }
        if (seconds >= 10.0F) {
            return String.format(Locale.ROOT, "%ds", (int) Math.ceil(seconds));
        }
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    /** Centre of a hotbar slot, the x Wathe centres its cooldown number on. / 物品栏格中心，与 Wathe 冷却数字居中位置相同。 */
    public static int slotCentreX(int scaledWidth, int slot) {
        return scaledWidth / 2 - 80 + slot * 20;
    }

    /**
     * Centre of vanilla's off-hand slot beside the hotbar ({@code w/2 - 91 - 26} or {@code w/2 + 91 + 10}, plus 8).
     * 原版副手格中心（位于物品栏左侧或右侧）。
     */
    public static int offhandCentreX(int scaledWidth, boolean leftOfHotbar) {
        return leftOfHotbar ? scaledWidth / 2 - 109 : scaledWidth / 2 + 109;
    }

    /**
     * Hotbar slot the badge marks: the selected slot when it holds the sword, else the first hotbar slot that does;
     * -1 when the sword is not on the hotbar (the off-hand is checked by the caller).
     * 剑槽标记所在的物品栏格：选中格持剑时为选中格，否则为第一个持剑格；剑不在物品栏时为 -1（副手由调用方判断）。
     */
    public static int swordHotbarSlot(int selectedSlot, IntPredicate holdsSword) {
        if (selectedSlot >= 0 && selectedSlot < HOTBAR_SLOTS && holdsSword.test(selectedSlot)) {
            return selectedSlot;
        }
        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            if (holdsSword.test(slot)) {
                return slot;
            }
        }
        return -1;
    }

    /** Badge text top: Wathe's cooldown number line above the hotbar. / 标记文字顶部，即 Wathe 冷却数字所在行。 */
    public static int badgeTextY(int scaledHeight) {
        return scaledHeight - 35;
    }

    /** Dash pips row: between the badge text shadow and the hotbar selection frame. / 冲刺点所在行。 */
    public static int pipsY(int scaledHeight) {
        return scaledHeight - 26;
    }

    public static int pipsX(int slotCentreX) {
        return slotCentreX - (DASH_PIPS * PIP_STEP - 1) / 2;
    }

    /** Lit dash pips, one per fifth of the cooldown. / 已点亮的冲刺点数，每五分之一冷却一个。 */
    public static int litPips(float progress) {
        return filledCells(DASH_PIPS, progress);
    }

    /** Charge of the pip currently recovering, 0..1. / 正在恢复的那一点的充能比例。 */
    public static float recoveringPipCharge(float progress) {
        if (progress >= 1.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, Math.max(0.0F, progress) * DASH_PIPS - litPips(progress)));
    }

    /** Ready flash strength {@code ageTicks} after a cooldown ended. / 冷却结束后经过 {@code ageTicks} 的闪白强度。 */
    public static float flash(float ageTicks) {
        if (ageTicks < 0.0F || ageTicks >= FLASH_TICKS) {
            return 0.0F;
        }
        return 1.0F - ageTicks / FLASH_TICKS;
    }

    public static int lerpArgb(int from, int to, float t) {
        float k = Math.max(0.0F, Math.min(1.0F, t));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            result |= Math.round(a + (b - a) * k) << shift;
        }
        return result;
    }

    /**
     * Remembers when one cooldown reached zero, so its gauge can flash. A gap in observations (sword put away, world
     * change) forgets the old value instead of flashing late.
     * 记录某个冷却归零的时刻用于闪白；观察中断（收起剑、切换世界）时丢弃旧值，不会迟到闪白。
     */
    public static final class ReadyFlash {
        private static final long STALE_TICKS = 2;
        private int lastRemaining = -1;
        private long lastSeen = Long.MIN_VALUE;
        private long readyAt = Long.MIN_VALUE;

        public void observe(int remainingTicks, long worldTime) {
            if (lastSeen == Long.MIN_VALUE || worldTime < lastSeen || worldTime - lastSeen > STALE_TICKS) {
                lastRemaining = -1;
                readyAt = Long.MIN_VALUE;
            }
            if (lastRemaining > 0 && remainingTicks <= 0) {
                readyAt = worldTime;
            }
            lastRemaining = remainingTicks;
            lastSeen = worldTime;
        }

        public float strength(float worldTime) {
            return readyAt == Long.MIN_VALUE ? 0.0F : flash(worldTime - readyAt);
        }
    }
}
