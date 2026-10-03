package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
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
 * Seeker economy (Q9): starting balance 0 (written by {@link SeekerLoadoutService#grant}; the tablet the old 50 paid for
 * is now issued free by SparkStrength), +50 per completed task, money always visible. SparkStrength pays nothing to new police roles, so SparkWitch owns all of the Seeker's income.
 * No task pay for a SparkTraits Impostor or an unknown answer ({@link SparkTraitsSeekerBridge#isImpostorOrUnknown}:
 * absent SparkTraits reads as "not impostor", a present build that cannot answer reads as "unknown"), following the
 * Control Expert's fail-closed precedent; SparkTraits pays an Impostor's task income itself.
 * 搜寻者经济（Q9）：开局余额 0（由 {@link SeekerLoadoutService#grant} 写入；原先 50 所购买的平板现由 SparkStrength 免费发放），
 * 每完成一个任务 +50，金钱始终可见。
 * SparkStrength 不给新警察职业发钱，因此搜寻者的全部收入由 SparkWitch 负责。SparkTraits 内鬼或无法判定时
 * 不发任务钱（未安装视为非内鬼，已安装但无法回答视为未知），沿用控场专家“未知即拒绝”的先例；内鬼的任务收入由 SparkTraits 自行支付。
 */
public final class SeekerEconomyService {
    private static boolean registered;

    private SeekerEconomyService() {
    }

    /** TaskComplete and CanSeeMoney only; the starting balance is part of the grant. / 仅注册任务完成与金钱可见。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(SeekerEconomyService::canSeeMoney);
    }

    /** Round-start balance; overwrites, never adds. / 开局余额；覆盖写入，不累加。 */
    static void initialize(ServerPlayerEntity player) {
        PlayerShopComponent.KEY.get(player).setBalance(SeekerRules.INITIAL_MONEY);
    }

    static void onTaskComplete(ServerPlayerEntity player) {
        if (canEarn(player) && receivesTaskMoney(SparkTraitsSeekerBridge.isImpostorOrUnknown(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(SeekerRules.TASK_MONEY_REWARD);
        }
    }

    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return canEarn(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player), player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player));
    }

    /** Same eligibility as the Judge and the Control Expert. / 与法官、控场专家相同的资格判定。 */
    static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                           boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && SeekerRules.isSeeker(role)
                && !spectator && !creative && !wraithRestricted;
    }

    /** Q5-b: no pay for an Impostor or an unknown answer. / Q5-b：内鬼或无法判定时不发钱。 */
    static boolean receivesTaskMoney(boolean impostorOrUnknown) {
        return !impostorOrUnknown;
    }

    static @Nullable CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return SeekerRules.isSeeker(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        return moneyVisibilityResult(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
