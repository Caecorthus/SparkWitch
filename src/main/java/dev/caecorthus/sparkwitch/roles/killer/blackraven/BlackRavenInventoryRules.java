package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/** Item-specific transfer rules for the secret-free bound ledger. */
public final class BlackRavenInventoryRules {
    private BlackRavenInventoryRules() {
    }

    public static boolean isLedger(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(SparkWitchItems.blackRavenLedger());
    }

    public static boolean blocksDrop(ItemStack stack) {
        return isLedger(stack);
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
        boolean cursorLedger = isLedger(cursor);
        boolean validSlot = slotIndex >= 0 && slotIndex < player.currentScreenHandler.slots.size();
        var clickedSlot = validSlot ? player.currentScreenHandler.slots.get(slotIndex) : null;
        boolean clickedLedger = clickedSlot != null && isLedger(clickedSlot.getStack());
        boolean playerSlot = clickedSlot != null && clickedSlot.inventory == player.getInventory();

        if (actionType == SlotActionType.SWAP && !playerSlot
                && button >= 0 && button < player.getInventory().size()
                && isLedger(player.getInventory().getStack(button))) {
            return true;
        }
        if (!cursorLedger && !clickedLedger) {
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
