package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owns Saint kill protection at Wathe's kill entry point and post-kill Karma through its public event.
 * 在 Wathe 击杀入口执行圣徒保护，并通过公开事件处理击杀后的业障逻辑。
 */
public final class SaintFeatureService {
    private static boolean registered;

    private SaintFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(SaintFeatureService::afterKill);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        SaintAbilityService.assignForRole(player, role);
        if (SaintRules.isKarmaImmune(role)) {
            SaintKarmaService.clear(player);
        }
    }

    /**
     * Called by the GameFunctions mixin before Wathe's short-circuiting BEFORE listeners.
     * 由 GameFunctions mixin 在 Wathe 会短路的 BEFORE 监听器之前调用。
     */
    public static boolean blocksKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        boolean saintVictim = SaintRules.isSaint(gameComponent.getRole(victim));
        Identifier killerFaction = saintVictim && killer != null
                ? SparkFactionApi.resolveEffectiveFaction(killer, gameComponent)
                : null;
        return SaintRules.blocksKill(saintVictim, killerFaction, deathReason);
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        WitchPlayerComponent victimComponent = WitchPlayerComponent.KEY.get(victim);
        boolean activeSaintDied = SaintRules.isSaint(gameComponent.getRole(victim))
                && victimComponent.getSaintState().isHellfireActive();
        if (activeSaintDied) {
            if (victimComponent.getSaintState().clearHellfire()) {
                victimComponent.sync();
            }
            if (killer != null
                    && !killer.getUuid().equals(victim.getUuid())
                    && !FactionIds.CIVILIAN.equals(
                    SparkFactionApi.resolveEffectiveFaction(killer, gameComponent))) {
                SaintKarmaService.mark(killer);
            }
        }

        if (killer != null && !killer.getUuid().equals(victim.getUuid())) {
            SaintKarmaService.trigger(killer);
        }
    }
}
