package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.util.StaggeredSweep;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Grants, restores, deduplicates, and terminally removes the bound revenge knife. */
public final class VendettaKnifeLoadoutService {
    /**
     * Cadence of the world-tick stray sweep for players not entitled to the knife (owner-approved 2026-10-22: a stray
     * knife may stay up to one second on such a player; drops are already refused by the knife mixins). The entitled
     * path still runs every tick.
     * 对无权持刀玩家的世界 tick 残留清理间隔（所有者 2026-10-22 批准：残留刀最多在此类玩家身上停留一秒；丢弃已被
     * 复仇刀 mixin 拒绝）。有权持刀的路径仍每 tick 执行。
     */
    static final int STRAY_SWEEP_INTERVAL_TICKS = 20;
    private static boolean registered;

    private VendettaKnifeLoadoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                VendettaKnifeService.clearPlayer(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> VendettaKnifeService.clearAll());
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long time = world.getTime();
            for (ServerPlayerEntity player : world.getPlayers()) {
                reconcile(player, isStraySweepTick(time, player.getId()));
            }
        });
    }

    /** Each non-entitled player is swept once per interval, offset by entity id. / 每名无权玩家每个间隔按实体 id 错开清理一次。 */
    static boolean isStraySweepTick(long worldTime, int entityId) {
        return StaggeredSweep.isDue(worldTime, entityId, STRAY_SWEEP_INTERVAL_TICKS);
    }

    public static void initializeForPromotion(ServerPlayerEntity player) {
        removeAll(player);
        reconcile(player);
    }

    /** Immediate reconcile, strays included. / 立即校正，包括清理残留。 */
    public static void reconcile(ServerPlayerEntity player) {
        reconcile(player, true);
    }

    private static void reconcile(ServerPlayerEntity player, boolean sweepStrays) {
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(player);
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        boolean entitled = component.isActive()
                && component.isKnifeAvailable()
                && !player.isSpectator()
                && game.isRole(player, SparkWitchRoles.vendetta());
        if (!entitled && (!sweepStrays || !containsKnife(player))) {
            return;
        }

        boolean found = false;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!VendettaKnifeInventoryRules.isKnife(stack)) {
                continue;
            }
            if (entitled && !found) {
                found = true;
            } else {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (VendettaKnifeInventoryRules.isKnife(player.currentScreenHandler.getCursorStack())) {
            if (entitled && !found) {
                found = true;
            } else {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        for (var slot : player.currentScreenHandler.slots) {
            if (slot.inventory != player.getInventory()
                    && VendettaKnifeInventoryRules.isKnife(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (entitled && !found) {
            ItemStack knife = VendettaKnifeStackFactory.create(player);
            if (!player.giveItemStack(knife)) {
                // Guarantee the role-owned knife without deleting a full-inventory player's selected item.
                // 背包已满时仍保证发刀，同时把被占用的当前物品正常丢回世界而不是删除。
                int selectedSlot = player.getInventory().selectedSlot;
                ItemStack displaced = player.getInventory().getStack(selectedSlot);
                player.getInventory().setStack(selectedSlot, knife);
                if (!displaced.isEmpty()) {
                    player.dropItem(displaced, false, true);
                }
            }
            changed = true;
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    public static void removeAll(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (VendettaKnifeInventoryRules.isKnife(player.getInventory().getStack(slot))) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (VendettaKnifeInventoryRules.isKnife(player.currentScreenHandler.getCursorStack())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (var slot : player.currentScreenHandler.slots) {
            if (VendettaKnifeInventoryRules.isKnife(slot.getStack())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    private static boolean containsKnife(ServerPlayerEntity player) {
        if (VendettaKnifeInventoryRules.isKnife(player.currentScreenHandler.getCursorStack())) {
            return true;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (VendettaKnifeInventoryRules.isKnife(player.getInventory().getStack(slot))) {
                return true;
            }
        }
        for (var slot : player.currentScreenHandler.slots) {
            if (VendettaKnifeInventoryRules.isKnife(slot.getStack())) {
                return true;
            }
        }
        return false;
    }
}
