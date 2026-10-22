package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.render.LocalViewerSnapshot;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only client gates for the local Black Raven. Eligibility reads the RAW Wathe role ({@code getRole}), never the
 * disguise acting overlay, so every gate here also holds while disguised; perceived-player knowledge still comes only
 * from the owner-only component sync.
 * 本地黑羽鸦的只读客户端门禁。资格读取 Wathe 原始职业（{@code getRole}），从不读取伪装扮演覆盖层，因此伪装期间这里的门禁
 * 同样成立；被感知者情报仍只来自仅拥有者可见的组件同步。
 */
public final class BlackRavenClientState {
    private BlackRavenClientState() {
    }

    /**
     * Asked several times per rendered entity by the instinct hooks and Perception gates; for the local player it is
     * memoized per world pass ({@link LocalViewerSnapshot}), any other player is answered fresh.
     * 本能钩子与感知门禁会对每个渲染实体多次询问；本地玩家的结果按世界 pass 缓存，其他玩家实时计算。
     */
    public static boolean isEligible(@Nullable PlayerEntity player) {
        return LocalViewerSnapshot.viewerFact(LocalViewerSnapshot.Fact.BLACK_RAVEN_ELIGIBLE, player,
                BlackRavenClientState::evaluateEligible);
    }

    public static boolean isPerceptionActive(@Nullable PlayerEntity player) {
        return LocalViewerSnapshot.viewerFact(LocalViewerSnapshot.Fact.BLACK_RAVEN_PERCEPTION_ACTIVE, player,
                BlackRavenClientState::evaluatePerceptionActive);
    }

    private static boolean evaluateEligible(@Nullable PlayerEntity player) {
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null) {
            return false;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getWorld());
        return gameComponent.isRunning()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && BlackRavenRules.isBlackRaven(gameComponent.getRole(player));
    }

    private static boolean evaluatePerceptionActive(@Nullable PlayerEntity player) {
        return isEligible(player) && BlackRavenPerceptionPlayerComponent.KEY.get(player).isActive();
    }
}
