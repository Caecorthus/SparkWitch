package dev.caecorthus.sparkwitch.roles.neutral.insider;

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
 * Insider task economy (C4): {@link InsiderRules#INITIAL_MONEY} at role assignment, +{@link InsiderRules#TASK_MONEY_REWARD}
 * per completed task, coin counter visible. SparkStrength pays nothing to the Insider, so SparkWitch owns all of its
 * income; SparkTraits Task Master adds its own bonus on top. Decisions live in {@link InsiderEconomyRules}.
 * 内应任务经济（C4）：分配职业时余额为 {@link InsiderRules#INITIAL_MONEY}，每完成一个任务
 * +{@link InsiderRules#TASK_MONEY_REWARD}，金币可见。SparkStrength 不给内应发钱，因此全部收入由 SparkWitch 负责；
 * SparkTraits 任务大师在此之上自行追加。判定逻辑位于 {@link InsiderEconomyRules}。
 */
public final class InsiderEconomyService {
    private static boolean registered;

    private InsiderEconomyService() {
    }

    /**
     * WP2's hub entry: the Insider's money listeners, then the shop that money is spent in.
     * WP2 的注册入口：内应的金钱监听器，以及花这笔钱的商店。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RoleAssigned.EVENT.register(InsiderEconomyService::onRoleAssigned);
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(InsiderEconomyService::canSeeMoney);
        InsiderShopService.register();
    }

    /** Round-start balance; overwrites, never adds. / 开局余额；覆盖写入，不累加。 */
    private static void onRoleAssigned(PlayerEntity player, Role role) {
        if (player instanceof ServerPlayerEntity serverPlayer && InsiderEconomyRules.startsWithInitialMoney(role)) {
            PlayerShopComponent.KEY.get(serverPlayer).setBalance(InsiderRules.INITIAL_MONEY);
        }
    }

    private static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (InsiderEconomyRules.earnsTaskMoney(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player), player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(InsiderRules.TASK_MONEY_REWARD);
        }
    }

    /**
     * Reads only synced role and death facts, so it answers the same on both sides and already while STARTING: a living
     * Insider sees the coin counter, a dead one does not (see {@link InsiderEconomyRules#moneyVisibility}).
     * 只读取已同步的身份与死亡信息，因此两端结果一致，并且在 STARTING 阶段即可作答：存活内应能看到金币，死亡内应看不到
     * （见 {@link InsiderEconomyRules#moneyVisibility}）。
     */
    private static @Nullable CanSeeMoney.Result canSeeMoney(@Nullable PlayerEntity player) {
        if (player == null) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return InsiderEconomyRules.moneyVisibility(game.getRole(player), game.isPlayerDead(player.getUuid()));
    }
}
