package dev.caecorthus.sparkwitch.roles.neutral.sniper;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Sniper money: {@link SniperRules#INITIAL_MONEY} at role assignment, +{@link SniperRules#TASK_MONEY_REWARD} per
 * completed task with SparkTraits bonuses stacking on top, and the coin counter visible while alive. Kills pay nothing:
 * no kill payout is registered here.
 * 狙击手金钱：分配职业时为 {@link SniperRules#INITIAL_MONEY}，每完成一个任务 +{@link SniperRules#TASK_MONEY_REWARD}
 *（SparkTraits 加成在其上叠加），存活时金币可见。杀人没有任何收入：这里不注册击杀奖励。
 */
public final class SniperEconomyService {
    private static boolean registered;

    private SniperEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RoleAssigned.EVENT.register(SniperEconomyService::onRoleAssigned);
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(SniperEconomyService::canSeeMoney);
        SniperShopService.register();
    }

    private static void onRoleAssigned(PlayerEntity player, Role role) {
        if (player instanceof ServerPlayerEntity serverPlayer && SniperRules.isSniperRole(role)) {
            PlayerShopComponent.KEY.get(serverPlayer).setBalance(SniperRules.INITIAL_MONEY);
        }
    }

    private static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && SniperRules.isSniperRole(role)
                && !player.isSpectator()
                && !player.isCreative()
                && !SparkWitchApi.isWraithRestricted(player)) {
            PlayerShopComponent.KEY.get(player).addToBalance(SniperRules.TASK_MONEY_REWARD);
        }
    }

    /** Living snipers see the coin counter, dead ones do not (same contract as the Insider). / 存活狙击手金币可见。 */
    private static @Nullable CanSeeMoney.Result canSeeMoney(@Nullable PlayerEntity player) {
        if (player == null) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!SniperRules.isSniperRole(game.getRole(player))) {
            return null;
        }
        return game.isPlayerDead(player.getUuid()) ? CanSeeMoney.Result.DENY : CanSeeMoney.Result.ALLOW;
    }
}
