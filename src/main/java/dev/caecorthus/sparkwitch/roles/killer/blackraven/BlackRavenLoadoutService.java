package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Owns assignment, cleanup, and one-copy restoration of Black Raven's two starting items. */
public final class BlackRavenLoadoutService {
    private BlackRavenLoadoutService() {
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (!BlackRavenRules.isBlackRaven(role)) {
            return;
        }
        removeOwnedItems(player);
        player.giveItemStack(new ItemStack(SparkWitchItems.featherBlade()));
        player.getItemCooldownManager().set(SparkWitchItems.featherBlade(), BlackRavenRules.FEATHER_COOLDOWN_TICKS);
        player.giveItemStack(new ItemStack(SparkWitchItems.blackRavenLedger()));
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void restoreLedgerIfNeeded(ServerPlayerEntity player) {
        UUID currentMatch = BlackRavenMatch.currentId();
        BlackRavenPerceptionPlayerComponent component = BlackRavenPerceptionPlayerComponent.KEY.get(player);
        if (currentMatch == null || !currentMatch.equals(component.matchId())
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))) {
            return;
        }

        boolean found = false;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (!player.getInventory().getStack(slot).isOf(SparkWitchItems.blackRavenLedger())) {
                continue;
            }
            if (!found) {
                found = true;
            } else {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (player.currentScreenHandler.getCursorStack().isOf(SparkWitchItems.blackRavenLedger())) {
            if (found) {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            } else {
                found = true;
            }
        }
        if (!found) {
            player.giveItemStack(new ItemStack(SparkWitchItems.blackRavenLedger()));
            changed = true;
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    public static void removeOwnedItems(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(SparkWitchItems.featherBlade()) || stack.isOf(SparkWitchItems.blackRavenLedger())) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (player.currentScreenHandler.getCursorStack().isOf(SparkWitchItems.blackRavenLedger())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (var slot : player.currentScreenHandler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isOf(SparkWitchItems.blackRavenLedger())) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    public static void removeLedger(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(SparkWitchItems.blackRavenLedger())) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (player.currentScreenHandler.getCursorStack().isOf(SparkWitchItems.blackRavenLedger())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
