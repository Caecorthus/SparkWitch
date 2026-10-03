package dev.caecorthus.sparkwitch.client.armor;

import org.jetbrains.annotations.Nullable;

/**
 * Pixel geometry and shift-click planning for the four armor slots on Wathe's {@code LimitedInventoryScreen} (D10).
 * A 2x2 block (head, chest / legs, feet) hangs left of the inventory block, centred on it, so it stays clear of the
 * shop row above, the role head rows below, the right-hand info card and the logo. The inventory block is Wathe's
 * 176x32 hotbar strip, plus, with SparkFactionAPI 0.1.5.13+, the 22 px second row (main slots 27-35) above it. Slots
 * are the real {@code PlayerScreenHandler} armor slots 5..8; only their on-screen position is ours.
 * Wathe {@code LimitedInventoryScreen} 四个护甲槽（D10）的像素布局与 Shift 点击规划。2x2 方块（头、胸 / 腿、脚）挂在
 * 物品栏块左侧并与其垂直居中，避开上方的商店行、下方的职业头像行、右侧信息卡和标志。物品栏块是 Wathe 176x32 热栏，
 * 在 SparkFactionAPI 0.1.5.13+ 下再加上其上方 22 像素高的第二行（主背包 27-35）。槽位就是原版
 * {@code PlayerScreenHandler} 的护甲槽 5..8，这里只决定它们在屏幕上的位置。
 */
public final class LimitedInventoryArmorLayout {
    /**
     * {@code PlayerScreenHandler.EQUIPMENT_START}: head, chest, legs, feet follow in this order.
     * {@code PlayerScreenHandler.EQUIPMENT_START}：其后依次为头、胸、腿、脚。
     */
    public static final int FIRST_ARMOR_SLOT_ID = 5;
    public static final int ARMOR_SLOT_COUNT = 4;
    /** {@code PlayerScreenHandler.HOTBAR_START} / {@code HOTBAR_END} (exclusive). / 快捷栏起止槽位（终点不含）。 */
    public static final int HOTBAR_START = 36;
    public static final int HOTBAR_END = 45;
    /**
     * Handler slots of the second row SparkFactionAPI 0.1.5.13+ shows (inventory 27-35, end exclusive).
     * SparkFactionAPI 0.1.5.13+ 显示的第二行对应的界面槽位（背包 27-35，终点不含）。
     */
    public static final int SECOND_ROW_START = 27;
    public static final int SECOND_ROW_END = 36;
    /**
     * Height the second row adds above the strip (SparkFactionAPI {@code LimitedInventorySecondRow.ROW_PITCH}).
     * 第二行在热栏上方增加的高度（SparkFactionAPI {@code LimitedInventorySecondRow.ROW_PITCH}）。
     */
    public static final int SECOND_ROW_PITCH = 22;
    public static final int COLUMNS = 2;
    /**
     * Wathe's strip: 8 px frame, 16 px cells on an 18 px pitch (176 = 8 + 9 * 18 - 2 + 8, 32 = 8 + 16 + 8).
     * Wathe 热栏条：8 像素边框，16 像素格子，间距 18 像素（176 = 8 + 9 * 18 - 2 + 8，32 = 8 + 16 + 8）。
     */
    public static final int FRAME = 8;
    public static final int SLOT_SIZE = 16;
    public static final int SLOT_PITCH = 18;
    public static final int STRIP_WIDTH = 176;
    public static final int STRIP_HEIGHT = 32;
    public static final int PANEL_WIDTH = FRAME + COLUMNS * SLOT_PITCH - (SLOT_PITCH - SLOT_SIZE) + FRAME;
    public static final int PANEL_HEIGHT = PANEL_WIDTH;
    /** Gap between the armor block and the hotbar strip. / 护甲块与热栏条之间的间距。 */
    public static final int GAP = 4;
    /**
     * Where the frame is cut from Wathe's own {@code limited_inventory.png}: the strip's left part through its second
     * cell, its right cap, its first cell row, and the one-pixel inner lines below ({@code v = 24}) and above
     * ({@code v = 7}) that row, which together form the divider between the two block rows.
     * 边框取自 Wathe 自己的 {@code limited_inventory.png}：热栏条左段到第二格、右端帽、第一行格子，以及该行下方
     * （{@code v = 24}）与上方（{@code v = 7}）各一像素的内线，二者拼成两行之间的分隔线。
     */
    public static final int STRIP_LEFT_WIDTH = FRAME + COLUMNS * SLOT_PITCH - (SLOT_PITCH - SLOT_SIZE);
    public static final int STRIP_RIGHT_CAP_U = STRIP_WIDTH - FRAME;
    public static final int CELL_ROW_V = FRAME;
    public static final int CELL_LINE_BELOW_V = FRAME + SLOT_SIZE;
    public static final int CELL_LINE_ABOVE_V = FRAME - 1;
    public static final int TOP_PART_HEIGHT = FRAME + SLOT_SIZE;

