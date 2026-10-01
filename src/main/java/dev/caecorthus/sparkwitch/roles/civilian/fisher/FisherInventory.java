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
        // Prefer bait that frees a hotbar slot, even if an earlier bait stack contains more than one.
        // 优先耗尽能腾出快捷栏槽位的单个鱼饵，即使更前方还有多件鱼饵栈。
        int hotbarEnd = Math.min(PlayerInventory.getHotbarSize(), inventory.main.size());
        for (int slot = 0; slot < hotbarEnd; slot++) {
            ItemStack stack = inventory.main.get(slot);
            if (stack.isOf(SparkWitchItems.fishBait()) && stack.getCount() == 1) {
                return stack;
            }
        }
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

    /** Reserve room for ANY catch before rolling; the exact spent bait stack may free its hotbar slot.
     * 抽奖前为任意渔获预留空位；本次恰好耗尽的鱼饵栈可以腾出快捷栏槽位。 */
    static int catchSlot(List<ItemStack> main, ItemStack bait) {
        int end = Math.min(PlayerInventory.getHotbarSize(), main.size());
        for (int slot = 0; slot < end; slot++) {
            ItemStack existing = main.get(slot);
            if (existing.isEmpty() || existing == bait && bait.getCount() == 1) {
                return slot;
            }
        }
        return -1;
    }

    static void deliver(List<ItemStack> main, int slot, ItemStack stack) {
        main.set(slot, stack);
    }

    static void sync(ServerPlayerEntity player) {
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }
}
