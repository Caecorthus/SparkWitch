package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The marked duel knife ({@code sparkwitch:duel_knife}). Only that stack is restricted: it may stab only the Knight,
 * and only in the hands of the duel target. A player's own Wathe knife is never restricted. The issued knife never
 * outlives the duel: every marked stack is confiscated when it ends.
 * 带标记的决斗小刀（{@code sparkwitch:duel_knife}）。只有该物品堆受限：只能刺骑士，且只能由决斗对手使用。
 * 玩家自己的 Wathe 小刀从不受限。发出的小刀不会在决斗结束后残留：决斗结束时收回所有带标记的物品堆。
 */
public final class KnightDuelKnives {
    private KnightDuelKnives() {
    }

    public static boolean isDuelKnife(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && Boolean.TRUE.equals(stack.get(SparkWitchDataComponents.DUEL_KNIFE));
    }

    /**
     * The knife a stab is made with, the same way on both sides: the stack being used when it is a knife (vanilla
     * {@code stopUsingItem} keeps it active through {@code KnifeItem.onStoppedUsing}, where the client ray runs), else
     * the main-hand knife, else the off hand, as Wathe's {@code use} picks hands. The server receives the stab after
     * the release packet cleared the active item, so it falls through to the hands.
     * 一次刺击所用的小刀，双端取法一致：正在使用的物品若是小刀则取它（原版 {@code stopUsingItem} 在
     * {@code KnifeItem.onStoppedUsing} 期间保持其激活，客户端射线即在此运行）；否则取主手小刀，再否则取副手，与 Wathe
     * {@code use} 的选手顺序一致。服务端收到刺击时松开数据包已清除激活物品，因此落到双手判断。
     */
    public static ItemStack stabKnife(PlayerEntity player) {
        ItemStack active = player.getActiveItem();
        if (active.isOf(WatheItems.KNIFE)) {
            return active;
        }
        ItemStack main = player.getMainHandStack();
        return main.isOf(WatheItems.KNIFE) ? main : player.getOffHandStack();
    }

    /** Whether a marked-knife stab by {@code attacker} on {@code victim} is allowed. / 带标记小刀的这次刺击是否允许。 */
    public static boolean allowsMarkedStab(KnightDuelWorldComponent duel, PlayerEntity attacker,
                                           @Nullable UUID victim) {
        return duel.isTarget(attacker.getUuid()) && duel.isKnight(victim);
    }

    /**
     * The duel's UI lock leaves only the hotbar and off hand reachable until it ends, so a weapon in an upper row is
     * swapped into the hotbar. True when the player can now reach {@code item}.
     * 决斗的界面锁使玩家在决斗结束前只能用到快捷栏与副手，因此上排的武器会被换到快捷栏。玩家现在能用到 {@code item} 时返回 true。
     */
    static boolean moveIntoReach(ServerPlayerEntity player, Item item) {
        PlayerInventory inventory = player.getInventory();
        if (player.getOffHandStack().isOf(item)) {
            return true;
        }
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return true;
            }
        }
        for (int slot = PlayerInventory.getHotbarSize(); slot < inventory.main.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(item)) {
                int hotbarSlot = inventory.getSwappableHotbarSlot();
                inventory.setStack(slot, inventory.getStack(hotbarSlot));
                inventory.setStack(hotbarSlot, stack);
                inventory.markDirty();
                player.currentScreenHandler.sendContentUpdates();
                return true;
            }
        }
        return false;
    }

    /**
     * Issues the marked knife to a target with no knife in reach, always into the hotbar. With a full hotbar it goes
     * into the selected slot, so it is in hand at once; the item it displaces moves to a free upper slot, then an empty
     * off hand, or is dropped at the target's feet as {@code VendettaKnifeLoadoutService} does, never deleted.
     * 为够不到小刀的对手发放带标记小刀，一定放入快捷栏。快捷栏已满时放进当前选中的格子，使其立即在手；被替换的物品移到
     * 上排空位，其次是空着的副手，否则像 {@code VendettaKnifeLoadoutService} 一样掉落在对手脚下，绝不删除。
     */
    static void issue(ServerPlayerEntity target) {
        if (moveIntoReach(target, WatheItems.KNIFE)) {
            return;
        }
        ItemStack knife = new ItemStack(WatheItems.KNIFE);
        knife.set(SparkWitchDataComponents.DUEL_KNIFE, true);
        PlayerInventory inventory = target.getInventory();
        int empty = inventory.getEmptySlot();
        if (PlayerInventory.isValidHotbarIndex(empty)) {
            inventory.setStack(empty, knife);
        } else {
            ItemStack displaced = inventory.getStack(inventory.selectedSlot);
            inventory.setStack(inventory.selectedSlot, knife);
            if (!target.giveItemStack(displaced)) {
                if (target.getOffHandStack().isEmpty()) {
                    target.setStackInHand(Hand.OFF_HAND, displaced);
                } else {
                    target.dropItem(displaced, false, true);
                }
            }
        }
        inventory.markDirty();
        target.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Removes every marked knife the server can reach: each online player's inventory, cursor and open container
     * slots, and every item entity in every world.
     * 收回服务端能触及的所有带标记小刀：每名在线玩家的背包、光标与已打开容器的槽位，以及所有世界中的物品实体。
     */
    static void confiscateEverywhere(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            confiscate(player);
        }
        for (ServerWorld world : server.getWorlds()) {
            for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, entity -> isDuelKnife(entity.getStack()))) {
                item.discard();
            }
        }
    }

    /** Removes the player's marked knives (also used for a player leaving or joining). / 移除该玩家的带标记小刀。 */
    static void confiscate(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isDuelKnife(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        changed |= confiscate(player.currentScreenHandler, inventory);
        if (player.playerScreenHandler != player.currentScreenHandler) {
            changed |= confiscate(player.playerScreenHandler, inventory);
        }
        if (changed) {
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    private static boolean confiscate(ScreenHandler handler, PlayerInventory inventory) {
        boolean changed = false;
        if (isDuelKnife(handler.getCursorStack())) {
            handler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        // Slots of other inventories (an open container, the 2x2 crafting grid); the player's own are done above.
        // 其他物品栏的槽位（已打开的容器、2x2 合成格）；玩家自己的槽位已在上面处理。
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory && isDuelKnife(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
    }
}
