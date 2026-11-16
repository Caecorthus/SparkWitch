package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.util.ServerDisconnectCleanup;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Observation at the kill (spec "观测他人击杀按实际冷却减半"): a Savior (living, playing, not a spectator, not inside a
 * Taotie) observes a kill when the victim is inside its 64-block, 120-degree view cone and {@code canSee} confirms
 * it. Only another player's KILL counts: when such a victim is killed by a player who is neither the victim nor the
 * observing Savior, both of that Savior's weapons lose half of their own effective cooldown
 * ({@link SaviorCooldowns#reduceAfterObservedKill}). Killer-less deaths (an ESCAPED disconnect, a fall, Wathe's
 * innocent-shot punishment), suicides and deaths SparkTraits intercepted (Last Stand) count for nothing. Players who
 * merely leave the cone trigger nothing. The sword's hard left-click lock is handled client-side by
 * {@code SaviorLightsaberSwingMixin}.
 * <p>When it is sampled (owner 2026-11): once per kill attempt, at the very start of Wathe's {@code killPlayer}
 * ({@code mixin/savior/GameFunctionsSaviorObservationMixin}), while the victim's pre-kill state is intact. The service
 * used to rebuild every Savior's whole visible set every server tick, raycasting to every player in the cone, only
 * for the few ticks on which somebody dies. The filters are the same ones, applied to the one pair that matters; in
 * particular a victim who already was a spectator or Wathe-dead before the kill (a Hiro feign's delayed death, a
 * Taotie-swallowed player) is seen by nobody, exactly as the per-tick snapshot never contained them. The sample
 * lives only for that kill attempt: {@code KillPlayer.AFTER} reads it, and it is dropped when the attempt returns,
 * cancelled or not. The only difference from the snapshot is the sampling instant: the kill tick instead of the end
 * of the previous tick.
 * 击杀时的观测（规格“观测他人击杀按实际冷却减半”）：当死者位于某名救世主（存活在局、非旁观、未被饕餮吞下）64 格、120 度的
 * 视野锥内且 {@code canSee} 为真时，该救世主观测到这次击杀。只有他人的“击杀”才算：这样的死者被一名既不是死者本人、也不是该
 * 救世主的玩家击杀时，该救世主两把武器各减少自身实际冷却的一半（{@link SaviorCooldowns#reduceAfterObservedKill}）。没有
 * 击杀者的死亡（ESCAPED 断线、坠落、Wathe 误杀惩罚）、自杀以及被 SparkTraits 拦截的死亡（背水一战）都不算。只是离开视野
 * 不触发任何效果。光剑冷却期硬禁左键由客户端 {@code SaviorLightsaberSwingMixin} 处理。
 * <p>采样时机（所有者 2026-11）：每次击杀尝试采样一次，位于 Wathe {@code killPlayer} 的最开头
 * （{@code mixin/savior/GameFunctionsSaviorObservationMixin}），此时死者击杀前的状态完好。此前本服务每个服务端刻都为每名
 * 救世主重建整个可见集合、对视野锥内的每名玩家做射线检测，而这只在有人死亡的少数几刻才有用。过滤条件完全相同，只是只作用于
 * 真正相关的那一对；尤其是击杀之前就已是旁观者或已被 Wathe 判定死亡的死者（希罗假死的延迟死亡、被饕餮吞下的玩家）不会被任何人
 * 看见，与逐刻快照从不包含他们完全一致。采样只在该次击杀尝试期间存在：{@code KillPlayer.AFTER} 读取它，尝试返回时（无论是否
 * 被取消）即丢弃。与快照唯一的差别是采样时刻：击杀所在的那一刻，而不是上一刻结束时。
 */
public final class SaviorObservationService {
    /** Vision range in blocks. / 视野距离（格）。 */
    private static final double VISION_RANGE = 64.0D;
    /** cos(half-angle): 0.5 = 120-degree full cone. / cos(半角)：0.5 = 全角 120 度。 */
    private static final double VISION_MIN_DOT = 0.5D;
    /** KillPlayer.AFTER and AFTER_DEATH may both report one death. / 两条死亡事件可能各报一次。 */
    private static final long DEATH_DEDUPE_TICKS = 5L;

    /**
     * Victim UUID → the Saviors that saw it when each kill attempt in flight began (a stack, because a kill can be
     * attempted again from inside its own attempt); victim UUID → world tick of its last counted kill. Server thread
     * only; a sample is dropped when its attempt returns, the rest is cleared per player on disconnect and wholesale
     * at round finalize and server stop.
     * 死者 UUID → 每次进行中的击杀尝试开始时看见它的救世主（使用栈，因为击杀可能在自身的尝试内部再次被尝试）；死者 UUID →
     * 最近一次被计入的击杀所在世界刻。仅服务端线程；采样在其尝试返回时丢弃，其余在断线时逐个清除，回合收尾与服务器停止时
     * 整体清空。
     */
    private static final Map<UUID, Deque<Set<UUID>>> KILL_SAMPLES = new HashMap<>();
    private static final Map<UUID, Long> LAST_DEATH = new HashMap<>();
    private static boolean registered;

    private SaviorObservationService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register((victim, killer, reason) -> onDeath(victim, killer));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                onDeath(victim, source.getAttacker() instanceof ServerPlayerEntity killer ? killer : null);
            }
        });
        ServerDisconnectCleanup.register((player, server) -> forget(player.getUuid()));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> forgetAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> forgetAll());
    }

    /**
     * Start of a kill attempt on {@code victim} (the outermost point of Wathe's {@code killPlayer}, before any hook
     * changes the victim): remembers which Saviors see the victim now. Always paired with {@link #endKillAttempt}.
     * 对 {@code victim} 的击杀尝试开始时（Wathe {@code killPlayer} 的最外层，早于任何钩子改动死者）：记下此刻哪些救世主看得见
     * 死者。始终与 {@link #endKillAttempt} 成对调用。
     */
    public static void beginKillAttempt(@Nullable ServerPlayerEntity victim) {
        if (victim != null) {
            KILL_SAMPLES.computeIfAbsent(victim.getUuid(), key -> new ArrayDeque<>()).push(observersOf(victim));
        }
    }

    /**
     * End of that attempt, on every path (killed, cancelled, refused, thrown): the sample never outlives it.
     * 该次尝试结束时，无论走哪条路径（击杀、被取消、被拒绝、抛出异常）：采样绝不比它存在得更久。
     */
    public static void endKillAttempt(@Nullable ServerPlayerEntity victim) {
        if (victim == null) {
            return;
        }
        Deque<Set<UUID>> samples = KILL_SAMPLES.get(victim.getUuid());
        if (samples == null) {
            return;
        }
        samples.poll();
        if (samples.isEmpty()) {
            KILL_SAMPLES.remove(victim.getUuid());
        }
    }

    /**
     * The Saviors that see {@code target} right now, by the filters the per-tick snapshot applied to every pair: the
     * target is in its world, not a spectator and not Wathe-dead; the viewer may observe, is within range, has the
     * target inside its cone and, last because it is a block raycast, has line of sight.
     * 此刻看得见 {@code target} 的救世主，过滤条件与逐刻快照对每一对所用的相同：目标在其世界中、不是旁观者且未被 Wathe 判定
     * 死亡；观察者可以观测、在范围内、目标位于其视野锥内，最后（因为是方块射线检测）有视线。
     */
    private static Set<UUID> observersOf(ServerPlayerEntity target) {
        ServerWorld world = target.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (target.isRemoved() || target.isSpectator() || game.isPlayerDead(target.getUuid())) {
            return Set.of();
        }
        Set<UUID> observers = new HashSet<>();
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer == target || !canObserve(viewer, game)
                    || viewer.squaredDistanceTo(target) > VISION_RANGE * VISION_RANGE) {
                continue;
            }
            Vec3d eye = viewer.getEyePos();
            Vec3d look = viewer.getRotationVec(1.0F);
            Vec3d to = target.getEyePos().subtract(eye);
            if (to.lengthSquared() < 1.0E-6D || look.dotProduct(to.normalize()) < VISION_MIN_DOT) {
                continue;
            }
            if (!viewer.canSee(target)) {
                continue;
            }
            observers.add(viewer.getUuid());
        }
        return observers;
    }

    /**
     * A Savior observes only while it is the raw Savior role, playing and alive, not a spectator and not inside a
     * Taotie. / 救世主只有在其真实职业为救世主、存活在局、非旁观且未被饕餮吞下时才能观测。
     */
    private static boolean canObserve(ServerPlayerEntity savior, GameWorldComponent game) {
        return SaviorRules.isSavior(game.getRole(savior))
                && GameFunctions.isPlayerPlayingAndAlive(savior)
                && !savior.isSpectator()
                && !NoellesTaotieSeekerBridge.isSwallowed(savior);
    }

    private static void onDeath(@Nullable ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer) {
        if (victim == null || WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        // A dead Savior observes nothing more: canObserve refuses it below and in every later sample.
        // 死亡的救世主不再观测：下方以及之后的每次采样中 canObserve 都会拒绝它。
        if (!isAnotherPlayersKill(killer != null, killer == victim)) {
            return;
        }
        long now = victim.getWorld().getTime();
        Long last = LAST_DEATH.get(victim.getUuid());
        if (last != null && now - last < DEATH_DEDUPE_TICKS) {
            return;
        }
        LAST_DEATH.put(victim.getUuid(), now);

        MinecraftServer server = victim.getServer();
        if (server == null) {
            return;
        }
        // The sample of the kill attempt this death belongs to. A death that did not come through killPlayer (a
        // vanilla death reported by AFTER_DEATH alone) has none; vanilla leaves the victim's mode and position alone,
        // so the same filters are applied on the spot.
        // 本次死亡所属击杀尝试的采样。未经 killPlayer 的死亡（仅由 AFTER_DEATH 报告的原版死亡）没有采样；原版不改变死者的
        // 模式与位置，因此当场应用同样的过滤条件。
        Deque<Set<UUID>> samples = KILL_SAMPLES.get(victim.getUuid());
        Set<UUID> observers = samples == null || samples.isEmpty() ? observersOf(victim) : samples.peek();
        for (UUID observer : Set.copyOf(observers)) {
            ServerPlayerEntity savior = server.getPlayerManager().getPlayer(observer);
            if (savior == null || savior == killer
                    || !canObserve(savior, GameWorldComponent.KEY.get(savior.getWorld()))) {
                continue;
            }
            SaviorCooldowns.reduceAfterObservedKill(savior);
        }
    }

    /**
     * Pure: only a kill by a player who is not the victim counts (the observing Savior's own kills are excluded per
     * Savior). / 纯函数：只有非死者本人的玩家造成的击杀才算（观测救世主自己的击杀按救世主逐个排除）。
     */
    static boolean isAnotherPlayersKill(boolean hasKiller, boolean killerIsVictim) {
        return hasKiller && !killerIsVictim;
    }

    private static void forget(UUID player) {
        LAST_DEATH.remove(player);
    }

    private static void forgetAll() {
        KILL_SAMPLES.clear();
        LAST_DEATH.clear();
    }
}
