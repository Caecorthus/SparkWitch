package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Item-specific transfer rules used by the bell binding mixins (mirrors the Black Raven ledger rules).
 * The bell is identified by its item class, so these rules stay safe before registry lookups.
 * 绑定之钟 mixin 使用的物品转移规则（与黑羽鸦感知册规则对应）；按物品类识别钟，因此不依赖注册表获取。
 */
public final class BellRingerInventoryRules {
    private BellRingerInventoryRules() {
    }

    public static boolean isTollBell(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TollBellItem;
    }

    /** Blocks drop/throw into an item entity. / 阻止丢弃或抛出为物品实体。 */
    public static boolean blocksDrop(ItemStack stack) {
        return isTollBell(stack);
    }

    /** Blocks the bell from Wathe's death-drop loop. / 阻止钟进入 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(ItemStack stack) {
        return isTollBell(stack);
    }

    /** Blocks throw, clone, swap, and moves out of the owner's own inventory slots. / 阻止抛出、复制、交换及移出拥有者自身背包栏位。 */
    public static boolean blocksSlotClick(
            PlayerEntity player,
            int slotIndex,
            int button,
            SlotActionType actionType
    ) {
        if (player == null || actionType == null) {
            return false;
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        boolean cursorBell = isTollBell(cursor);
        boolean validSlot = slotIndex >= 0 && slotIndex < player.currentScreenHandler.slots.size();
        var clickedSlot = validSlot ? player.currentScreenHandler.slots.get(slotIndex) : null;
        boolean clickedBell = clickedSlot != null && isTollBell(clickedSlot.getStack());
        boolean playerSlot = clickedSlot != null && clickedSlot.inventory == player.getInventory();

        // Number-key/offhand swap of the bell into a foreign slot. / 数字键或副手交换把钟换入外部栏位。
        if (actionType == SlotActionType.SWAP && !playerSlot
                && button >= 0 && button < player.getInventory().size()
                && isTollBell(player.getInventory().getStack(button))) {
            return true;
        }
        if (!cursorBell && !clickedBell) {
            return false;
        }
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE) {
            return true;
        }
        if (actionType == SlotActionType.QUICK_MOVE) {
            return player.currentScreenHandler != player.playerScreenHandler || !playerSlot;
        }
        return !validSlot || !playerSlot;
    }
}
