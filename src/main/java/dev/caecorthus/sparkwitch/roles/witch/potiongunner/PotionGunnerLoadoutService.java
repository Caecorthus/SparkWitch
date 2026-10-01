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
     * kept; every other copy (and any copy in a crafting grid or open foreign container) is removed, handing its shell
     * to the kept launcher when that one is empty. A kept launcher outside the hotbar moves into an empty hotbar slot
     * (Wathe's screen shows only the hotbar). A missing launcher is created in the leftmost empty hotbar slot, else
     * the first empty hidden main slot; with no room nothing is moved or destroyed and the next sweep retries. Cheap
     * when nothing is wrong: a slot scan with no writes.
     * 保持恰好一个炮筒：按快捷栏、光标、隐藏主背包、副手、盔甲的顺序保留找到的第一个；移除其他所有副本（含合成格或已打开的
     * 外部容器中的副本），若保留的炮筒为空则把被移除副本的炮弹交给它。位于快捷栏之外的保留炮筒会移入空快捷栏位（Wathe
     * 界面只显示快捷栏）。缺失时在最左侧空快捷栏位创建，否则放入第一个空的隐藏主背包栏位；完全没有空间时不移动也不销毁
     * 任何物品，由下一次清扫重试。一切正常时开销很低：只扫描栏位而不写入。
     */
    public static void ensureLauncher(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        int keeper = keeperSlot(slot -> PotionGunnerInventoryRules.isLauncher(inventory.getStack(slot)),
                PotionGunnerInventoryRules.isLauncher(handler.getCursorStack()));
        PotionShellType rescued = null;
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (slot == keeper || !PotionGunnerInventoryRules.isLauncher(stack)) {
                continue;
            }
            rescued = firstLoad(rescued, stack);
            inventory.setStack(slot, ItemStack.EMPTY);
            changed = true;
        }
        if (keeper != CURSOR && PotionGunnerInventoryRules.isLauncher(handler.getCursorStack())) {
            rescued = firstLoad(rescued, handler.getCursorStack());
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && PotionGunnerInventoryRules.isLauncher(slot.getStack())) {
                rescued = firstLoad(rescued, slot.getStack());
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
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
                changed = true;
            }
        } else {
            int target = placementSlot(slot -> inventory.getStack(slot).isEmpty());
            if (target == NO_SLOT) {
                finish(player, changed);
                return;
            }
            kept = new ItemStack(SparkWitchItems.potionLauncher());
            inventory.setStack(target, kept);
            changed = true;
        }
        if (rescued != null && !PotionLauncherLoad.isLoaded(kept)) {
            PotionLauncherLoad.setLoaded(kept, rescued);
            changed = true;
        }
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
                // Hotbar first, then hidden main slots; a remainder with a completely full inventory is lost.
                // 先快捷栏，后隐藏主背包；背包完全满时剩余部分丢失。
                serverPlayer.getInventory().insertStack(copy);
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

    private static @Nullable PotionShellType firstLoad(@Nullable PotionShellType rescued, ItemStack removed) {
        return rescued != null ? rescued : PotionLauncherLoad.loaded(removed).orElse(null);
    }

    private static void finish(ServerPlayerEntity player, boolean changed) {
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
