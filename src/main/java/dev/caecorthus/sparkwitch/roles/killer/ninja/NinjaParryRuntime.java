package dev.caecorthus.sparkwitch.roles.killer.ninja;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Owns Ninja parry-window ticking without moving component schema ownership.
 * 负责忍者格挡窗口的 tick，但不转移组件 schema 的所有权。
 */
public final class NinjaParryRuntime {
    private NinjaParryRuntime() {
    }

    public static void tick(ServerPlayerEntity player, WitchPlayerComponent component) {
        if (!component.isNinjaParryActive()) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!NinjaRules.isNinja(role) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            component.clearNinjaParryWindow();
            return;
        }

        int remainingTicks = component.decrementNinjaParryTicks();
        if (remainingTicks == 0) {
            component.finishNinjaParryWindow();
        } else if (remainingTicks % 20 == 0) {
            component.sync();
        }
    }
}
