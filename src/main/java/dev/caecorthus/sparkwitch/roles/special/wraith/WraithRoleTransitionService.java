package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleAnnouncementApi;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/** Owns live role replacement and full opening replay without RoleAssigned. */
final class WraithRoleTransitionService {
    private WraithRoleTransitionService() {
    }

    static void transition(ServerPlayerEntity player, Role role) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        game.addRole(player, role);
        game.sync();
        RoleAnnouncementApi.announceCurrentRole(player);
    }
}
