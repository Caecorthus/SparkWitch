package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Owns assignment, cleanup, and one-copy restoration of Black Raven's starting items (blade, ledger, Raven Mask), plus
 * the police revolver it grants on assignment.
 * 负责黑羽鸦开局物品（羽刃、账本、鸦羽假面）的发放、清理与单份补回，以及分配时发放的警察左轮。
 */
public final class BlackRavenLoadoutService {
    private BlackRavenLoadoutService() {
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (!BlackRavenRules.isBlackRaven(role)) {
            return;
        }
        removeOwnedItems(player);
        // Police kit (plan v2 §3.1): a Wathe revolver only when none is held anywhere (a forced re-assign has already
        // returned the stashed one). At round start this runs from RoleAssigned inside initializeGame, before Wathe
        // writes its per-Item 60 s revolver cooldown, so the grant shares it like every other police revolver. The
        // plain insertStack keeps SparkTraits' Impostor free-gun refusal, which is deliberately not bypassed.
        // 警察套装（计划 v2 §3.1）：只有身上任何位置都没有左轮时才发放 Wathe 左轮（强制重新指定时暂存的左轮已先归还）。
        // 开局时本方法在 initializeGame 内的 RoleAssigned 中执行，早于 Wathe 按物品写入的 60 秒左轮冷却，因此与其他警察的
        // 左轮共享该冷却。普通 insertStack 保留 SparkTraits 对内鬼的免费枪械拒绝，刻意不绕过。
        if (BlackRavenPoliceRules.grantsStartingRevolver(holdsRevolver(player))) {
            player.getInventory().insertStack(new ItemStack(WatheItems.REVOLVER));
        }
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

    /** Live inventory (main, armor, offhand) and the cursor. / 当前背包（主栏、盔甲、副手）与光标。 */
    static boolean holdsRevolver(ServerPlayerEntity player) {
        return player.getInventory().contains(stack -> stack.isOf(WatheItems.REVOLVER))
                || (player.currentScreenHandler != null
                && player.currentScreenHandler.getCursorStack().isOf(WatheItems.REVOLVER));
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
