package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowDropFeatherC2SPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Receiver of {@code sparkwitch:black_raven_crow_drop_feather}. Owned by crow-world: at most once per session it
 * spawns the Homing Feather marker ({@code sparkwitch:homing_feather}) at the crow (gravity, visible to all, never
 * picked up or destroyed) and records it with {@link BlackRavenCrowPlayerComponent#setFeather}. Until then only the
 * session gate runs and the drop is ignored.
 * {@code sparkwitch:black_raven_crow_drop_feather} 的接收方。归 crow-world 所有：每次会话至多一次，在乌鸦位置生成归羽标记
 * （受重力、所有人可见、不能拾取或破坏），并用 {@link BlackRavenCrowPlayerComponent#setFeather} 记录。在此之前只执行会话门槛，
 * 丢弃被忽略。
 */
public final class BlackRavenCrowFeatherService {
    private BlackRavenCrowFeatherService() {
    }

    public static void handleDrop(@Nullable ServerPlayerEntity player,
                                  @Nullable BlackRavenCrowDropFeatherC2SPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(player);
        if (crow == null || BlackRavenCrowPlayerComponent.KEY.get(player).hasFeather()) {
            return;
        }
        // crow-world: spawn the Homing Feather marker at the crow here. / crow-world：在此于乌鸦位置生成归羽标记。
    }
}