    private LimitedInventoryArmorLayout() {
    }

    /** Left edge of the block for Wathe's strip left edge {@code screenX}. / 护甲块左边缘。 */
    public static int panelX(int screenX) {
        return screenX - GAP - PANEL_WIDTH;
    }

    /**
     * The y every other method here takes as {@code screenY}: Wathe's strip top, or with the second row shown, the top
     * of a strip-high band with the same centre as the 176x54 inventory block ({@code screenY - 22}).
     * 本类其他方法作为 {@code screenY} 接收的 y：Wathe 热栏顶边；显示第二行时，为与 176x54 物品栏块（{@code screenY - 22}）
     * 同中心、热栏高度的区域顶边。
     */
    public static int anchorY(int stripY, boolean secondRow) {
        return secondRow ? stripY - SECOND_ROW_PITCH / 2 : stripY;
    }

    /** Top edge of the block for the anchor {@code screenY} ({@link #anchorY}). / 护甲块顶边。 */
    public static int panelY(int screenY) {
        return screenY + (STRIP_HEIGHT - PANEL_HEIGHT) / 2;
    }

    /** Screen x of armor slot {@code index} (0 = head, 1 = chest, 2 = legs, 3 = feet). / 护甲槽屏幕 x。 */
    public static int slotX(int screenX, int index) {
        return panelX(screenX) + FRAME + (index % COLUMNS) * SLOT_PITCH;
    }

    /** Screen y of armor slot {@code index}. / 护甲槽屏幕 y。 */
    public static int slotY(int screenY, int index) {
        return panelY(screenY) + FRAME + (index / COLUMNS) * SLOT_PITCH;
    }

    public static boolean isArmorSlotId(int slotId) {
        return slotId >= FIRST_ARMOR_SLOT_ID && slotId < FIRST_ARMOR_SLOT_ID + ARMOR_SLOT_COUNT;
    }

    public static boolean isHotbarSlotId(int slotId) {
        return slotId >= HOTBAR_START && slotId < HOTBAR_END;
    }

    /**
     * Armor index (0..3) under the mouse, or -1. Uses Wathe's {@code isPointWithinBounds} rule: one pixel of slack on
     * each side, the far edge exclusive.
     * 鼠标下的护甲序号（0..3），没有则为 -1；沿用 Wathe {@code isPointWithinBounds} 的判定：四边各放宽 1 像素，远边不含。
     */
    public static int armorIndexAt(int screenX, int screenY, double mouseX, double mouseY) {
        for (int index = 0; index < ARMOR_SLOT_COUNT; index++) {
            int x = slotX(screenX, index);
            int y = slotY(screenY, index);
            if (mouseX >= x - 1 && mouseX < x + SLOT_SIZE + 1 && mouseY >= y - 1 && mouseY < y + SLOT_SIZE + 1) {
                return index;
            }
        }
        return -1;
    }

    /**
     * One vanilla {@code SWAP} click: {@code slotId} swapped with hotbar {@code button} (0..8).
     * 一次原版 {@code SWAP} 点击：{@code slotId} 与快捷栏第 {@code button} 格（0..8）互换。
     */
    public record Swap(int slotId, int button) {
    }

