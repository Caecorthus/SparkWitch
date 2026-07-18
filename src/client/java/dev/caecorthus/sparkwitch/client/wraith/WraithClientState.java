package dev.caecorthus.sparkwitch.client.wraith;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.minecraft.entity.player.PlayerEntity;

/** Reads only server-confirmed Wraith state synchronized by SparkWitch. / 只读取由 SparkWitch 服务端确认并同步的冤魂状态。 */
public final class WraithClientState {
    private WraithClientState() {
    }

    public static boolean isActive(PlayerEntity player) {
        return SparkWitchServerConnection.isConfirmedServer()
                && player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isActive)
                .orElse(false);
    }

    public static boolean isRestricted(PlayerEntity player) {
        return SparkWitchServerConnection.isConfirmedServer()
                && player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isRestricted)
                .orElse(false);
    }

    public static boolean isPromoted(PlayerEntity player) {
        return SparkWitchServerConnection.isConfirmedServer()
                && player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isPromoted)
                .orElse(false);
    }
}
