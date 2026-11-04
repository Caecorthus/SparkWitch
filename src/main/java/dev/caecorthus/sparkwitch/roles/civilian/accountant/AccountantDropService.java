package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.doctor4t.wathe.api.event.KillPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Wathe's custom death does not run vanilla's drop-inventory, so a passbook would stay in a spectator's inventory and
 * never be found. This drops it at the death spot instead (owner spec: 死亡时会掉落).
 * Wathe 的自定义死亡不会走原版的掉落流程，存折会留在旁观者的物品栏里、谁也别想捡到。
 * 这里改成在死亡位置直接掉落（所有者规格：死亡时会掉落）。
 */
public final class AccountantDropService {

    private AccountantDropService() {
    }

    public static void register() {
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayerEntity player)
                    || !(player.getWorld() instanceof ServerWorld world)) {
                return;
            }
            if (!AccountantService.isAccountant(player)) {
                return;
            }
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isOf(dev.caecorthus.sparkwitch.SparkWitchItems.accountantPassbook())) {
                    ItemStack dropped = stack.copy();
                    player.getInventory().setStack(slot, ItemStack.EMPTY);
                    world.spawnEntity(new net.minecraft.entity.ItemEntity(
                            world, player.getX(), player.getY() + 0.5, player.getZ(), dropped));
                }
            }
        });
    }
}
