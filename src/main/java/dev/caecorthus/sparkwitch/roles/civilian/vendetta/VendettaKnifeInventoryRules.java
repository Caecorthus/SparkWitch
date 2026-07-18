package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Keeps the one-use knife inside its owner's personal inventory. */
public final class VendettaKnifeInventoryRules {
    private VendettaKnifeInventoryRules() {
    }

    public static boolean isKnife(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(SparkWitchItems.vendettaKnife());
    }

    public static boolean blocksDrop(ItemStack stack) {
        return isKnife(stack);
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
        boolean cursorKnife = isKnife(cursor);
        boolean validSlot = slotIndex >= 0 && slotIndex < player.currentScreenHandler.slots.size();
        var clickedSlot = validSlot ? player.currentScreenHandler.slots.get(slotIndex) : null;
        boolean clickedKnife = clickedSlot != null && isKnife(clickedSlot.getStack());
        boolean playerSlot = clickedSlot != null && clickedSlot.inventory == player.getInventory();

        if (actionType == SlotActionType.SWAP && !playerSlot
                && button >= 0 && button < player.getInventory().size()
                && isKnife(player.getInventory().getStack(button))) {
            return true;
        }
        if (!cursorKnife && !clickedKnife) {
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
