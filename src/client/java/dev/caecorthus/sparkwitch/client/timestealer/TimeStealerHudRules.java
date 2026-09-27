package dev.caecorthus.sparkwitch.client.timestealer;

import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;

/**
 * Pure rules for the Time Stealer's owner-only HUD (stamp count). Role-owned presentation; never the Witch skill
 * inventory panel. The row is right-aligned in the bottom-right corner with the same 5 px padding as the shared
 * bottom-right skill line. The Time Stealer has no key skill, so it normally takes that bottom row itself; if the
 * shared line is ever occupied, the row moves up one line with a 2 px gap (the Black Raven placement), never on top.
 * 窃时者仅拥有者可见 HUD（邮票数）的纯规则。职业自有展示；从不使用魔女技能背包面板。该行在右下角右对齐，
 * 边距与共享的右下角技能行相同（5 像素）。窃时者没有按键技能，因此通常自己占用最底行；若共享技能行被占用，
 * 则上移一行并留 2 像素间距（黑羽鸦的摆放方式），绝不重叠。
 */
public final class TimeStealerHudRules {
    /** One-argument lang key: {@code "⌛ %s"}. / 单参数语言键：{@code "⌛ %s"}。 */
    public static final String STAMPS_KEY = "hud.sparkwitch.time_stealer.stamps";
    /** Opaque role color for the row. / 该行使用的不透明职业颜色。 */
    public static final int TEXT_COLOR = 0xFF000000 | TimeStealerRules.COLOR;
    public static final int RIGHT_PADDING = 5;
    public static final int BOTTOM_PADDING = 5;
    public static final int ROW_GAP = 2;

    private TimeStealerHudRules() {
    }

    /**
     * Whether the local owner's stamp row is drawn this frame. Every input is local-client state: the F1 flag, the
     * synced game status (ACTIVE only; STOPPING still counts as playing but is covered by Wathe's round-end fade,
     * which this TAIL-drawn row would otherwise sit on top of), the local player's playing/alive state, Wathe's train
     * HUD, the exact local role and the balance computed from the owner's own synced inventory. Nothing about any
     * other player is read, so the row can never leak.
     * 本帧是否绘制本地拥有者的邮票行。所有输入都是本地客户端状态：F1 标记、已同步的对局状态（仅 ACTIVE；STOPPING
     * 仍算作参与中，但已被 Wathe 的回合结束黑幕覆盖，而该行在 TAIL 绘制，否则会浮在黑幕之上）、本地玩家的参与/存活
     * 状态、Wathe 列车 HUD、本地精确职业，以及由拥有者自身已同步背包算出的余额。不读取任何其他玩家的信息，因此该行
     * 绝不会泄露信息。
     */
    public static boolean showsStampRow(
            boolean hudHidden,
            boolean gameActive,
            boolean playingAndAlive,
            boolean trainHudActive,
            boolean exactTimeStealer,
            int balance
    ) {
        return !hudHidden && gameActive && playingAndAlive && trainHudActive && exactTimeStealer && balance > 0;
    }

    /** Left x of the right-aligned row. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return scaledWidth - RIGHT_PADDING - Math.max(0, textWidth);
    }

    /**
     * Top y of the row: the bottom row when the shared bottom-right line is free, otherwise one line above it.
     * 该行的顶部 y：共享右下角技能行空闲时为最底行，否则位于其上方一行。
     */
    public static int rowY(int scaledHeight, int fontHeight, boolean sharedLineOccupied) {
        int bottomRow = scaledHeight - BOTTOM_PADDING - fontHeight;
        return sharedLineOccupied ? bottomRow - fontHeight - ROW_GAP : bottomRow;
    }
}
