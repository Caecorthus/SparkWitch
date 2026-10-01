package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Applies a swap plan to a live player inventory. Captures the cursor and every open crafting input into
 * the outgoing overflow before any screen closes, keeps pinned stacks (wathe:key, wathe:letter, the mask)
 * in place, and never drops an item. Tracking is by slot snapshot only, never by stack tags.
 * 将交换规划应用到玩家背包。在关闭任何界面前把光标与所有已打开的合成输入收入传出存档的溢出区，
 * 固定物品（wathe:key、wathe:letter、面具）留在原位，且从不丢弃物品。仅按槽位快照追踪，从不使用物品标记。
 */
public final class BlackRavenInventorySwap {
    /** Crafting input slots 1..4 of the player's own 2x2 grid, 1..9 of an open 3x3 table. / 合成输入槽位范围。 */
    private static final int PLAYER_CRAFTING_INPUTS = 4;
    private static final int TABLE_CRAFTING_INPUTS = 9;

    private BlackRavenInventorySwap() {
    }

    public static boolean isPinned(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && BlackRavenDisguiseRules.isPinnedItemId(Registries.ITEM.getId(stack.getItem()));
    }

    /**
     * Moves cursor and crafting inputs (2x2 and any open 3x3) into {@code outgoing} overflow, then closes the
     * screen so its close callbacks find nothing to return or drop. Pinned stacks found there are returned so the
     * caller can put them back into the live inventory after the stash.
     * 在关闭界面前把光标与合成输入（2x2 与已打开的 3x3）移入传出溢出区，使关闭回调无物可返还或掉落；
     * 其中的固定物品会被返回，由调用方在存档后放回当前背包。
     */
    public static List<ItemStack> detachScreenInputs(ServerPlayerEntity player, BlackRavenIdentityStash outgoing) {
        List<ItemStack> pinned = new ArrayList<>();
        ScreenHandler current = player.currentScreenHandler;
        detachCursor(current, outgoing, pinned);
        if (current != player.playerScreenHandler) {
            detachCursor(player.playerScreenHandler, outgoing, pinned);
        }
        detachSlots(player.playerScreenHandler, PLAYER_CRAFTING_INPUTS, outgoing, pinned);
        if (current instanceof CraftingScreenHandler) {
            detachSlots(current, TABLE_CRAFTING_INPUTS, outgoing, pinned);
        }
        player.closeHandledScreen();
        return pinned;
    }

    /** Moves every non-pinned slot 0-40 into {@code outgoing}. / 将 0-40 的非固定槽位移入传出存档。 */
    public static void stashLive(ServerPlayerEntity player, BlackRavenIdentityStash outgoing) {
        PlayerInventory inventory = player.getInventory();
        boolean[] occupied = new boolean[BlackRavenDisguiseRules.PLAYER_SLOT_COUNT];
        boolean[] pinned = new boolean[BlackRavenDisguiseRules.PLAYER_SLOT_COUNT];
        for (int slot = 0; slot < BlackRavenDisguiseRules.PLAYER_SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStack(slot);
            occupied[slot] = !stack.isEmpty();
            pinned[slot] = isPinned(stack);
        }
        BlackRavenInventorySwapPlan.Plan plan = BlackRavenInventorySwapPlan.plan(occupied, pinned, new int[0]);
        for (int slot : plan.stashedSlots()) {
            outgoing.slots().add(new BlackRavenIdentityStash.SlotStack(slot, inventory.getStack(slot).copy()));
            inventory.setStack(slot, ItemStack.EMPTY);
        }
    }

