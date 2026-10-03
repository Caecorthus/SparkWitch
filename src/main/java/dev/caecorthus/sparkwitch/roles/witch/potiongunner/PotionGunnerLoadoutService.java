package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherLoad;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Grants the bound launcher. Idempotent: called on role assignment (forced roles) and again from the accomplice
 * pool's post-recruit hook, because recruitment rewrites the whole inventory after {@code RoleAssigned}; the
 * lifecycle sweep also calls it for every living gunner. Server-authoritative; it never creates item entities and
 * never routes the launcher through vanilla give/offer paths, so every placement is an explicit slot write.
 * 发放绑定的炮筒。幂等：在职业分配时（强制指定职业）调用，并在共犯池的招募完成钩子里再调用一次，
 * 因为招募会在 {@code RoleAssigned} 之后整体重写背包；生命周期清扫也会对每名存活药炮手调用。由服务端裁定；从不生成
 * 物品实体，也从不经由原版给予/放入路径放置炮筒，每次放置都是显式的栏位写入。
 */
public final class PotionGunnerLoadoutService {
    static final int NO_SLOT = -1;
    /** Pseudo slot for the open handler's cursor. / 已打开界面光标的伪栏位。 */
    static final int CURSOR = -2;

    private PotionGunnerLoadoutService() {
    }

