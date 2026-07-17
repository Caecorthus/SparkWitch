package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Reads only confirmed, synchronized Wraith state on the client.
 * 客户端仅在服务端确认后读取已同步的冤魂状态。
 */
public final class WraithClientState {
    private WraithClientState() {
    }

    public static boolean isActive(PlayerEntity player) {
        return SparkWitchServerConnection.isConfirmedServer()
                && player != null
                && SparkWitchApi.isWraithActive(player);
    }

    public static boolean isRestricted(PlayerEntity player) {
        return isActive(player) && SparkWitchApi.isWraithRestricted(player);
    }
}
