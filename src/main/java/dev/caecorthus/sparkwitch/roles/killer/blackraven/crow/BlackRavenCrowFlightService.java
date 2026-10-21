package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowMoveC2SPayload;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Server half of the crow flight: validates {@code sparkwitch:black_raven_crow_move} like SparkStrength's
 * {@code DronePilotService.move} (stale or excess moves dropped silently, non-finite poses corrected) and replays the
 * pose through {@link BlackRavenCrowEntity#applyPilotStep}.
 * 乌鸦飞行的服务端部分：仿照 SparkStrength {@code DronePilotService.move} 校验 {@code black_raven_crow_move}（过期或超量的移动包
 * 静默丢弃，非有限位姿予以纠正），再经 {@link BlackRavenCrowEntity#applyPilotStep} 重放位姿。
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
        if (crow == null || crow.getId() != payload.crowEntityId()
                || !crow.acceptMove(player.getServerWorld().getTime())) {
            return;
        }
        boolean accepted = crow.applyPilotStep(new Vec3d(payload.x(), payload.y(), payload.z()),
                payload.yaw(), payload.pitch());
        if (!accepted) {
            BlackRavenCrowNetworking.sendCorrection(player, BlackRavenCrowSessions.correctionOf(crow));
        }
    }
}
