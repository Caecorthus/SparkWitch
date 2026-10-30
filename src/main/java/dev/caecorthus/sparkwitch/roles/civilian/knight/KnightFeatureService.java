package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Knight round-start loadout: a revolver, a derringer and the cross sword, in that order. Granted once on final
 * roles at {@code ON_FINISH_INITIALIZE}, after Wathe's own per-role item grants.
 * 骑士开局装备：依次发放左轮手枪、德林加手枪和十字剑。在最终身份确定后的 {@code ON_FINISH_INITIALIZE}
 * 发放一次，位于 Wathe 自带的按职业发物品之后。
 */
public final class KnightFeatureService {
    private static boolean registered;

    private KnightFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStartLoadouts(serverWorld, game);
            }
        });
    }

    static void grantRoundStartLoadouts(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // The game is still STARTING here; the running-state helper would reject every player.
            // 此时对局仍处于 STARTING，运行态判定会拒绝所有玩家。
            if (receivesRoundStartLoadout(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player))) {
                grantRoundStartLoadout(player);
            }
        }
    }

    static boolean receivesRoundStartLoadout(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && KnightRules.isKnight(finalRole);
    }

    private static void grantRoundStartLoadout(ServerPlayerEntity player) {
        player.getInventory().insertStack(new ItemStack(WatheItems.REVOLVER));
        // Starts spent ("0 bullets"): Wathe reloads a used derringer on the owner's next kill.
        // 开局为空膛（“0 发子弹”）：Wathe 会在持有者下次击杀时重新装填已使用的德林加。
        ItemStack derringer = new ItemStack(WatheItems.DERRINGER);
        derringer.set(WatheDataComponentTypes.USED, true);
        player.getInventory().insertStack(derringer);
        player.getInventory().insertStack(new ItemStack(SparkWitchItems.crossSword()));
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }
}
