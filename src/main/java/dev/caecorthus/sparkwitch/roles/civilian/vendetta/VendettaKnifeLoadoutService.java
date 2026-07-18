package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Grants, restores, deduplicates, and terminally removes the bound revenge knife. */
public final class VendettaKnifeLoadoutService {
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
            for (ServerPlayerEntity player : world.getPlayers()) {
                reconcile(player);
            }
        });
    }

    public static void initializeForPromotion(ServerPlayerEntity player) {
        removeAll(player);
        reconcile(player);
    }

    public static void reconcile(ServerPlayerEntity player) {
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(player);
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        boolean entitled = component.isActive()
                && component.isKnifeAvailable()
                && !player.isSpectator()
                && game.isRole(player, SparkWitchRoles.vendetta());
        if (!entitled && !containsKnife(player)) {
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
            ItemStack knife = new ItemStack(SparkWitchItems.vendettaKnife());
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
