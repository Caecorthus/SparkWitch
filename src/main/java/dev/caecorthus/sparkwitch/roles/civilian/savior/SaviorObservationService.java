package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Real-time observation set: every tick each living Savior records the players inside its view cone that
 * {@code canSee} confirms. When one of those players dies and the observing Savior did not kill them, both of the
 * Savior's weapons lose half of the cooldown duration remembered at their last cooldown entry; if the remainder is
 * shorter than that half, the weapon becomes available. Players who merely leave the cone are not deaths and trigger
 * nothing. The sword's hard left-click lock is handled client-side by {@code SaviorLightsaberSwingMixin}.
 * 实时观测集合：每个服务端刻，每名存活救世主记录视野锥内且 {@code canSee} 为真的玩家。集合中有人死亡且不是该救世主
 * 所杀时，两把武器各减少“本次进入冷却时长”的一半；剩余不足一半则直接可用。只是离开视野不算死亡，不触发。光剑冷却期
 * 硬禁左键由客户端 {@code SaviorLightsaberSwingMixin} 处理。
 */
public final class SaviorObservationService {
    /** Vision range in blocks. / 视野距离（格）。 */
    private static final double VISION_RANGE = 64.0D;
    /** cos(half-angle): 0.5 = 120-degree full cone. / cos(半角)：0.5 = 全角 120 度。 */
    private static final double VISION_MIN_DOT = 0.5D;
    /** KillPlayer.AFTER and AFTER_DEATH may both report one death. / 两条死亡事件可能各报一次。 */
    private static final long DEATH_DEDUPE_TICKS = 5L;

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
    }

    private static void tick(MinecraftServer server) {
        Set<UUID> livingSaviors = new HashSet<>();
        for (ServerWorld world : server.getWorlds()) {
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            for (ServerPlayerEntity viewer : world.getPlayers()) {
                if (game.getRole(viewer) != SparkWitchRoles.savior() || game.isPlayerDead(viewer.getUuid())) {
                    continue;
                }
                livingSaviors.add(viewer.getUuid());
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
        VISIBLE.keySet().retainAll(livingSaviors);
    }

    private static void onDeath(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer) {
        if (victim == null) {
            return;
        }
        long now = victim.getWorld().getTime();
        Long last = LAST_DEATH.get(victim.getUuid());
        if (last != null && now - last < DEATH_DEDUPE_TICKS) {
            return;
        }
        LAST_DEATH.put(victim.getUuid(), now);

        MinecraftServer server = victim.getServer();
        for (Map.Entry<UUID, Set<UUID>> entry : VISIBLE.entrySet()) {
            UUID saviorId = entry.getKey();
            if (saviorId.equals(victim.getUuid()) || !entry.getValue().contains(victim.getUuid())) {
                continue;
            }
            ServerPlayerEntity savior = server == null ? null : server.getPlayerManager().getPlayer(saviorId);
            if (savior == null || savior.isSpectator() || killer == savior) {
                continue;
            }
            if (GameWorldComponent.KEY.get(savior.getWorld()).getRole(savior) != SparkWitchRoles.savior()) {
                continue;
            }
            SaviorCooldowns.reduceAfterObservedKill(savior);
        }
        SaviorCooldowns.clear(victim);
    }
}
