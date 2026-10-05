package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.function.IntPredicate;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Gift Watch loadout (owner decision 2026-10-05), driven by {@link TimeStealerLoadoutService}. SparkTraits Conscience
 * settles after Wathe's RoleAssigned, so the watch is granted lazily from the living holder's tick, not at role
 * assignment: a Conscience Time Stealer keeps exactly one Gift Watch in the hotbar next to the Clock, and its first
 * grant of the round writes the 45 s round-start cooldown; anyone else loses every copy. Like the Clock it never
 * creates item entities, and every placement is an explicit slot write.
 * 赠时怀表装备（所有者决定 2026-10-05），由 {@link TimeStealerLoadoutService} 驱动。SparkTraits 善良在 Wathe 的
 * RoleAssigned 之后才确定，因此赠时怀表由存活持有者的 tick 延迟发放，而不是在职业分配时发放：善良窃时者在快捷栏里于时钟之外
 * 恰好保留一块赠时怀表，本局首次发放时写入 45 秒开局冷却；其他人失去所有副本。与时钟一样从不生成物品实体，每次放置都是
 * 显式的栏位写入。
 */
final class TimeGiftWatchLoadout {
    private static final int NO_SLOT = -1;

    private TimeGiftWatchLoadout() {
    }

    /** Living exact Time Stealer's tick. / 存活精确窃时者的 tick。 */
    static void tickHolder(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!TimeStealerRules.holdsGiftWatch(TimeStealerRules.isTimeStealer(game.getRole(player)),
                SparkFactionApi.resolveEffectiveFaction(player, game))) {
            removeGiftWatches(player);
            return;
        }
        ensureGiftWatchInHotbar(player);
        TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(player);
        if (state.giftReadyAt() <= 0L) {
            // First grant this round (the round reset zeroes it): the round-start cooldown, like the Clock's.
            // 本局首次发放（回合重置会将其归零）：与时钟相同的开局冷却。
            int ticks = TimeStealerRules.GIFT_INITIAL_COOLDOWN_TICKS;
            state.setGiftReadyAt(player.getServerWorld().getTime() + ticks);
            TimeGiftWatchService.showCooldown(player, SparkWitchItems.timeStealerGiftWatch(), ticks);
            return;
        }
        TimeGiftWatchService.keepDisplayedCooldown(player);
    }

    /**
     * With a full hotbar, the slot whose item is moved out for the Gift Watch: the rightmost hotbar slot that is neither
     * selected nor holding the Clock, so neither the held item nor the Clock is ever displaced; -1 when none.
     * 快捷栏已满时为赠时怀表腾出的栏位：最右侧既非选中、也不放着时钟的快捷栏位，因此从不移走手持物品或时钟；没有时为 -1。
     */
    static int displacedHotbarSlot(int selectedSlot, IntPredicate holdsClock) {
        for (int slot = PlayerInventory.getHotbarSize() - 1; slot >= 0; slot--) {
            if (slot != selectedSlot && !holdsClock.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    /**
     * Keeps exactly one Gift Watch, in the hotbar: the leftmost hotbar copy is kept, every other copy (main slots 9-35,
     * armor, offhand, cursor, crafting grid or open container) is removed, and a missing one is recreated in the hotbar.
     * 保持恰好一块赠时怀表并使其位于快捷栏：保留最左侧的快捷栏副本，移除其他所有副本（主背包 9-35、盔甲、副手、光标、
     * 合成格或已打开的容器），缺失时在快捷栏重新创建。
     */
    private static void ensureGiftWatchInHotbar(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        int kept = NO_SLOT;
        int vacated = NO_SLOT;
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!TimeStealerInventoryRules.isGiftWatch(stack)) {
                continue;
            }
            if (kept == NO_SLOT && PlayerInventory.isValidHotbarIndex(slot)) {
                kept = slot;
                if (stack.getCount() > 1) {
                    stack.setCount(1);
                    changed = true;
                }
                continue;
            }
            inventory.setStack(slot, ItemStack.EMPTY);
            if (vacated == NO_SLOT && isStorageSlot(slot)) {
                vacated = slot;
            }
            changed = true;
        }
        if (TimeStealerInventoryRules.isGiftWatch(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && TimeStealerInventoryRules.isGiftWatch(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (kept == NO_SLOT) {
            changed |= placeGiftWatchInHotbar(inventory, vacated);
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    /**
     * Puts a fresh Gift Watch into the hotbar; with a full hotbar the displaced item moves where the Clock's would
     * ({@link TimeStealerLoadoutService#displacementSlot}). With no room nothing is moved and the next tick retries.
     * 把新的赠时怀表放入快捷栏；快捷栏已满时被移出的物品去往与时钟相同的位置（{@link TimeStealerLoadoutService#displacementSlot}）。
     * 没有空间时不移动任何物品，由下一 tick 重试。
     */
    private static boolean placeGiftWatchInHotbar(PlayerInventory inventory, int vacated) {
        int target = TimeStealerLoadoutService.hotbarTarget(inventory.selectedSlot,
                slot -> inventory.getStack(slot).isEmpty());
        if (target == NO_SLOT) {
            target = displacedHotbarSlot(inventory.selectedSlot,
                    slot -> TimeStealerInventoryRules.isClock(inventory.getStack(slot)));
            if (target == NO_SLOT) {
                return false;
            }
            int destination = TimeStealerLoadoutService.displacementSlot(SparkFactionSecondRowCompat.isShown(), vacated,
                    slot -> inventory.getStack(slot).isEmpty());
            if (destination == NO_SLOT) {
                return false;
            }
            inventory.setStack(destination, inventory.getStack(target));
        }
        inventory.setStack(target, new ItemStack(SparkWitchItems.timeStealerGiftWatch()));
        return true;
    }

    /** Removes every Gift Watch from inventory 0..40, the cursor and open handler slots. / 从背包 0..40、光标与已打开界面栏位移除所有赠时怀表。 */
    static void removeGiftWatches(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (TimeStealerInventoryRules.isGiftWatch(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (TimeStealerInventoryRules.isGiftWatch(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (TimeStealerInventoryRules.isGiftWatch(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
            handler.sendContentUpdates();
        }
    }

    /** Main slots 9-35 or the offhand. / 主背包 9-35 或副手。 */
    private static boolean isStorageSlot(int slot) {
        return (slot >= PlayerInventory.getHotbarSize() && slot < PlayerInventory.MAIN_SIZE)
                || slot == PlayerInventory.OFF_HAND_SLOT;
    }
}
