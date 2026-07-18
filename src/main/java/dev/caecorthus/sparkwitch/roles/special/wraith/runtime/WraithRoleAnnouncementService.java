package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkwitch.net.WraithRoleAnnouncementS2CPacket;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Replays the current promoted identity without changing Wathe's assignment lifecycle. */
final class WraithRoleAnnouncementService {
    private WraithRoleAnnouncementService() {
    }

    static void announceCurrentRole(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        int killers = game.getStartingKillerCount();
        int targets = Math.max(0, game.getAllPlayers().size() - killers);
        ServerPlayNetworking.send(player, new WraithRoleAnnouncementS2CPacket(
                (role == null ? WatheRoles.CIVILIAN : role).identifier().toString(),
                killers,
                targets
        ));
    }
}
