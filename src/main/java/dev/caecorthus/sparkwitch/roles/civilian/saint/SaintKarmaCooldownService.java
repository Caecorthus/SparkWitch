package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Applies Karma to item types currently carried in main inventory, hotbar, or offhand; armor is excluded.
 * 将业障冷却施加到主背包、快捷栏和副手内的物品类型；盔甲栏不在范围内。
 */
public final class SaintKarmaCooldownService {
    private SaintKarmaCooldownService() {
    }

    public static void apply(ServerPlayerEntity player, int requestedTicks) {
        if (requestedTicks <= 0) {
            return;
        }

        Set<Item> carriedItems = new HashSet<>();
        collectItems(player.getInventory().main, carriedItems);
        collectItems(player.getInventory().offHand, carriedItems);

        ItemCooldownManager manager = player.getItemCooldownManager();
        ItemCooldownManagerAccessor managerAccessor = (ItemCooldownManagerAccessor) manager;
        Map<Item, ?> entries = managerAccessor.sparkwitch$getEntries();
        int currentTick = managerAccessor.sparkwitch$getTick();
        for (Item item : carriedItems) {
            int existingTicks = remainingTicks(entries.get(item), currentTick);
            int mergedTicks = SaintRules.mergeCooldownTicks(existingTicks, requestedTicks);
            if (mergedTicks > existingTicks) {
                manager.set(item, mergedTicks);
            }
        }
    }

    private static void collectItems(Iterable<ItemStack> stacks, Set<Item> items) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                items.add(stack.getItem());
            }
        }
    }

    private static int remainingTicks(Object entry, int currentTick) {
        if (!(entry instanceof ItemCooldownEntryAccessor accessor)) {
            return 0;
        }
        return Math.max(0, accessor.sparkwitch$getEndTick() - currentTick);
    }
}
