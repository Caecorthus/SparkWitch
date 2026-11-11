package dev.caecorthus.sparkwitch.roles.civilian.bear;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsTaskIncomeBridge;
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
 * BEAR coins, the USEC model (owner 2026-11-05): the balance is overwritten with {@link BearRules#INITIAL_MONEY} for
 * every living final BEAR in BEAR's own {@code ON_FINISH_INITIALIZE} phase (after the default phase, so roles are
 * final), +{@link BearRules#TASK_PAY} per task (never for a SparkTraits Impostor or an unknown answer, through
 * {@link SparkTraitsTaskIncomeBridge}: SparkTraits pays an Impostor's task income itself, the Accountant precedent) and
 * +{@link BearRules#PASSIVE_INCOME} on Wathe's passive-money ticks with no cap (Impostor or not: nothing else pays it).
 * Income goes through {@code addToBalance} so income observers see it; the round-start overwrite is the only
 * {@code setBalance}. {@code CanSeeMoney} answers ALLOW for a living BEAR, so the money HUD shows, and DENY for a dead
 * one. Server is the only writer; the client only reads {@code CanSeeMoney}.
 * BEAR 金币，沿用 USEC 模式（所有者 2026-11-05）：在 BEAR 自有的 {@code ON_FINISH_INITIALIZE} 阶段（排在默认阶段之后，此时职业
 * 已是最终职业）把每名存活的最终 BEAR 的余额覆盖为 {@link BearRules#INITIAL_MONEY}；每个任务 +{@link BearRules#TASK_PAY}
 * （SparkTraits 内鬼或无法判定时不发，经 {@link SparkTraitsTaskIncomeBridge}：内鬼的任务收入由 SparkTraits 自行支付，沿用会计
 * 先例）；在 Wathe 被动金币刻 +{@link BearRules#PASSIVE_INCOME}，不设上限（无论是否内鬼：没有其他系统支付）。收入经
 * {@code addToBalance} 发放，收入观察者都能看到；开局覆盖是唯一的 {@code setBalance}。{@code CanSeeMoney} 对存活的 BEAR 返回
 * ALLOW，使金币 HUD 显示；对死亡的 BEAR 返回 DENY。只有服务端写入；客户端只读取 {@code CanSeeMoney}。
 */
final class BearEconomyService {
    /** BEAR's own ON_FINISH_INITIALIZE phase, after the default one. / BEAR 自有的 ON_FINISH_INITIALIZE 阶段，排在默认阶段之后。 */
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("bear_economy_finish_initialize");

    private static boolean registered;

    private BearEconomyService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(BearEconomyService::canSeeMoney);
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                initializeBalances(serverWorld, game);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(BearEconomyService::tickPassiveIncome);
    }

    // ---- Start ----

    static void initializeBalances(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Still STARTING here, so the running-state helper would reject everyone. / 此时仍为 STARTING，运行态判定会拒绝所有人。
            if (receivesStartingBalance(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player))) {
                PlayerShopComponent.KEY.get(player).setBalance(BearRules.INITIAL_MONEY);
            }
        }
    }

    /** Only a living final BEAR with a role. / 只有拥有身份且存活的最终 BEAR。 */
    static boolean receivesStartingBalance(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && BearRules.isBear(finalRole);
    }

    // ---- Tasks ----

    static void onTaskComplete(ServerPlayerEntity player) {
        if (canEarn(player) && receivesTaskMoney(SparkTraitsTaskIncomeBridge.isImpostor(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(BearRules.TASK_PAY);
        }
    }

    /**
     * Pays only on a confirmed non-Impostor; the bridge answers FALSE without SparkTraits and null when unsure.
     * 仅在确认不是内鬼时发放；未安装 SparkTraits 时桥接返回 FALSE，无法确认时返回 null。
     */
    static boolean receivesTaskMoney(@Nullable Boolean impostor) {
        return Boolean.FALSE.equals(impostor);
    }

    // ---- Passive income ----

    /**
     * END_WORLD_TICK reads the same {@code getTime()} as Wathe's game loop in that tick, so the payout lands on Wathe's
     * own passive-money ticks. / END_WORLD_TICK 与同一刻 Wathe 游戏循环读到的 {@code getTime()} 相同，因此与 Wathe 的被动金币刻对齐。
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
     * {@link BearRules#PASSIVE_INCOME} when the world time is a multiple of {@link BearRules#PASSIVE_INTERVAL_TICKS},
     * else 0; independent of the balance (no cap). / 世界时间是 {@link BearRules#PASSIVE_INTERVAL_TICKS} 的整数倍时为
     * {@link BearRules#PASSIVE_INCOME}，否则为 0；与余额无关（不设上限）。
     */
    static int passiveIncomeAt(long worldTime) {
        return worldTime % BearRules.PASSIVE_INTERVAL_TICKS == 0 ? BearRules.PASSIVE_INCOME : 0;
    }

    // ---- Eligibility ----

    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        // Cheap role test first: this runs for every player on each passive tick. / 先做廉价的职业判断：每个被动刻对所有玩家执行。
        if (!BearRules.isBear(role)) {
            return false;
        }
        return canEarn(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), role, player.isSpectator(), player.isCreative(),
                SparkWitchApi.isWraithRestricted(player));
    }

    /**
     * Running round, playing and alive, exactly BEAR, survival, not a restricted Wraith (USEC and Accountant gates).
     * 对局进行中、参与且存活、职业恰为 BEAR、生存模式、不是受限冤魂（与 USEC、会计相同）。
     */
    static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                           boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && BearRules.isBear(role) && !spectator && !creative && !wraithRestricted;
    }

    // ---- Visibility ----

    static @Nullable CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return BearRules.isBear(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    /**
     * For the exact BEAR role: DENY once it is dead (the Insider precedent), ALLOW while playing and alive, otherwise
     * no answer as before. The explicit DENY is needed because with no answer Wathe falls back to
     * {@code ShopUtils.canAccessShop}, and the BEAR shop is built from the role alone, so a dead BEAR kept the coin HUD.
     * A BEAR that is not dead but not playing (the round still STARTING, when SparkTraits rolls money traits) is
     * deliberately left to that fallback, unchanged. Other roles get no answer. Reads only synced facts (role, dead
     * set), so both sides agree.
     * 对精确的 BEAR 职业：死亡后返回 DENY（沿用内应先例），参与且存活时返回 ALLOW，其余情况与之前一样不作答。显式 DENY 是必要
     * 的：不作答时 Wathe 退回 {@code ShopUtils.canAccessShop}，而 BEAR 商店只按职业构建，死亡的 BEAR 因此仍看得到金币 HUD。
     * 未死亡但未参与的 BEAR（对局仍为 STARTING、SparkTraits 抽取金钱词条时）刻意保持交给该回退，行为不变。其他职业不作答。只读取
     * 已同步的信息（职业、死亡名单），因此两端一致。
     */
    static @Nullable CanSeeMoney.Result moneyVisibility(@Nullable Role role, boolean dead, boolean playingAndAlive) {
        if (!BearRules.isBear(role)) {
            return null;
        }
        if (dead) {
            return CanSeeMoney.Result.DENY;
        }
        return playingAndAlive ? moneyVisibilityResult(role) : null;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return moneyVisibility(game.getRole(player), game.isPlayerDead(player.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(player));
    }
}
