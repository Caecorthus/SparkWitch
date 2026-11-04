package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsTaskIncomeBridge;
import dev.doctor4t.wathe.Wathe;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * 探索者 server passives: open-air Speed II and the per-task payout (the role has no passive income). Both use
 * {@link #canUsePassives}, the USEC/Knight/Control Expert {@code canEarn} gate. The sprint drain lives in
 * {@link DiscovererStaminaService}.
 * 探索者的服务端被动：露天速度 II 与按任务结算的金币（该职业没有被动收入）。两者都使用 {@link #canUsePassives}，即 USEC、
 * 骑士与控场专家的 {@code canEarn} 门槛。疾跑消耗见 {@link DiscovererStaminaService}。
 */
public final class DiscovererService {
    private static boolean registered;

    private DiscovererService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        ServerTickEvents.END_WORLD_TICK.register(DiscovererService::tickOpenAirSpeed);
    }

    /**
     * +{@link DiscovererRules#TASK_REWARD} per finished task, every task type included. {@code addToBalance} lets income
     * observers (SparkFactionAPI's income record, SparkTraits Snowball, SparkStrength's purse) see it as ordinary
     * income. Never paid to a SparkTraits Impostor, whose task income SparkTraits pays itself.
     * 每完成一个任务 +{@link DiscovererRules#TASK_REWARD}，所有任务类型都算。使用 {@code addToBalance}，使收入观察者
     * （SparkFactionAPI 收入记录、SparkTraits 滚雪球、SparkStrength 钱包）视其为普通收入。从不发给 SparkTraits 内鬼，
     * 内鬼的任务收入由 SparkTraits 自行支付。
     */
    static void onTaskComplete(ServerPlayerEntity player) {
        if (canUsePassives(player)
                && DiscovererRules.receivesTaskMoney(SparkTraitsTaskIncomeBridge.isImpostor(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(DiscovererRules.TASK_REWARD);
        }
    }

    private static void tickOpenAirSpeed(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Every player every tick: the role test rejects non-Discoverers before the other lookups.
            // 每 tick 对每名玩家执行：先用职业判断排除非探索者，再做其余查询。
            if (!DiscovererRules.isDiscoverer(game.getRole(player))) {
                continue;
            }
            // Wathe's own open-air test (its outside task and ambience use it): eye position, four diagonals, and
            // not standing inside a door leaf. / Wathe 自己的露天判定（其“在外面”任务与环境音都用它）：眼睛位置、
            // 四个斜角、且人不在门扇方块里。
            if (canUsePassives(player) && Wathe.isSkyVisibleAdjacent(player)) {
                applyOpenAirSpeed(player);
            }
        }
    }

    /**
     * Speed II with Emma's 40-tick length, refreshed at 20 ticks left. Never removed actively: it simply runs out
     * 1–2 s after the player steps inside, so another source's Speed (kept by vanilla as the hidden effect) is never
     * stripped.
     * 发放与艾玛相同的 40 刻速度 II，剩余 20 刻时续期。从不主动移除：进入室内 1–2 秒后自然结束，因此不会顶掉其他来源的速度
     * （原版将其保存为隐藏效果）。
     */
    private static void applyOpenAirSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        boolean refresh = current == null
                ? DiscovererRules.refreshesSpeed(false, 0, false, 0)
                : DiscovererRules.refreshesSpeed(true, current.getAmplifier(), current.isInfinite(),
                        current.getDuration());
        if (!refresh) {
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                DiscovererRules.OPEN_AIR_SPEED_DURATION_TICKS,
                DiscovererRules.OPEN_AIR_SPEED_AMPLIFIER,
                false,
                false,
                true));
    }

    static boolean canUsePassives(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return DiscovererRules.receivesPassives(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player), player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player));
    }
}
