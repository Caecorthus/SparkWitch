package dev.caecorthus.sparkwitch.roles.neutral.stowaway;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Stowaway opening loadout: exactly one lockpick, no room key and no role letter. Runs after Wathe has handed out both
 * default items, in the {@code ON_FINISH_INITIALIZE} phase (final roles already assigned, round still STARTING).
 * 偷渡客开局装备：恰好一个开锁器，没有房间钥匙与职业信件。在 Wathe 发放完两件默认物品后的
 * {@code ON_FINISH_INITIALIZE} 阶段执行（最终职业已确定，对局仍为 STARTING）。
 */
public final class StowawayLoadoutService {
    private static boolean registered;

    private StowawayLoadoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStart(serverWorld, game);
            }
        });
    }

    static void grantRoundStart(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!StowawayRules.isStowaway(game.getRole(player)) || game.isPlayerDead(player.getUuid())) {
                continue;
            }
            stripDefaultStartingItems(player);
            ensureLockpick(player);
        }
    }

    /** Removes every room key and role letter, including the crafting grid. / 移除所有房间钥匙与职业信件，含合成格。 */
    private static void stripDefaultStartingItems(ServerPlayerEntity player) {
        int removed = player.getInventory().remove(
                stack -> stack.isOf(WatheItems.KEY) || stack.isOf(WatheItems.LETTER),
                Integer.MAX_VALUE,
                player.playerScreenHandler.getCraftingInput()
        );
        if (removed > 0) {
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.onContentChanged(player.getInventory());
        }
    }

    /** Grants one lockpick unless the player already owns one. / 玩家尚未持有时发放一个开锁器。 */
    private static void ensureLockpick(ServerPlayerEntity player) {
        if (hasLockpick(player)) {
            return;
        }
        player.getInventory().offerOrDrop(new ItemStack(WatheItems.LOCKPICK));
        player.currentScreenHandler.sendContentUpdates();
    }

    private static boolean hasLockpick(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(WatheItems.LOCKPICK)) {
                return true;
            }
        }
        return player.currentScreenHandler.getCursorStack().isOf(WatheItems.LOCKPICK);
    }
}
