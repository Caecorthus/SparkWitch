package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.net.ThrowKidnapperBodyC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperCarryState;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Claims crouch-use for one throw request while the server retains authority. / 每次按住潜行使用键只认领一次投掷请求，最终由服务端裁决。 */
public final class KidnapperThrowClientHooks {
    private static boolean useHeld;

    private KidnapperThrowClientHooks() {
    }

    public static void tick(MinecraftClient client) {
        if (!client.options.useKey.isPressed()) {
            useHeld = false;
        }
    }

    public static void reset() {
        useHeld = false;
    }

    public static boolean tryThrow(MinecraftClient client) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            useHeld = false;
            return false;
        }
        if (useHeld) {
            return true;
        }

        ClientPlayerEntity player = client.player;
        if (player == null
                || client.getNetworkHandler() == null
                || !ClientPlayNetworking.canSend(ThrowKidnapperBodyC2SPacket.ID)
                || !isEligible(player)) {
            return false;
        }
        useHeld = true;
        ClientPlayNetworking.send(new ThrowKidnapperBodyC2SPacket());
        return true;
    }

    private static boolean isEligible(ClientPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return KidnapperRules.isKidnapper(role)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && player.isSneaking()
                && KidnapperCarryState.findCarriedBody(player) != null;
    }
}
