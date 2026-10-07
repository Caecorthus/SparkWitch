package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentWorldComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendParticipation;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client adapter for {@link FiendMomentHighlightRules}. Client authority is presentation only: the server syncs just the
 * moment fact (Fiend UUID and remaining ticks) through {@link FiendMomentWorldComponent}; roles come from Wathe's synced
 * game component. Every non-Fiend frame returns {@code null} after the cheap gates (confirmed server, player target,
 * active moment), so unrelated outlines stay on their existing path.
 * {@link FiendMomentHighlightRules} 的客户端适配。客户端只负责展示：服务端仅通过 {@link FiendMomentWorldComponent}
 * 同步时刻事实（魔人 UUID 与剩余 tick），职业来自 Wathe 已同步的对局组件。与魔人无关的每一帧在廉价门槛（已确认服务端、
 * 玩家目标、时刻进行中）后返回 {@code null}，使无关描边保持原有路径。
 */
public final class FiendMomentHighlightClient {
    private FiendMomentHighlightClient() {
    }

    public static @Nullable Integer highlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer() || !(target instanceof PlayerEntity playerTarget)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !FiendMomentWorldComponent.get(viewer.getWorld()).isActive()) {
            return null;
        }
        return FiendMomentHighlightRules.highlight(
                true,
                FiendParticipation.isMomentFiend(viewer),
                FiendParticipation.isMomentFiend(playerTarget),
                viewer.getUuid().equals(playerTarget.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(playerTarget)
                        && !GameFunctions.isPlayerSpectatingOrCreative(playerTarget)
        );
    }
}
