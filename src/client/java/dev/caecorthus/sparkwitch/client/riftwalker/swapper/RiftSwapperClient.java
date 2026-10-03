package dev.caecorthus.sparkwitch.client.riftwalker.swapper;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client side of the Swapper crush (C7; plan §16): the NoellesRoles Swapper screen patch that lets the Swapper pick a
 * listed player who is in spectator mode but alive (only gate occupants are crushed; the server silently cancels every
 * other spectator target). Owned by P9 (together with its client mixin). Presentation only: it never predicts the
 * outcome and sends nothing beyond NR's own payload.
 * 交换者夹死的客户端（C7；plan §16）：NoellesRoles 交换界面补丁，让交换者可以点选列表中处于旁观者模式但仍存活的玩家
 * （只有门内的人会触发夹死；服务端对其他旁观者目标静默取消）。归属 P9（连同其客户端 mixin）。仅负责展示：不预测结果，
 * 除 NR 自身的数据包外不发送任何内容。
 */
public final class RiftSwapperClient {
    private static boolean initialized;

    private RiftSwapperClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // Nothing to register: RiftSwapperWidgetMixin is the whole client side. / 无需注册：客户端只有 RiftSwapperWidgetMixin。
    }

    /**
     * Non-null stand-in for NR's "is the clicked player in my world" check when {@link RiftSwapperPickRules} allows an
     * untracked pick; the widget only null-checks that value (pinned by a local jar test), so the local player is
     * returned. Null keeps NR's stock silent refusal.
     * 当 {@link RiftSwapperPickRules} 允许点选未追踪的玩家时，为 NR「被点玩家是否在我的世界里」检查提供的非空替身；
     * 控件只对该值判空（由本地 jar 测试锁定），因此返回本地玩家。返回 null 则保持 NR 原有的静默拒绝。
     */
    public static @Nullable PlayerEntity standInForUntrackedPick(UUID clicked) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (clicked == null || client.player == null || client.world == null) {
            return null;
        }
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        PlayerListEntry entry = network == null ? null : network.getPlayerListEntry(clicked);
        boolean listedAsSpectator = entry != null && entry.getGameMode() == GameMode.SPECTATOR;
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        boolean aliveInWathe = game.isRunning() && game.hasAnyRole(clicked) && !game.isPlayerDead(clicked);
        return RiftSwapperPickRules.allowsUntrackedPick(listedAsSpectator, aliveInWathe) ? client.player : null;
    }
}
