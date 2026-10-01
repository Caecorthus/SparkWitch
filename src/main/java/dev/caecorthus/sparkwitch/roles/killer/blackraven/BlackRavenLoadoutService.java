package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Owns assignment, cleanup, and one-copy restoration of Black Raven's starting items (blade, ledger, Raven Mask). */
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
        player.giveItemStack(new ItemStack(SparkWitchItems.blackRavenMask()));
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Name kept for compatibility: keeps exactly one Raven Mask for a bound, alive raw Black Raven at all times,
     * and restores the ledger only while not disguised, because a disguise keeps the ledger in the stashed Raven
     * set until the Raven reverts.
     * 保留原方法名：已绑定且存活的真实黑羽鸦始终恰好持有一个鸦羽假面；仅在未伪装时补回账本，
     * 因为伪装期间账本随黑羽鸦物品存放在存档中，恢复为黑羽鸦时才取回。
     */
    public static void restoreLedgerIfNeeded(ServerPlayerEntity player) {
        UUID currentMatch = BlackRavenMatch.currentId();
        BlackRavenPerceptionPlayerComponent component = BlackRavenPerceptionPlayerComponent.KEY.get(player);
        if (currentMatch == null || !currentMatch.equals(component.matchId())
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))) {
            return;
        }

        boolean changed = keepExactlyOne(player, SparkWitchItems.blackRavenMask());
        if (!isDisguised(player)) {
            changed |= keepExactlyOne(player, SparkWitchItems.blackRavenLedger());
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
            if (stack.isOf(SparkWitchItems.featherBlade()) || BlackRavenInventoryRules.isBound(stack)) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (BlackRavenInventoryRules.isBound(player.currentScreenHandler.getCursorStack())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        for (var slot : player.currentScreenHandler.slots) {
            ItemStack stack = slot.getStack();
            if (BlackRavenInventoryRules.isBound(stack)) {
                slot.setStack(ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    /** Name kept: removes both bound items (ledger and Raven Mask) from a non-Raven. / 保留原名：移除非黑羽鸦身上的两件绑定物品。 */
    public static void removeLedger(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (BlackRavenInventoryRules.isBound(player.getInventory().getStack(slot))) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (BlackRavenInventoryRules.isBound(player.currentScreenHandler.getCursorStack())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }

    /** Server truth for "the ledger lives in the Raven stash". / 服务端判定“账本位于黑羽鸦存档中”。 */
    private static boolean isDisguised(ServerPlayerEntity player) {
        return BlackRavenActingRole.isDisguised(player)
                || BlackRavenDisguiseComponent.KEY.get(player).state().acting() != null;
    }

    /** Keeps the first copy (inventory, then cursor), removes extras, and grants one if missing. / 保留首个副本、移除多余副本，缺失时补发一个。 */
    private static boolean keepExactlyOne(ServerPlayerEntity player, Item item) {
        boolean found = false;
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (!player.getInventory().getStack(slot).isOf(item)) {
                continue;
            }
            if (!found) {
                found = true;
            } else {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (player.currentScreenHandler.getCursorStack().isOf(item)) {
            if (found) {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            } else {
                found = true;
            }
        }
        if (!found) {
            player.giveItemStack(new ItemStack(item));
            changed = true;
        }
        return changed;
    }
}
