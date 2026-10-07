package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owned by WP1: starting money, task pay and the uncapped passive income (Q1/Q1+, D11). Called once from
 * {@link UsecFeatureService#register()}. The server is the only writer of the balance; the client only reads
 * {@code CanSeeMoney}.
 * <ul>
 *   <li>Start: the balance is overwritten with {@link UsecRules#INITIAL_MONEY} for every living final USEC at
 *   {@code ON_FINISH_INITIALIZE}, in a phase after the default one (after SparkTraits Conscience compensation has
 *   settled final roles), like the Seeker. The round-start loadout never writes the balance.</li>
 *   <li>Tasks: +{@link UsecRules#TASK_REWARD}, never for a SparkTraits Impostor or an unknown answer (SparkTraits pays
 *   an Impostor's task income itself), Control Expert semantics.</li>
 *   <li>Passive: +{@link UsecRules#PASSIVE_INCOME} on the same world tick as Wathe's killer ticker
 *   ({@code getTime() % interval == 0}) with no balance cap, to every earning USEC (Impostor or not: no other system
 *   pays a USEC passive income).</li>
 * </ul>
 * 归 WP1 所有：初始金币、任务收入与不设上限的被动收入（Q1/Q1+、D11）。由 {@link UsecFeatureService#register()} 调用一次。
 * 只有服务端写入余额；客户端只读取 {@code CanSeeMoney}。
 * 开局：在 {@code ON_FINISH_INITIALIZE} 默认阶段之后的阶段（SparkTraits 良心补偿已确定最终身份后），与搜寻者一样，
 * 把每名存活的最终 USEC 的余额覆盖为 {@link UsecRules#INITIAL_MONEY}；开局装备流程从不写余额。
 * 任务：+{@link UsecRules#TASK_REWARD}，SparkTraits 内鬼或无法判定时不发（内鬼任务收入由 SparkTraits 自行支付），沿用控场专家语义。
 * 被动：与 Wathe 杀手计时器同一世界刻（{@code getTime() % interval == 0}）发放 +{@link UsecRules#PASSIVE_INCOME}，
 * 不设余额上限，发给每名可获得收入的 USEC（无论是否内鬼：没有其他系统给 USEC 发被动收入）。
 */
public final class UsecEconomyService {
    /**
     * USEC's own ON_FINISH_INITIALIZE phase, ordered after the default phase.
     * USEC 自有的 ON_FINISH_INITIALIZE 阶段，排在默认阶段之后。
     */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("usec_economy_finish_initialize");

    private static boolean registered;

    private UsecEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(UsecEconomyService::canSeeMoney);
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                initializeBalances(serverWorld, game);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(UsecEconomyService::tickPassiveIncome);
    }

    // ---- Start ----

    static void initializeBalances(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // The round is still STARTING here, so the running-state helper would reject every player.
            // 此时对局仍处于 STARTING，运行态判定会拒绝所有玩家。
            if (receivesStartingBalance(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player))) {
                initialize(player);
            }
        }
    }

    /** Only a living final USEC with a role. / 只有拥有身份且存活的最终 USEC。 */
    static boolean receivesStartingBalance(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && UsecRules.isUsec(finalRole);
    }

    /** Round-start balance; overwrites, never adds. / 开局余额；覆盖写入，不累加。 */
    static void initialize(ServerPlayerEntity player) {
        PlayerShopComponent.KEY.get(player).setBalance(UsecRules.INITIAL_MONEY);
    }

    // ---- Tasks ----

    static void onTaskComplete(ServerPlayerEntity player) {
        if (canEarn(player) && receivesTaskMoney(UsecEconomyTraitsProbe.isSparkTraitsLoaded(),
                UsecEconomyTraitsProbe.isImpostor(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(UsecRules.TASK_REWARD);
        }
    }

    /**
     * SparkTraits absent → pay; present → pay only on a confirmed non-Impostor (unknown fails closed).
     * 未安装 SparkTraits → 发放；已安装 → 仅在确认不是内鬼时发放（无法确认时不发放）。
     */
    static boolean receivesTaskMoney(boolean traitsLoaded, @Nullable Boolean impostor) {
        return !traitsLoaded || Boolean.FALSE.equals(impostor);
    }

    // ---- Passive income ----

    /**
     * Pays every earning USEC on Wathe's passive-money ticks. END_WORLD_TICK sees the same {@code getTime()} as
     * Wathe's game loop in that world tick, so both payouts land on the same tick. Uses {@code addToBalance}, so income
     * observers (e.g. SparkTraits Snowball) see it as ordinary income; never compared with a balance cap.
     * 在 Wathe 被动金币刻给每名可获得收入的 USEC 发钱。END_WORLD_TICK 与同一世界刻内 Wathe 游戏循环读到的
     * {@code getTime()} 相同，因此两者在同一刻发放。使用 {@code addToBalance}，收入观察者（如 SparkTraits 滚雪球）
     * 视其为普通收入；从不与余额上限比较。
     */
    static void tickPassiveIncome(ServerWorld world) {
        int income = passiveIncomeAt(world.getTime());
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

    /**
     * Coins due on this world tick: {@link UsecRules#PASSIVE_INCOME} when the time is a multiple of
     * {@link UsecRules#PASSIVE_INCOME_INTERVAL_TICKS} (Wathe's {@code PASSIVE_MONEY_TICKER} alignment), else 0.
     * Independent of the balance: there is no cap.
     * 本世界刻应发金币：时间是 {@link UsecRules#PASSIVE_INCOME_INTERVAL_TICKS} 的整数倍时为
     * {@link UsecRules#PASSIVE_INCOME}（与 Wathe {@code PASSIVE_MONEY_TICKER} 对齐），否则为 0。与余额无关：不设上限。
     */
    static int passiveIncomeAt(long worldTime) {
        return worldTime % UsecRules.PASSIVE_INCOME_INTERVAL_TICKS == 0 ? UsecRules.PASSIVE_INCOME : 0;
    }

    // ---- Shared eligibility ----

    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return canEarn(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player), player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player));
    }

    /**
     * Running round, playing and alive, exactly USEC, survival, not a restricted Wraith (Seeker and Control Expert
     * eligibility). / 对局进行中、参与且存活、身份恰为 USEC、生存模式、不是受限冤魂（与搜寻者、控场专家相同）。
     */
    static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                           boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && UsecRules.isUsec(role)
                && !spectator && !creative && !wraithRestricted;
    }

    // ---- Visibility ----

    static @Nullable CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return UsecRules.isUsec(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        return moneyVisibilityResult(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
