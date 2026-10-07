package dev.caecorthus.sparkwitch.economy;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
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

    /**
     * Server-side task pay for the Grand Witch and every accomplice; eligibility lives in
     * {@link WitchFactionRules#earnsTaskMoney}. SparkWitch owns this income: Wathe, SparkStrength and NoellesRoles pay
     * witch roles nothing per task.
     * 大魔女与所有共犯的服务端任务收入；资格判定见 {@link WitchFactionRules#earnsTaskMoney}。该收入由 SparkWitch 负责：
     * wathe、SparkStrength 与 NoellesRoles 都不按任务给魔女职业发钱。
     */
    public static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (WitchFactionRules.earnsTaskMoney(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player), player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(WitchFactionRules.WITCH_TASK_MONEY_REWARD);
        }
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
        if (!WitchFactionRules.isGrandWitch(killerRole)) {
            return;
        }

        for (ServerPlayerEntity teammate : victim.getServerWorld().getPlayers()) {
            boolean samePlayer = teammate.getUuid().equals(killer.getUuid());
            boolean teammateAlive = GameFunctions.isPlayerPlayingAndAlive(teammate);
            Role teammateRole = gameComponent.getRole(teammate);
            if (WitchFactionRules.shouldAwardWitchTeamKillMoney(killerRole, teammateRole, samePlayer, teammateAlive)) {
                PlayerShopComponent.KEY.get(teammate).addToBalance(WitchFactionRules.WITCH_TEAM_KILL_MONEY_REWARD);
            }
        }
    }
}
