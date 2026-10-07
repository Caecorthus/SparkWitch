package dev.caecorthus.sparkwitch.client.inventory;

/**
 * Spark 旧版 Wathe 没有导出 NoellesRoles 使用的 InventoryButtonLayout，
 * 这里保留同一套公开布局常量，供魔术师及后续扩展职业共用。
 */
public final class SparkWitchInventoryButtonLayout {
    public static final int PLAYERS_PER_PAGE = 10;
    public static final int SLOT_APART = 36;
    public static final int SLOT_X_OFFSET = 9;

    private SparkWitchInventoryButtonLayout() {
    }

    /** 玩家头像行位于 Wathe 商店商品区域下方。 */
    public static int getPlayerRowY(int screenHeight) {
        return (screenHeight - 32) / 2 + 80;
    }

    /** 按槽位间距居中计算玩家按钮行起点。 */
    public static int getCenteredPlayerStartX(int screenWidth, int visiblePlayerCount) {
        return screenWidth / 2 - visiblePlayerCount * SLOT_APART / 2 + SLOT_X_OFFSET;
    }

    /**
     * Centers the complete player/control group. The page buttons occupy the same
     * 36-pixel slot as an avatar, matching SparkStrength's inventory layout.
     */
    public static int getCenteredGroupStartX(
            int screenWidth,
            int visiblePlayerCount,
            boolean showPrevious,
            boolean showNext
    ) {
        int controlCount = (showPrevious ? 1 : 0) + (showNext ? 1 : 0);
        return screenWidth / 2 - (visiblePlayerCount + controlCount) * SLOT_APART / 2 + SLOT_X_OFFSET;
    }

    public static int getTotalPageCount(int totalPlayers) {
        return Math.max(1, (Math.max(0, totalPlayers) + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
    }
}