    /**
     * Plans a shift-click as one vanilla {@code SWAP} click, so the server applies the vanilla armor-slot rules
     * ({@code ArmorSlot.canInsert} / {@code canTakeItems}) to a single packet. A hotbar item whose preferred armor
     * slot is empty is equipped there (vanilla quick-move); a worn piece goes to the first empty hotbar slot, because
     * Wathe hides the main inventory that vanilla would fill. Everything else returns null and stays Wathe's plain
     * pick-up click.
     * 把 Shift 点击规划为一次原版 {@code SWAP} 点击，服务器对这一个数据包执行原版护甲槽规则。快捷栏物品的首选护甲槽
     * 为空时穿上（原版快速移动）；已穿的护甲移到第一个空快捷栏格，因为 Wathe 隐藏了原版会填入的主背包。其余情况返回
     * null，保持 Wathe 的普通拾取点击。
     *
     * @param clickedSlotId           handler slot id that was shift-clicked
     * @param clickedHasStack         that slot holds an item
     * @param preferredArmorSlotId    handler armor slot id (5..8) the clicked item prefers, or -1
     * @param preferredArmorSlotEmpty that armor slot is empty
     * @param firstEmptyHotbarButton  first empty hotbar index (0..8), or -1
     */
    public static @Nullable Swap planShiftClick(int clickedSlotId, boolean clickedHasStack, int preferredArmorSlotId,
                                                boolean preferredArmorSlotEmpty, int firstEmptyHotbarButton) {
        if (!clickedHasStack) {
            return null;
        }
        if (isHotbarSlotId(clickedSlotId)) {
            return isArmorSlotId(preferredArmorSlotId) && preferredArmorSlotEmpty
                    ? new Swap(preferredArmorSlotId, clickedSlotId - HOTBAR_START)
                    : null;
        }
        if (isArmorSlotId(clickedSlotId)) {
            return firstEmptyHotbarButton >= 0 && firstEmptyHotbarButton < HOTBAR_END - HOTBAR_START
                    ? new Swap(clickedSlotId, firstEmptyHotbarButton)
                    : null;
        }
        return null;
    }

    /**
     * False only for picking a worn piece onto an empty cursor while no shown slot is free: on close the server returns
     * a cursor stack to the first free inventory slot, which would then be in the main inventory Wathe hides. Shown
     * slots are the hotbar, plus the second row with SparkFactionAPI 0.1.5.13+, whose
     * {@code PlayerInventory#getEmptySlot} fills that row before hidden 9-26.
     * 仅在没有空的显示栏位时用空光标拿起已穿护甲返回 false：关闭界面时服务器会把光标物品放回第一个空背包格，而那会是
     * Wathe 隐藏的主背包。显示栏位为快捷栏，SparkFactionAPI 0.1.5.13+ 下还包括第二行，其
     * {@code PlayerInventory#getEmptySlot} 会先填第二行，再填隐藏的 9-26。
     */
    public static boolean mayPickUpArmor(int slotId, boolean slotHasStack, boolean cursorEmpty, boolean shownSlotFree) {
        return !isArmorSlotId(slotId) || !slotHasStack || !cursorEmpty || shownSlotFree;
    }

    /**
     * Armor slot id a cursor piece is put back into just before the screen closes, or -1. With no shown slot free,
     * closing would offer the cursor stack to the first free inventory slot, which is in the main inventory Wathe
     * hides; a piece whose own armor slot is empty goes back there instead. With a free shown slot (see
     * {@link #mayPickUpArmor}) vanilla's offer already lands there.
     * 关闭界面前光标上的护甲应放回的护甲槽 id，没有则为 -1。没有空的显示栏位时，关闭界面会把光标物品放进第一个空背包格，
     * 而那会是 Wathe 隐藏的主背包；其自身护甲槽为空的护甲改为放回该槽。有空的显示栏位时（见 {@link #mayPickUpArmor}）
     * 原版的放回本就落在那里。
     */
    public static int cursorReturnArmorSlotId(boolean cursorHasStack, int preferredArmorSlotId,
                                              boolean preferredArmorSlotEmpty, boolean shownSlotFree) {
        return cursorHasStack && !shownSlotFree && isArmorSlotId(preferredArmorSlotId)
                && preferredArmorSlotEmpty ? preferredArmorSlotId : -1;
    }

    /**
     * Handler armor slot id for an entity slot id (vanilla {@code 8 - EquipmentSlot.getEntitySlotId()}: feet 0 → 8,
     * head 3 → 5), or -1 when it is not a humanoid armor slot.
     * 实体槽位编号对应的护甲槽 id（原版 {@code 8 - getEntitySlotId()}），不是人形护甲槽时为 -1。
     */
    public static int armorSlotIdForEntitySlot(boolean humanoidArmor, int entitySlotId) {
        if (!humanoidArmor || entitySlotId < 0 || entitySlotId >= ARMOR_SLOT_COUNT) {
            return -1;
        }
        return FIRST_ARMOR_SLOT_ID + ARMOR_SLOT_COUNT - 1 - entitySlotId;
    }
}