    /**
     * Restores {@code incoming} slots (original slot, else first free main slot) and retries its overflow; what
     * still does not fit stays in its overflow. Returns true when something stayed in overflow.
     * 恢复存档槽位（原槽位，否则第一个空主槽位）并重试其溢出物品；仍放不下的留在溢出区。有物品留下时返回 true。
     */
    public static boolean loadStash(ServerPlayerEntity player, BlackRavenIdentityStash incoming) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        List<Integer> preferred = new ArrayList<>();
        for (BlackRavenIdentityStash.SlotStack entry : incoming.slots()) {
            if (!entry.stack().isEmpty()) {
                stacks.add(entry.stack());
                preferred.add(entry.slot());
            }
        }
        for (ItemStack stack : incoming.overflow()) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
                preferred.add(-1);
            }
        }
        boolean[] occupied = new boolean[BlackRavenDisguiseRules.PLAYER_SLOT_COUNT];
        for (int slot = 0; slot < BlackRavenDisguiseRules.PLAYER_SLOT_COUNT; slot++) {
            occupied[slot] = !inventory.getStack(slot).isEmpty();
        }
        // Everything still live stays in place here; the planner only fills free slots.
        // 此时仍在背包中的物品全部留在原位；规划器只填充空槽位。
        BlackRavenInventorySwapPlan.Plan plan = BlackRavenInventorySwapPlan.plan(
                occupied, occupied.clone(), preferred.stream().mapToInt(Integer::intValue).toArray());
        for (BlackRavenInventorySwapPlan.Placement placement : plan.placements()) {
            inventory.setStack(placement.targetSlot(), stacks.get(placement.incomingIndex()).copy());
        }
        incoming.slots().clear();
        incoming.overflow().clear();
        for (int index : plan.overflowIncomingIndices()) {
            incoming.overflow().add(stacks.get(index));
        }
        return !incoming.overflow().isEmpty();
    }

    /** Inserts a first-entry kit; leftovers go to {@code overflowTarget}. Returns true on leftovers. / 发放首次物品，剩余进入溢出区。 */
    public static boolean grantKit(ServerPlayerEntity player, List<ItemStack> kit, BlackRavenIdentityStash overflowTarget) {
        boolean leftovers = false;
        for (ItemStack stack : kit) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = stack.copy();
            player.getInventory().insertStack(remaining);
            if (!remaining.isEmpty()) {
                overflowTarget.overflow().add(remaining);
                leftovers = true;
            }
        }
        return leftovers;
    }

    /**
     * Puts pinned stacks detached from the cursor or a crafting grid back into the live inventory; anything
     * without room stays in {@code overflowTarget}.
     * 将从光标或合成栏取下的固定物品放回当前背包；放不下的留在溢出区。
     */
    public static void returnPinned(ServerPlayerEntity player, List<ItemStack> pinned,
                                    BlackRavenIdentityStash overflowTarget) {
        grantKit(player, pinned, overflowTarget);
    }

    /** Resyncs the player handler and any other open handler after a swap. / 交换后重新同步玩家与当前界面处理器。 */
    public static void resync(ServerPlayerEntity player) {
        player.getInventory().markDirty();
        player.playerScreenHandler.syncState();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            player.currentScreenHandler.syncState();
        }
    }

    private static void detachCursor(ScreenHandler handler, BlackRavenIdentityStash outgoing, List<ItemStack> pinned) {
        ItemStack cursor = handler.getCursorStack();
        if (!cursor.isEmpty()) {
            keep(cursor.copy(), outgoing, pinned);
            handler.setCursorStack(ItemStack.EMPTY);
        }
    }

    private static void detachSlots(ScreenHandler handler, int inputs, BlackRavenIdentityStash outgoing,
                                    List<ItemStack> pinned) {
        for (int slot = 1; slot <= inputs && slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty()) {
                keep(stack.copy(), outgoing, pinned);
                handler.getSlot(slot).setStack(ItemStack.EMPTY);
            }
        }
    }

    private static void keep(ItemStack stack, BlackRavenIdentityStash outgoing, List<ItemStack> pinned) {
        if (isPinned(stack)) {
            pinned.add(stack);
        } else {
            outgoing.overflow().add(stack);
        }
    }
}
