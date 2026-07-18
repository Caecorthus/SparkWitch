package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.PerfumerPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionFeatureService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleAnnouncementApi;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Replaces a live role without replaying round-start RoleAssigned side effects.
 * 局中替换身份，但不重放开局 RoleAssigned 副作用。
 */
final class WraithRoleTransitionService {
    private WraithRoleTransitionService() {
    }

    static void transition(ServerPlayerEntity player, Role role) {
        // RoleAssigned is intentionally bypassed, so clear SparkWitch-owned prior-role state here.
        // 此处有意绕过 RoleAssigned，因此必须在这里清理 SparkWitch 旧身份运行态。
        WitchFactionFeatureService.clearPlayerRuntime(player);
        WitchPlayerComponent.KEY.get(player).clear();
        PerfumerPlayerComponent.KEY.get(player).clear();
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        game.addRole(player, role);
        game.sync();
        RoleAnnouncementApi.announceCurrentRole(player);
    }
}