    /**
     * Side-agnostic holder test: a living, playing, exact Potion Gunner. Spectator mode does not count against it, so
     * a gunner swallowed by the NoellesRoles Taotie keeps the launcher and shells (Time Stealer rule).
     * 两端通用的持有者判定：存活、在对局中且职业恰为药炮手。旁观模式不影响判定，因此被 NoellesRoles 饕餮吞下的
     * 药炮手保留炮筒与炮弹（与窃时者规则一致）。
     */
    public static boolean mayHold(@Nullable PlayerEntity player) {
        return player != null
                && PotionGunnerRules.isPotionGunner(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    /**
     * Keeps exactly one launcher: the first one found in hotbar, cursor, hidden main slots, offhand, armor order is
     * kept and moved into an empty hotbar slot when it sits elsewhere (Wathe's screen shows only the hotbar); a missing
     * launcher is created in the leftmost empty hotbar slot, else the first empty hidden main slot. Every other copy
     * (inventory, cursor, or a crafting grid / open foreign container) is removed, and its loaded shell is never lost:
     * it loads the kept launcher when that one is empty, else returns as a shell item hotbar-first (a hidden slot
     * when the hotbar is full, the emptied cursor as a last resort); a foreign copy whose shell finds no room at all,
     * or that no kept launcher could replace, stays where it is until the next sweep. Finally, shells sitting in hidden
     * main slots 9-35 are moved into hotbar room (same-type stacks first, then empty slots) without displacing
     * anything. Cheap when nothing is wrong: a slot scan with no writes.
     * 保持恰好一个炮筒：按快捷栏、光标、隐藏主背包、副手、盔甲的顺序保留找到的第一个，若不在快捷栏则移入空快捷栏位
     * （Wathe 界面只显示快捷栏）；缺失时在最左侧空快捷栏位创建，否则放入第一个空的隐藏主背包栏位。其他所有副本（背包、
     * 光标，或合成格/已打开的外部容器中）都会被移除，且其已装填的炮弹绝不丢失：保留的炮筒为空时装入其中，否则作为炮弹物品
     * 优先放回快捷栏（快捷栏已满时放入隐藏栏位，最后才放到已清空的光标上）；外部副本的炮弹完全无处安放、或没有可保留的
     * 炮筒能替代它时，该副本原地保留，等待下一次清扫。最后，把位于隐藏主背包 9-35 格的炮弹移入快捷栏空余处（先并入同种
     * 炮弹堆，再放入空栏位），绝不挤占其他物品。一切正常时开销很低：只扫描栏位而不写入。
     */
    public static void ensureLauncher(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        int keeper = keeperSlot(slot -> PotionGunnerInventoryRules.isLauncher(inventory.getStack(slot)),
                PotionGunnerInventoryRules.isLauncher(handler.getCursorStack()));
        boolean changed = false;
        ItemStack kept;
        if (keeper == CURSOR) {
            kept = handler.getCursorStack();
        } else if (keeper != NO_SLOT) {
            kept = inventory.getStack(keeper);
            if (kept.getCount() > 1) {
                kept.setCount(1);
                changed = true;
            }
            int hotbar = PlayerInventory.isValidHotbarIndex(keeper) ? NO_SLOT
                    : placementSlot(slot -> PlayerInventory.isValidHotbarIndex(slot)
                            && inventory.getStack(slot).isEmpty());
            if (hotbar != NO_SLOT) {
                inventory.setStack(keeper, ItemStack.EMPTY);
                inventory.setStack(hotbar, kept);
                keeper = hotbar;
                changed = true;
            }
        } else {
            int target = placementSlot(slot -> inventory.getStack(slot).isEmpty());
            kept = target == NO_SLOT ? null : new ItemStack(SparkWitchItems.potionLauncher());
            if (kept != null) {
                inventory.setStack(target, kept);
                keeper = target;
                changed = true;
            }
        }
        // Inventory copies free their own slot first, so their shell always finds room.
        // 背包中的副本先腾出自己的栏位，因此其炮弹总能找到位置。
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack copy = inventory.getStack(slot);
            if (slot == keeper || !PotionGunnerInventoryRules.isLauncher(copy)) {
                continue;
            }
            inventory.setStack(slot, ItemStack.EMPTY);
            if (rehomeShell(inventory, handler, kept, copy)) {
                changed = true;
            } else {
                inventory.setStack(slot, copy);
            }
        }
        if (keeper != CURSOR && PotionGunnerInventoryRules.isLauncher(handler.getCursorStack())) {
            ItemStack copy = handler.getCursorStack();
            handler.setCursorStack(ItemStack.EMPTY);
            if (rehomeShell(inventory, handler, kept, copy)) {
                changed = true;
            } else {
                handler.setCursorStack(copy);
            }
        }
        for (Slot slot : handler.slots) {
            if (kept == null || slot.inventory == inventory
                    || !PotionGunnerInventoryRules.isLauncher(slot.getStack())) {
                continue;
            }
            ItemStack copy = slot.getStack();
            slot.setStack(ItemStack.EMPTY);
            if (rehomeShell(inventory, handler, kept, copy)) {
                changed = true;
            } else {
                slot.setStack(copy);
            }
        }
        changed |= surfaceHiddenShells(inventory);
        finish(player, changed);
    }

    /**
     * Removes every launcher and shell from inventory 0..40, the cursor, and every open handler slot (crafting grid or
     * foreign container included). Used for anyone who may not hold them, and on death, reset, and finalize.
     * 从背包 0..40、光标及已打开界面的所有栏位（含合成格与外部容器）移除所有炮筒与炮弹。用于不可持有者，以及死亡、
     * 重置与局末。
     */
    public static void stripAll(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (PotionGunnerInventoryRules.isBound(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (PotionGunnerInventoryRules.isBound(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (PotionGunnerInventoryRules.isBound(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        finish(player, changed);
    }

    /**
     * Called at the HEAD of the player drop method on both sides; true cancels the drop, so a bound item never becomes
     * an item entity. Move semantics on the server: the passed stack is emptied first (some callers already removed it
     * from its slot, others still reference it), then a copy is re-inserted for a living holder — a launcher keeps its
     * loaded shell — and destroyed for anyone else, who is stripped anyway. On the client only the prediction is
     * cancelled; the server decides.
     * 在双端的玩家丢弃方法 HEAD 处调用；返回 true 即取消丢弃，因此绑定物品永不成为物品实体。服务端采用移动语义：先清空
     * 传入的物品堆（有些调用方已把它移出栏位，另一些仍在栏位中引用它），再为存活持有者重新放回一份副本（炮筒保留已装填
     * 的炮弹），其他人则直接销毁（他们本来就会被收走）。客户端只取消预测，由服务端裁定。
     */
    public static boolean interceptDrop(PlayerEntity player, ItemStack stack) {
        if (!PotionGunnerInventoryRules.isBound(stack)) {
            return false;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ItemStack copy = stack.copy();
            stack.setCount(0);
            if (serverPlayer.isAlive() && mayHold(serverPlayer)) {
                // Hotbar first, then hidden main slots; whatever still does not fit goes back into the passed stack, so
                // a caller that still holds it in a slot keeps it there instead of losing it.
                // 先快捷栏，后隐藏主背包；仍放不下的部分放回传入的物品堆，仍在栏位中引用它的调用方因此原样保留，而不会丢失。
                if (!returnToInventory(serverPlayer.getInventory(), copy, false)) {
                    stack.setCount(copy.getCount());
                }
                serverPlayer.getInventory().markDirty();
            }
        }
        return true;
    }

    /**
     * Which existing launcher to keep: the leftmost hotbar slot, else the cursor, else hidden main slots 9-35, else
     * the offhand, else armor; {@link #CURSOR} or {@link #NO_SLOT} when none.
     * 保留哪一个已有炮筒：最左侧快捷栏位，否则光标，否则隐藏主背包 9-35，否则副手，否则盔甲；
     * 光标返回 {@link #CURSOR}，都没有时返回 {@link #NO_SLOT}。
     */
    static int keeperSlot(IntPredicate holdsLauncher, boolean cursorHoldsLauncher) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (holdsLauncher.test(slot)) {
                return slot;
            }
        }
        if (cursorHoldsLauncher) {
            return CURSOR;
        }
        for (int slot = PlayerInventory.getHotbarSize(); slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (holdsLauncher.test(slot)) {
                return slot;
            }
        }
        if (holdsLauncher.test(PlayerInventory.OFF_HAND_SLOT)) {
            return PlayerInventory.OFF_HAND_SLOT;
        }
        for (int slot = PlayerInventory.MAIN_SIZE; slot < PlayerInventory.OFF_HAND_SLOT; slot++) {
            if (holdsLauncher.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    /**
     * Where a launcher goes: the leftmost empty hotbar slot, else the first empty hidden main slot 9-35, else
     * {@link #NO_SLOT}. Never the offhand or armor.
     * 炮筒放置的位置：最左侧空快捷栏位，否则第一个空的隐藏主背包栏位 9-35，否则 {@link #NO_SLOT}。从不放入副手或盔甲栏。
     */
    static int placementSlot(IntPredicate emptySlot) {
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (emptySlot.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    /**
     * Return order for a bound stack, never the offhand or armor and never a slot holding anything else: same-type
     * hotbar stacks with room, empty hotbar slots, then (unless {@code hotbarOnly}) same-type hidden main stacks with
     * room and empty hidden main slots 9-35.
     * 绑定物品堆的放回顺序，从不使用副手或盔甲栏，也从不占用放着其他物品的栏位：有空余的同种快捷栏物品堆、空快捷栏位，
     * 然后（{@code hotbarOnly} 为 false 时）有空余的同种隐藏主背包物品堆与空的隐藏主背包栏位 9-35。
     */
    static List<Integer> returnSlots(IntPredicate sameTypeWithRoom, IntPredicate empty, boolean hotbarOnly) {
        int hotbar = PlayerInventory.getHotbarSize();
        List<Integer> slots = new ArrayList<>();
        collect(slots, 0, hotbar, sameTypeWithRoom);
        collect(slots, 0, hotbar, empty);
        if (!hotbarOnly) {
            collect(slots, hotbar, PlayerInventory.MAIN_SIZE, sameTypeWithRoom);
            collect(slots, hotbar, PlayerInventory.MAIN_SIZE, empty);
        }
        return slots;
    }

    /**
     * Moves as much of {@code stack} as fits into {@link #returnSlots} order; true when all of it was placed.
     * 按 {@link #returnSlots} 的顺序尽量放入 {@code stack}；全部放入时返回 true。
     */
    static boolean returnToInventory(PlayerInventory inventory, ItemStack stack, boolean hotbarOnly) {
        for (int slot : returnSlots(index -> canMerge(inventory, inventory.getStack(index), stack),
                index -> inventory.getStack(index).isEmpty(), hotbarOnly)) {
            if (stack.isEmpty()) {
                break;
            }
            ItemStack target = inventory.getStack(slot);
            if (target.isEmpty()) {
                inventory.setStack(slot, stack.split(Math.min(stack.getCount(), inventory.getMaxCount(stack))));
            } else if (canMerge(inventory, target, stack)) {
                int moved = Math.min(stack.getCount(), maxCount(inventory, target) - target.getCount());
                target.increment(moved);
                stack.decrement(moved);
            }
        }
        return stack.isEmpty();
    }

    /**
     * Gives a removed duplicate launcher's shell a home: the kept launcher when it is empty, else a shell item
     * hotbar-first, else the empty cursor. False (nothing changed) when it has nowhere to go, so the caller puts the
     * duplicate back instead of deleting the shell.
     * 为被移除的重复炮筒中的炮弹安置去处：保留的炮筒为空时装入其中，否则作为炮弹物品优先放回快捷栏，再否则放到空光标上。
     * 无处可去时返回 false（不做任何改动），由调用方放回该副本，而不是删除炮弹。
     */
    private static boolean rehomeShell(PlayerInventory inventory, ScreenHandler handler, @Nullable ItemStack kept,
                                       ItemStack duplicate) {
        PotionShellType shell = PotionLauncherLoad.loaded(duplicate).orElse(null);
        if (shell == null) {
            return true;
        }
        if (kept != null && !PotionLauncherLoad.isLoaded(kept)) {
            PotionLauncherLoad.setLoaded(kept, shell);
            return true;
        }
        ItemStack item = new ItemStack(SparkWitchItems.potionShell(shell));
        if (returnToInventory(inventory, item, false)) {
            return true;
        }
        if (handler.getCursorStack().isEmpty()) {
            handler.setCursorStack(item);
            return true;
        }
        return false;
    }

    /**
     * Moves shells out of hidden main slots 9-35 into hotbar room; true when anything moved.
     * 把隐藏主背包 9-35 格中的炮弹移入快捷栏空余处；有移动时返回 true。
     */
    private static boolean surfaceHiddenShells(PlayerInventory inventory) {
        boolean changed = false;
        for (int slot = PlayerInventory.getHotbarSize(); slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!PotionGunnerInventoryRules.isShell(stack)) {
                continue;
            }
            int before = stack.getCount();
            returnToInventory(inventory, stack, true);
            if (stack.getCount() != before) {
                changed = true;
                if (stack.isEmpty()) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                }
            }
        }
        return changed;
    }

    private static boolean canMerge(PlayerInventory inventory, ItemStack target, ItemStack stack) {
        return !target.isEmpty() && target != stack && ItemStack.areItemsAndComponentsEqual(target, stack)
                && target.getCount() < maxCount(inventory, target);
    }

    private static int maxCount(PlayerInventory inventory, ItemStack stack) {
        return Math.min(stack.getMaxCount(), inventory.getMaxCount(stack));
    }

    private static void collect(List<Integer> slots, int from, int to, IntPredicate include) {
        for (int slot = from; slot < to; slot++) {
            if (include.test(slot)) {
                slots.add(slot);
            }
        }
    }

    private static void finish(ServerPlayerEntity player, boolean changed) {
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
