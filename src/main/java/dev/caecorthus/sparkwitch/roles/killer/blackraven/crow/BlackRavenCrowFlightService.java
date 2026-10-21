package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowMoveC2SPayload;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Server half of the crow flight: validates {@code sparkwitch:black_raven_crow_move} like SparkStrength's
 * {@code DronePilotService.move} (stale moves dropped silently, non-finite poses corrected) and replays the pose through
 * {@link BlackRavenCrowEntity#applyPilotStep}. Moves beyond the per-tick count merge into one latest pose that the crow
 * applies on its own tick ({@link #applyDeferredMove}), so a burst never drops a pose.
 * 乌鸦飞行的服务端部分：仿照 SparkStrength {@code DronePilotService.move} 校验 {@code black_raven_crow_move}（过期的移动包静默
 * 丢弃，非有限位姿予以纠正），再经 {@link BlackRavenCrowEntity#applyPilotStep} 重放位姿。超过每刻数量的移动合并为一个最新位姿，
 * 由乌鸦在自身刻中应用（{@link #applyDeferredMove}），因此突发不会丢失位姿。
 */
public final class BlackRavenCrowFlightService {
    private BlackRavenCrowFlightService() {
    }

    public static void handleMove(@Nullable ServerPlayerEntity player, @Nullable BlackRavenCrowMoveC2SPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(player);
        // Moves still in flight after an end, or for an older crow, are dropped silently.
        // 会话结束后仍在途中的移动包或指向旧乌鸦的移动包直接丢弃。
        if (crow == null || crow.getId() != payload.crowEntityId()) {
            return;
        }
        BlackRavenCrowEntity.PilotPose pose = new BlackRavenCrowEntity.PilotPose(
                new Vec3d(payload.x(), payload.y(), payload.z()), payload.yaw(), payload.pitch());
        if (!crow.acceptMove(player.getServerWorld().getTime())) {
            crow.deferMove(pose);
            return;
        }
        apply(player, crow, pose);
    }

    /**
     * Server, from the crow's own tick: applies this tick's merged excess move, if any, with the same validation and
     * correction as a live move. / 服务端，由乌鸦自身刻调用：应用本刻合并的超量移动（如有），校验与纠正同实时移动。
     */
    static void applyDeferredMove(BlackRavenCrowEntity crow) {
        BlackRavenCrowEntity.PilotPose pose = crow.takeDeferredMove();
        ServerPlayerEntity owner = pose == null ? null : BlackRavenCrowSessions.ownerOf(crow);
        if (owner != null) {
            apply(owner, crow, pose);
        }
    }

    private static void apply(ServerPlayerEntity player, BlackRavenCrowEntity crow, BlackRavenCrowEntity.PilotPose pose) {
        if (!crow.applyPilotStep(pose.target(), pose.yaw(), pose.pitch())) {
            BlackRavenCrowNetworking.sendCorrection(player, BlackRavenCrowSessions.correctionOf(crow));
        }
    }
}
