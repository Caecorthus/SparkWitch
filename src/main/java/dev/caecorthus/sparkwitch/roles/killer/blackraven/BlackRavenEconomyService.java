package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsBlackRavenBridge;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Black Raven police economy (owner, 2026-10-25): +{@link BlackRavenPoliceRules#TASK_MONEY_REWARD} per completed task,
 * like the Judge, Seeker and Control Expert, and a visible balance while undisguised. SparkStrength and Wathe pay new
 * police roles nothing, so SparkWitch owns this income. It always lands in the Black Raven's own wallet: the live
 * wallet while undisguised, the stashed Raven wallet while disguised, never a disguise wallet. A disguise's own task
 * income ({@code BlackRavenDisguiseEconomy}) is separate and stacks.
 * 黑羽鸦警察经济（所有者 2026-10-25）：与法官、搜寻者、控场专家一样，每完成一个任务
 * +{@link BlackRavenPoliceRules#TASK_MONEY_REWARD}，未伪装时金钱可见。SparkStrength 与 Wathe 不给新警察职业发钱，
 * 因此该收入由 SparkWitch 负责。它总是进入黑羽鸦自己的钱包：未伪装时为当前钱包，伪装时为存档中的黑羽鸦钱包，
 * 从不进入伪装钱包。伪装身份自己的任务收入（{@code BlackRavenDisguiseEconomy}）独立发放并叠加。
 */
public final class BlackRavenEconomyService {
    private static boolean registered;

    private BlackRavenEconomyService() {
    }

    /** TaskComplete and CanSeeMoney only. / 仅注册任务完成与金钱可见。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(BlackRavenEconomyService::canSeeMoney);
    }

    static void onTaskComplete(ServerPlayerEntity player) {
        if (!canEarn(player)
                || !BlackRavenPoliceRules.receivesTaskMoney(SparkTraitsBlackRavenBridge.isImpostor(player))) {
            return;
        }
        if (BlackRavenDisguiseService.routesKillerIncome(player)) {
            BlackRavenDisguiseService.creditRavenWallet(player, BlackRavenPoliceRules.TASK_MONEY_REWARD);
        } else if (!BlackRavenActingRole.isDisguised(player)) {
            PlayerShopComponent.KEY.get(player).addToBalance(BlackRavenPoliceRules.TASK_MONEY_REWARD);
        }
        // Disguised without a bound Raven stash: pay nothing rather than the disguise wallet.
        // 伪装中却没有已绑定的黑羽鸦存档：不发放，也不进入伪装钱包。
    }

    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return BlackRavenPoliceRules.earnsTaskMoney(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player), player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player));
    }

    /**
     * ALLOW for an undisguised raw Raven; null while disguised, so the disguise phase
     * ({@code BlackRavenDisguiseClientState}) keeps answering for the acting identity.
     * 未伪装的原始黑羽鸦返回 ALLOW；伪装时返回 null，由伪装阶段（{@code BlackRavenDisguiseClientState}）按扮演身份回答。
     */
    static @Nullable CanSeeMoney.Result moneyVisibilityResult(@Nullable Role rawRole, boolean disguised) {
        return BlackRavenRules.isBlackRaven(rawRole) && !disguised ? CanSeeMoney.Result.ALLOW : null;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        return moneyVisibilityResult(GameWorldComponent.KEY.get(player.getWorld()).getRole(player),
                BlackRavenActingRole.isDisguised(player));
    }
}
