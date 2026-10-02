package dev.caecorthus.sparkwitch.client.blind;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Stable client contract: the single predicate every Blind client feature (black screen, line art, HUD gates, sound
 * capture, keys) gates on. True when, on a confirmed SparkWitch server, the local player's REAL synced Wathe role
 * ({@code getRole}, never the Black Raven {@code isRole} overlay) is the Blind in a running round and either the player
 * is alive and playing with the camera on itself, or a Taotie has swallowed it (C7: stays black). Re-evaluated every
 * call, so recruitment, death, spectating another entity and round end turn the view off without a listener.
 * 稳定客户端契约：所有盲人客户端功能（黑屏、线稿、HUD 闸门、声音采集、按键）共用的唯一判定。在已确认的 SparkWitch
 * 服务器上，本地玩家同步的真实 Wathe 职业（{@code getRole}，从不使用黑羽鸦 {@code isRole} 覆盖层）为盲人且对局进行中，
 * 并且玩家存活参与且镜头在自己身上，或已被饕餮吞下（C7：保持全黑）时为真。每次调用都重新计算，
 * 因此招募、死亡、观看其他实体与回合结束都会自动关闭视图，无需监听器。
 */
public final class BlindView {
    private BlindView() {
    }

    public static boolean isActive(@Nullable MinecraftClient client) {
        if (client == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return shouldBeBlind(
                game.isRunning(),
                BlindRules.isBlind(game.getRole(player)),
                GameFunctions.isPlayerPlayingAndAlive(player),
                client.getCameraEntity() == player,
                NoellesTaotieSeekerBridge.isSwallowed(player));
    }

    /**
     * The local player's real synced role is the Blind (no liveness or round check). For role-scoped key routing.
     * 本地玩家同步的真实职业为盲人（不检查存活或对局）。用于按职业路由按键。
     */
    public static boolean isRealBlind(@Nullable PlayerEntity player) {
        return player != null && BlindRules.isBlind(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** Pure rule behind {@link #isActive}. / {@link #isActive} 背后的纯规则。 */
    public static boolean shouldBeBlind(boolean running, boolean realBlind, boolean playingAndAlive,
                                        boolean cameraOnSelf, boolean swallowed) {
        return running && realBlind && (swallowed || (playingAndAlive && cameraOnSelf));
    }
}
