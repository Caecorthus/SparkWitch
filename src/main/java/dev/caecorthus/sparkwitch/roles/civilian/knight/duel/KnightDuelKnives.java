package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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

    /** True when the player holds any Wathe knife, marked or not. / 玩家是否持有任意 Wathe 小刀（无论是否带标记）。 */
    static boolean carriesKnife(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(WatheItems.KNIFE)) {
                return true;
            }
        }
        return player.currentScreenHandler.getCursorStack().isOf(WatheItems.KNIFE);
    }

    /**
     * Issues the marked knife to a target without one. With no free slot (giveItemStack fails) the knife still goes
     * into the selected hotbar slot, so it is in hand at once; the item it displaces moves to an empty off hand, or is
     * dropped at the target's feet as {@code VendettaKnifeLoadoutService} does, never deleted.
     * 为没有小刀的对手发放带标记小刀。没有空位（giveItemStack 失败）时仍把小刀放进当前快捷栏，使其立即在手；被替换的物品
     * 移到空着的副手，否则像 {@code VendettaKnifeLoadoutService} 一样掉落在对手脚下，绝不删除。
     */
    static void issue(ServerPlayerEntity target) {
        if (carriesKnife(target)) {
            return;
        }
        ItemStack knife = new ItemStack(WatheItems.KNIFE);
        knife.set(SparkWitchDataComponents.DUEL_KNIFE, true);
        if (!target.giveItemStack(knife)) {
            PlayerInventory inventory = target.getInventory();
            ItemStack displaced = inventory.getStack(inventory.selectedSlot);
            inventory.setStack(inventory.selectedSlot, knife);
            if (!displaced.isEmpty()) {
                if (target.getOffHandStack().isEmpty()) {
                    target.setStackInHand(Hand.OFF_HAND, displaced);
                } else {
                    target.dropItem(displaced, false, true);
                }
            }
        }
        target.getInventory().markDirty();
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
