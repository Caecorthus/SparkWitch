package dev.caecorthus.sparkwitch.headset;

/**
 * Pure shift-click (QUICK_MOVE) routing of the player screen for the headset slot. Into it: an item the slot accepts,
 * shift-clicked from the main inventory, hotbar or offhand (handler 9..45) while the headset slot is empty, goes there
 * first. Out of it: vanilla's last branch already moves handler 46 into 9..44, so nothing is added. Every other click
 * keeps vanilla's routing.
 * 玩家界面中耳机栏位的纯 Shift 点击（QUICK_MOVE）路由。移入：耳机栏位为空时，从主背包、快捷栏或副手（界面 9..45）Shift 点击
 * 的可接受物品优先进入耳机栏位。移出：原版最后一个分支本就把界面 46 移到 9..44，因此不另加处理。其他点击保持原版路由。
 */
public final class HeadsetQuickMove {
    /** {@code PlayerScreenHandler.INVENTORY_START}. */
    public static final int FIRST_SOURCE = 9;
    /** {@code PlayerScreenHandler.OFFHAND_ID}, inclusive. / 副手下标（含）。 */
    public static final int LAST_SOURCE = 45;

    private HeadsetQuickMove() {
    }

    /** Whether a shift-click of handler slot {@code slotIndex} goes into the headset slot. / 是否移入耳机栏位。 */
    public static boolean routesIntoHeadset(int slotIndex, boolean accepted, boolean headsetEmpty) {
        return accepted && headsetEmpty && slotIndex >= FIRST_SOURCE && slotIndex <= LAST_SOURCE;
    }
}
