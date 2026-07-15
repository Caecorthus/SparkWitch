package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the Perfumer's task-funded shop economy without changing other roles.
 * 独立管理调香师依靠任务获得金币的商店经济，不改变其他职业。
 */
public final class PerfumerEconomyService {
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = PerfumerRules.TASK_REWARD;
    private static final Identifier PERFUMER_ROLE_ID = Identifier.of(PerfumerRules.ROLE_ID);
    private static boolean registered;

    private PerfumerEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CanSeeMoney.EVENT.register(PerfumerEconomyService::canSeeMoney);
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
        return isPerfumer(role);
    }

    static boolean earnsTaskMoney(@Nullable Role role) {
        return isPerfumer(role);
    }

    static CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return isPerfumer(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    private static CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return moneyVisibilityResult(role);
    }

    private static boolean isPerfumer(@Nullable Role role) {
        return role != null && PERFUMER_ROLE_ID.equals(role.identifier());
    }
}
