package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Local-only instinct mode; all identity knowledge still comes from owner-only component sync. / 仅本地保存本能模式；身份情报仍只来自所有者组件同步。 */
public final class BlackRavenClientState {
    public enum InstinctMode {
        NORMAL,
        SENSED_ONLY;

        private InstinctMode next() {
            return this == NORMAL ? SENSED_ONLY : NORMAL;
        }
    }

    private static InstinctMode mode = InstinctMode.NORMAL;

    private BlackRavenClientState() {
    }

    public static InstinctMode mode() {
        return mode;
    }

    public static boolean isSensedMode() {
        return mode == InstinctMode.SENSED_ONLY;
    }

    public static void cycle(ClientPlayerEntity player) {
        if (!isEligible(player)) {
            reset();
            return;
        }
        if (isPerceptionActive(player)) {
            return;
        }
        mode = mode.next();
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || !isEligible(client.player)) {
            reset();
        }
    }

    public static void reset() {
        mode = InstinctMode.NORMAL;
    }

    public static boolean isEligible(@Nullable PlayerEntity player) {
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null) {
            return false;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getWorld());
        return gameComponent.isRunning()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && BlackRavenRules.isBlackRaven(gameComponent.getRole(player));
    }

    public static boolean isPerceptionActive(@Nullable PlayerEntity player) {
        return isEligible(player) && BlackRavenPerceptionPlayerComponent.KEY.get(player).isActive();
    }

    public static @Nullable BlackRavenIdentitySnapshot snapshot(PlayerEntity viewer, PlayerEntity target) {
        if (!isEligible(viewer)) {
            return null;
        }
        return BlackRavenPerceptionPlayerComponent.KEY.get(viewer).snapshot(target.getUuid());
    }
}
