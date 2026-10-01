package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Item-specific transfer rules for Black Raven's bound items: the secret-free ledger and the Raven Mask.
 * Both stay inside their owner's own inventory slots and never become item entities.
 * 黑羽鸦绑定物品（无秘密的账本与鸦羽假面）的转移规则：两者只能留在持有者自己的背包槽位中，且不会成为掉落物。
 */
public final class BlackRavenInventoryRules {
    private BlackRavenInventoryRules() {
    }

    public static boolean isLedger(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(SparkWitchItems.blackRavenLedger());
    }

    public static boolean isMask(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(SparkWitchItems.blackRavenMask());
    }

    /** Ledger or Raven Mask. / 账本或鸦羽假面。 */
    public static boolean isBound(ItemStack stack) {
        return isLedger(stack) || isMask(stack);
    }

    public static boolean blocksDrop(ItemStack stack) {
        return isBound(stack);
    }

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
        boolean cursorBound = isBound(cursor);
        boolean validSlot = slotIndex >= 0 && slotIndex < player.currentScreenHandler.slots.size();
        var clickedSlot = validSlot ? player.currentScreenHandler.slots.get(slotIndex) : null;
        boolean clickedBound = clickedSlot != null && isBound(clickedSlot.getStack());
        boolean playerSlot = clickedSlot != null && clickedSlot.inventory == player.getInventory();

        if (actionType == SlotActionType.SWAP && !playerSlot
                && button >= 0 && button < player.getInventory().size()
                && isBound(player.getInventory().getStack(button))) {
            return true;
        }
        if (!cursorBound && !clickedBound) {
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
