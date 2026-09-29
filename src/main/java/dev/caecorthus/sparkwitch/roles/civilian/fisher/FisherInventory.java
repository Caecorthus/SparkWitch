package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

final class FisherInventory {
    private FisherInventory() {
    }

    static ItemStack findBait(PlayerInventory inventory) {
        for (ItemStack stack : inventory.main) {
            if (stack.isOf(SparkWitchItems.fishBait()) && !stack.isEmpty()) {
                return stack;
            }
        }
        for (ItemStack stack : inventory.offHand) {
            if (stack.isOf(SparkWitchItems.fishBait()) && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Reserve a visible slot before spending bait; hidden main-inventory slots never receive catches.
     * 扣鱼饵前预留可见槽位；渔获绝不放进隐藏的主背包槽位。 */
    static int catchSlot(List<ItemStack> main, ItemStack catchStack) {
        int end = Math.min(PlayerInventory.getHotbarSize(), main.size());
        for (int slot = 0; slot < end; slot++) {
            ItemStack existing = main.get(slot);
            if (!existing.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, catchStack)
                    && existing.getCount() + catchStack.getCount() <= existing.getMaxCount()) {
                return slot;
            }
        }
        for (int slot = 0; slot < end; slot++) {
            if (main.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    static void deliver(List<ItemStack> main, int slot, ItemStack stack) {
        ItemStack existing = main.get(slot);
        if (existing.isEmpty()) {
            main.set(slot, stack);
        } else {
            existing.increment(stack.getCount());
        }
    }

    static void sync(ServerPlayerEntity player) {
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }
}
