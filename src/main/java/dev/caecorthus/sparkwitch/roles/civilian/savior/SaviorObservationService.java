package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Real-time observation set (spec "观测他人击杀按实际冷却减半"): every tick each observing Savior (living, playing, not a
 * spectator, not inside a Taotie) records the players inside its 64-block, 120-degree view cone that {@code canSee}
 * confirms. Only another player's KILL counts: when one of those players is killed by a player who is neither the
 * victim nor the observing Savior, both of that Savior's weapons lose half of their own effective cooldown
 * ({@link SaviorCooldowns#reduceAfterObservedKill}). Killer-less deaths (an ESCAPED disconnect, a fall, Wathe's
 * innocent-shot punishment), suicides and deaths SparkTraits intercepted (Last Stand) count for nothing. Players who
 * merely leave the cone trigger nothing. The sword's hard left-click lock is handled client-side by
 * {@code SaviorLightsaberSwingMixin}.
 * 实时观测集合（规格“观测他人击杀按实际冷却减半”）：每个服务端刻，每名可观测的救世主（存活在局、非旁观、未被饕餮吞下）记录
 * 其 64 格、120 度视野锥内且 {@code canSee} 为真的玩家。只有他人的“击杀”才算：集合中有人被一名既不是死者本人、也不是该
 * 救世主的玩家击杀时，该救世主两把武器各减少自身实际冷却的一半（{@link SaviorCooldowns#reduceAfterObservedKill}）。没有
 * 击杀者的死亡（ESCAPED 断线、坠落、Wathe 误杀惩罚）、自杀以及被 SparkTraits 拦截的死亡（背水一战）都不算。只是离开视野
 * 不触发任何效果。光剑冷却期硬禁左键由客户端 {@code SaviorLightsaberSwingMixin} 处理。
 */
public final class SaviorObservationService {
    /** Vision range in blocks. / 视野距离（格）。 */
    private static final double VISION_RANGE = 64.0D;
    /** cos(half-angle): 0.5 = 120-degree full cone. / cos(半角)：0.5 = 全角 120 度。 */
    private static final double VISION_MIN_DOT = 0.5D;
    /** KillPlayer.AFTER and AFTER_DEATH may both report one death. / 两条死亡事件可能各报一次。 */
    private static final long DEATH_DEDUPE_TICKS = 5L;

    /**
     * Savior UUID → players seen this tick; victim UUID → world tick of its last counted kill. Server thread only;
     * cleared per player on disconnect and wholesale at round finalize and server stop.
     * 救世主 UUID → 本刻看见的玩家；死者 UUID → 最近一次被计入的击杀所在世界刻。仅服务端线程；断线时逐个清除，回合收尾与
     * 服务器停止时整体清空。
     */
    private static final Map<UUID, Set<UUID>> VISIBLE = new HashMap<>();
    private static final Map<UUID, Long> LAST_DEATH = new HashMap<>();
    private static boolean registered;

    private SaviorObservationService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(SaviorObservationService::tick);
        KillPlayer.AFTER.register((victim, killer, reason) -> onDeath(victim, killer));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                onDeath(victim, source.getAttacker() instanceof ServerPlayerEntity killer ? killer : null);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                forget(handler.player.getUuid());
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> forgetAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> forgetAll());
    }

    private static void tick(MinecraftServer server) {
        Set<UUID> observers = new HashSet<>();
        for (ServerWorld world : server.getWorlds()) {
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            for (ServerPlayerEntity viewer : world.getPlayers()) {
                if (!canObserve(viewer, game)) {
                    continue;
                }
                observers.add(viewer.getUuid());
                Set<UUID> visible = new HashSet<>();
                Vec3d eye = viewer.getEyePos();
                Vec3d look = viewer.getRotationVec(1.0F);
                for (ServerPlayerEntity target : world.getPlayers()) {
                    if (target == viewer || target.isSpectator() || game.isPlayerDead(target.getUuid())
                            || viewer.squaredDistanceTo(target) > VISION_RANGE * VISION_RANGE) {
                        continue;
                    }
                    Vec3d to = target.getEyePos().subtract(eye);
                    if (to.lengthSquared() < 1.0E-6D || look.dotProduct(to.normalize()) < VISION_MIN_DOT) {
                        continue;
                    }
                    if (!viewer.canSee(target)) {
                        continue;
                    }
                    visible.add(target.getUuid());
                }
                VISIBLE.put(viewer.getUuid(), visible);
            }
        }
        VISIBLE.keySet().retainAll(observers);
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
        // A dead Savior observes nothing more. / 死亡的救世主不再观测。
        VISIBLE.remove(victim.getUuid());
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
        for (Map.Entry<UUID, Set<UUID>> entry : Map.copyOf(VISIBLE).entrySet()) {
            if (!entry.getValue().contains(victim.getUuid())) {
                continue;
            }
            ServerPlayerEntity savior = server.getPlayerManager().getPlayer(entry.getKey());
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
        VISIBLE.remove(player);
        LAST_DEATH.remove(player);
    }

    private static void forgetAll() {
        VISIBLE.clear();
        LAST_DEATH.clear();
    }
}
