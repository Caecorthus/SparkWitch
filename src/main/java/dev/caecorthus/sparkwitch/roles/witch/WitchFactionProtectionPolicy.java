package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Wathe protection hooks for Witch faction blackout immunity and Voodoo guard.
 * 魔女阵营停电免疫和巫毒保护的 wathe 保护钩子。
 */
public final class WitchFactionProtectionPolicy {
    private static boolean registered;

    private WitchFactionProtectionPolicy() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackoutEffect.BEFORE.register(WitchFactionProtectionPolicy::beforeBlackoutEffect);
        KillPlayer.BEFORE.register(WitchFactionProtectionPolicy::beforeKillPlayer);
    }

    static BlackoutEffect.BlackoutResult beforeBlackoutEffect(ServerPlayerEntity player, int durationTicks) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        return WitchFactionRules.isWitchFactionMember(role) ? BlackoutEffect.BlackoutResult.cancel() : null;
    }

    static KillPlayer.KillResult beforeKillPlayer(
            ServerPlayerEntity victim,
            ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        Role role = GameWorldComponent.KEY.get(victim.getServerWorld()).getRole(victim);
        return GrandWitchRules.shouldBlockVoodooCurse(role, deathReason) ? KillPlayer.KillResult.cancel() : null;
    }
}
