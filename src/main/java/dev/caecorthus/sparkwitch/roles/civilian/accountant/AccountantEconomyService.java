package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsTaskIncomeBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * 会计 income: +{@link AccountantRules#TASK_REWARD} per task and +{@link AccountantRules#PASSIVE_INCOME} every
 * {@link AccountantRules#PASSIVE_INCOME_INTERVAL_TICKS} world ticks, only while {@link #canEarn} holds (so a dead,
 * swallowed or Wraith Accountant earns nothing). Both use {@code addToBalance}, so income observers (SparkFactionAPI's
 * income record, SparkTraits Snowball) see ordinary income. Task pay skips a SparkTraits Impostor, whose task income
 * SparkTraits pays itself; the passive income has no other payer, so it is paid either way (USEC precedent).
 * 会计收入：每个任务 +{@link AccountantRules#TASK_REWARD}，另每 {@link AccountantRules#PASSIVE_INCOME_INTERVAL_TICKS}
 * 世界刻 +{@link AccountantRules#PASSIVE_INCOME}，只在 {@link #canEarn} 成立时发放（死亡、被吞或成为冤魂的会计不再获得收入）。
 * 两者都用 {@code addToBalance}，收入观察者（SparkFactionAPI 收入记录、SparkTraits 滚雪球）视其为普通收入。任务收入不发给
 * SparkTraits 内鬼（内鬼任务收入由 SparkTraits 自行支付）；被动收入没有其他来源，因此照常发放（沿用 USEC）。
 */
final class AccountantEconomyService {
    private static boolean registered;

    private AccountantEconomyService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        ServerTickEvents.END_WORLD_TICK.register(AccountantEconomyService::tickPassiveIncome);
    }

    static void onTaskComplete(ServerPlayerEntity player) {
        if (canEarn(player) && AccountantRules.receivesTaskMoney(SparkTraitsTaskIncomeBridge.isImpostor(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(AccountantRules.TASK_REWARD);
        }
    }

    /**
     * END_WORLD_TICK reads the same {@code getTime()} as Wathe's game loop in that tick, so the payout is aligned with
     * Wathe's own passive-money ticks. / END_WORLD_TICK 与同一刻内 Wathe 游戏循环读到的 {@code getTime()} 相同，
     * 因此发放与 Wathe 自己的被动金币刻对齐。
     */
    private static void tickPassiveIncome(ServerWorld world) {
        int income = AccountantRules.passiveIncomeAt(world.getTime());
        if (income <= 0) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (canEarn(player)) {
                PlayerShopComponent.KEY.get(player).addToBalance(income);
            }
        }
    }

    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        // Cheap role test first: this runs for every player on each passive tick. / 先做廉价的职业判断：每个被动刻都对所有玩家执行。
        if (!AccountantRules.isAccountant(role)) {
            return false;
        }
        return AccountantRules.canEarn(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), role, player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player));
    }
}
