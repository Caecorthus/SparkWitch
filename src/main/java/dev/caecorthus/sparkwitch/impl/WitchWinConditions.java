package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionWinContext;
import dev.caecorthus.sparkfactionapi.api.FactionWinResult;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Custom Witch faction round-end rules for SparkFactionAPI.
 * 魔女阵营的自定义结算规则，由 SparkFactionAPI 统一接入回合结束流程。
 */
public final class WitchWinConditions {
    private WitchWinConditions() {
    }

    public static FactionWinResult checkWin(FactionWinContext context) {
        int livingPlayerCount = 0;
        int livingWitchCount = 0;

        for (ServerPlayerEntity player : context.world().getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            livingPlayerCount++;
            Role role = context.gameComponent().getRole(player);
            if (GrandWitchRules.isWitchFactionMember(role)) {
                livingWitchCount++;
            }
        }

        return switch (winAction(livingPlayerCount, livingWitchCount, context.currentStatus())) {
            case FACTION_WIN -> FactionWinResult.factionWin(context.factionId());
            case BLOCK -> FactionWinResult.block();
            case NONE -> FactionWinResult.none();
        };
    }

    public static WinAction winAction(
            int livingPlayerCount,
            int livingWitchCount,
            GameFunctions.WinStatus currentStatus
    ) {
        if (livingWitchCount > 0 && livingWitchCount == livingPlayerCount) {
            return WinAction.FACTION_WIN;
        }
        if (livingWitchCount > 0
                && (currentStatus == GameFunctions.WinStatus.KILLERS
                || currentStatus == GameFunctions.WinStatus.PASSENGERS)) {
            return WinAction.BLOCK;
        }
        return WinAction.NONE;
    }

    public enum WinAction {
        NONE,
        BLOCK,
        FACTION_WIN
    }
}
