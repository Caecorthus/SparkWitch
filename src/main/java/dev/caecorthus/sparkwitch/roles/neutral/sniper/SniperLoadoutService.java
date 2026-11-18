package dev.caecorthus.sparkwitch.roles.neutral.sniper;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Cooldowns;
import dev.caecorthus.sparkwitch.item.m1903a4.M1903A4Gunplay;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Sniper kit at round start: the M1903A4 in place of the Corrupt Cop's revolver. No master key is issued (owner
 * 2026-11-18); the tablet is issued by SparkStrength once the role is registered on the police network. Items drop at
 * the player's feet if the inventory is full, so nothing is lost. The 60 s opening lock is the holder's rifle item
 * cooldown, written like the AXMC's ({@link M1903A4Cooldowns#roundStart}), so it also covers a rifle that dropped.
 * 狙击手开局装备：以 M1903A4 替代黑警的左轮。不发放万能钥匙（所有者 2026-11-18）；平板在职业注册进警察网络后由
 * SparkStrength 发放。背包已满时物品掉在脚下，不会丢失。开局 60 秒锁定是持有者的步枪物品冷却，写法与 AXMC 相同
 * （{@link M1903A4Cooldowns#roundStart}），因此掉在脚下的步枪同样受其约束。
 */
public final class SniperLoadoutService {
    private static boolean registered;

    private SniperLoadoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grant(serverWorld, game);
            }
        });
    }

    static void grant(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!SniperRules.isSniperRole(game.getRole(player)) || game.isPlayerDead(player.getUuid())) {
                continue;
            }
            PlayerInventory inventory = player.getInventory();
            if (!inventory.contains(stack -> stack.isOf(SparkWitchItems.m1903a4()))) {
                inventory.offerOrDrop(new ItemStack(SparkWitchItems.m1903a4()));
            }
            M1903A4Gunplay.reset(player);
            M1903A4Cooldowns.roundStart(player);
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
