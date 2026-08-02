package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps Saint's good-role coin economy scoped to that exact role.
 * 将圣徒的好人金币初始化、任务收益与可见性严格限制在该职业内。
 */
public final class SaintEconomyService {
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;
    private static boolean registered;

    private SaintEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CanSeeMoney.EVENT.register(SaintEconomyService::canSeeMoney);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (shouldInitializeMoney(role)) {
            PlayerShopComponent.KEY.get(player).setBalance(INITIAL_MONEY);
        }
    }

    public static void onTaskComplete(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (earnsTaskMoney(role)) {
            PlayerShopComponent.KEY.get(player).addToBalance(TASK_MONEY_REWARD);
        }
    }

    static boolean shouldInitializeMoney(@Nullable Role role) {
        return SaintRules.isSaint(role);
    }

    static boolean earnsTaskMoney(@Nullable Role role) {
        return SaintRules.isSaint(role);
    }

    static CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return SaintRules.isSaint(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    private static CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        return moneyVisibilityResult(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
