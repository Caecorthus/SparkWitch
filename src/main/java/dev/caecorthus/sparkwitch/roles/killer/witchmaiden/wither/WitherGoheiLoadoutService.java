package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.util.GiveCommandDropScope;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
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
 * Keeps exactly one Wither Gohei on a living Witch Maiden who has unlocked the Mini Wither, and none on anyone else in
 * the match. A full inventory at unlock is retried by the periodic sweep.
 * 让已解锁小凋灵的存活巫女身上恰好有一把凋灵御币，对局中其他人一把都没有。解锁时背包已满的情况由定期清扫重试。
 */
public final class WitherGoheiLoadoutService {
    static final int NO_SLOT = -1;

    private WitherGoheiLoadoutService() {
    }

    /** A living, playing Witch Maiden with the Wither unlocked. / 已解锁小凋灵、存活且在局的巫女。 */
    public static boolean mayHold(@Nullable PlayerEntity player) {
        return player != null
                && WitchMaidenRules.isWitchMaiden(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && MiniWitherPlayerComponent.KEY.get(player).isUnlocked();
    }

    /**
     * Keeps the first Gohei found (hotbar, then the rest of the inventory, then the cursor), moves it into a shown slot
     * when it sits hidden, creates one when missing, and removes every other copy.
     * 保留找到的第一把御币（先快捷栏，再背包其余位置，最后光标），位于隐藏栏位时移入显示栏位，缺失时创建，并移除其他所有副本。
     */
    public static void ensureGohei(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;
        boolean changed = false;
        int keeper = NO_SLOT;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (WitherGoheiInventoryRules.isGohei(inventory.getStack(slot))) {
                keeper = slot;
                break;
            }
        }
        boolean cursorKept = keeper == NO_SLOT && WitherGoheiInventoryRules.isGohei(handler.getCursorStack());
        if (keeper != NO_SLOT && !isShown(keeper)) {
            int moved = placementSlot(SparkFactionSecondRowCompat.isShown(), slot -> inventory.getStack(slot).isEmpty());
            if (moved != NO_SLOT && isShown(moved)) {
                inventory.setStack(moved, inventory.getStack(keeper));
                inventory.setStack(keeper, ItemStack.EMPTY);
                keeper = moved;
                changed = true;
            }
        } else if (keeper == NO_SLOT && !cursorKept) {
            int target = placementSlot(SparkFactionSecondRowCompat.isShown(), slot -> inventory.getStack(slot).isEmpty());
            if (target != NO_SLOT) {
                inventory.setStack(target, new ItemStack(SparkWitchItems.witherGohei()));
                keeper = target;
                changed = true;
            }
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (slot != keeper && WitherGoheiInventoryRules.isGohei(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (!cursorKept && WitherGoheiInventoryRules.isGohei(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && WitherGoheiInventoryRules.isGohei(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        finish(player, changed);
    }

    /** Removes every Gohei: inventory, cursor and open handler slots. / 移除所有御币：背包、光标与已打开界面的栏位。 */
    public static void stripAll(ServerPlayerEntity player) {
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (WitherGoheiInventoryRules.isGohei(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (WitherGoheiInventoryRules.isGohei(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (Slot slot : handler.slots) {
            if (WitherGoheiInventoryRules.isGohei(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        finish(player, changed);
    }

    /**
     * Player drop HEAD on both sides; true cancels the drop. On the server the stack goes back into the inventory for a
     * holder or a free holder (not a match participant), and is destroyed for anyone else.
     * 双端玩家丢弃方法 HEAD；返回 true 即取消丢弃。服务端为持有者或自由持有者（非对局参与者）放回背包，其他人直接销毁。
     */
    public static boolean interceptDrop(PlayerEntity player, ItemStack stack) {
        if (!WitherGoheiInventoryRules.isGohei(stack)) {
            return false;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (GiveCommandDropScope.isCosmeticCopy(stack)) {
                return true;
            }
            ItemStack copy = stack.copy();
            stack.setCount(0);
            if (serverPlayer.isAlive()
                    && (mayHold(serverPlayer) || !OffMatchUse.isMatchParticipant(serverPlayer))) {
                PlayerInventory inventory = serverPlayer.getInventory();
                int slot = placementSlot(SparkFactionSecondRowCompat.isShown(),
                        index -> inventory.getStack(index).isEmpty());
                if (slot != NO_SLOT) {
                    inventory.setStack(slot, copy);
                } else {
                    stack.setCount(copy.getCount());
                }
                inventory.markDirty();
            }
        }
        return true;
    }

    /**
     * The leftmost empty hotbar slot, else (when shown) the first empty second-row slot, else the first empty hidden
     * main slot; never the offhand or armor.
     * 最左侧空快捷栏位，否则（显示时）第二行第一个空栏位，否则第一个空的隐藏主背包栏位；从不放入副手或盔甲栏。
     */
    static int placementSlot(boolean secondRowShown, IntPredicate emptySlot) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (emptySlot.test(slot)) {
                return slot;
            }
        }
        if (secondRowShown) {
            for (int slot = SparkFactionSecondRowCompat.SECOND_ROW_START;
                 slot < SparkFactionSecondRowCompat.SECOND_ROW_END; slot++) {
                if (emptySlot.test(slot)) {
                    return slot;
                }
            }
        }
        for (int slot = PlayerInventory.getHotbarSize(); slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (emptySlot.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    private static boolean isShown(int slot) {
        return PlayerInventory.isValidHotbarIndex(slot)
                || SparkFactionSecondRowCompat.isShown() && SparkFactionSecondRowCompat.isSecondRowSlot(slot);
    }

    private static void finish(ServerPlayerEntity player, boolean changed) {
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
