package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowUseC2SPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Receiver of {@code sparkwitch:black_raven_crow_use} (a right-click while flying). Owned by crow-world, which replays
 * an empty-hand Wathe door, button, lever, trapdoor or fence-gate use from the crow after re-validating distance, sight
 * and facing ({@code SeekerCarUseService.useWatheDoor} / {@code SeekerCarUseRules} pattern); locked doors stay locked.
 * Until then only the session gate runs and the use is ignored.
 * {@code sparkwitch:black_raven_crow_use}（飞行中右键）的接收方。归 crow-world 所有：从乌鸦位置重新校验距离、视线与朝向后，
 * 重放空手的 Wathe 开门、按钮、拉杆、活板门或栅栏门交互（仿照搜寻者小车）；上锁的门照常拒绝。在此之前只执行会话门槛，交互被忽略。
 */
public final class BlackRavenCrowUseService {
    private BlackRavenCrowUseService() {
    }

    public static void handleUse(@Nullable ServerPlayerEntity player, @Nullable BlackRavenCrowUseC2SPayload payload) {
        if (player == null || payload == null || payload.hit() == null) {
            return;
        }
        BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(player);
        if (crow == null || crow.getId() != payload.crowEntityId()) {
            return;
        }
        // crow-world: validate payload.hit() from the crow and replay the block use here.
        // crow-world：在此从乌鸦位置校验 payload.hit() 并重放方块交互。
    }
}
