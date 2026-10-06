package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkwitch.net.WraithRoleAnnouncementS2CPacket;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Replays the current promoted identity without changing Wathe's assignment lifecycle. Shared by Wraith promotion and
 * Bewitched promotion (C4); it only sends the existing role-announcement packet to the player.
 * 重放当前晋升后的身份，不改变 Wathe 的分配生命周期。冤魂晋升与魔化使晋升（C4）共用；只向该玩家发送现有的身份公告数据包。
 */
public final class WraithRoleAnnouncementService {
    private WraithRoleAnnouncementService() {
    }

    public static void announceCurrentRole(ServerPlayerEntity player) {
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
