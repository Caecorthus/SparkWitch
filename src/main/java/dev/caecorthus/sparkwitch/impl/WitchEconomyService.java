package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side money bridges for the custom Witch faction.
 * 自定义魔女阵营的服务端金币桥接；不修改 wathe 原生杀手队伍。
 */
public final class WitchEconomyService {
    private WitchEconomyService() {
    }

    public static int killerStyleStartingMoney(ServerPlayerEntity player, GameWorldComponent gameComponent) {
        return WitchEconomyRules.killerStartingMoney(
                player.getServerWorld().getPlayers().size(),
                gameComponent.getAllKillerTeamPlayers().size(),
                gameComponent.getKillerDividend()
        );
    }

    public static int accompliceStartingMoney(ServerPlayerEntity player, GameWorldComponent gameComponent) {
        return killerStyleStartingMoney(player, gameComponent);
    }

    public static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (killer == null) {
            return;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        Role killerRole = gameComponent.getRole(killer);
        if (!GrandWitchRules.isGrandWitch(killerRole)) {
            return;
        }

        for (ServerPlayerEntity teammate : victim.getServerWorld().getPlayers()) {
            boolean samePlayer = teammate.getUuid().equals(killer.getUuid());
            boolean teammateAlive = GameFunctions.isPlayerPlayingAndAlive(teammate);
            Role teammateRole = gameComponent.getRole(teammate);
            if (GrandWitchRules.shouldAwardWitchTeamKillMoney(killerRole, teammateRole, samePlayer, teammateAlive)) {
                PlayerShopComponent.KEY.get(teammate).addToBalance(GrandWitchRules.WITCH_TEAM_KILL_MONEY_REWARD);
            }
        }
    }
}
