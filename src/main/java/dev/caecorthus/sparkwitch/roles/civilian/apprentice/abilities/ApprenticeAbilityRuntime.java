package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing.HealingAbility;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Owns server ticking for all Apprentice ability windows.
 * 负责所有预备魔女能力窗口的服务端 tick，不拥有组件存储或同步 schema。
 */
public final class ApprenticeAbilityRuntime {
    private ApprenticeAbilityRuntime() {
    }

    public static void tick(ServerPlayerEntity player, WitchPlayerComponent component) {
        if (!component.hasApprenticeWindowState()) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role != SparkWitchRoles.apprenticeWitch() || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            component.clearApprenticeWindowState();
            component.sync();
            return;
        }

        ApprenticeAbilityWindowRules.TickResult result =
                ApprenticeAbilityWindowRules.tick(component.apprenticeWindowState());
        component.applyApprenticeWindowState(result.state());
        if (result.healingPulseDue()) {
            HealingAbility.applyPulse(player);
        }
        if (result.startDeferredCooldown()) {
            component.startDeferredCooldownNow();
        }
        if (result.syncRequired() || result.startDeferredCooldown()) {
            component.sync();
        }
    }
}
