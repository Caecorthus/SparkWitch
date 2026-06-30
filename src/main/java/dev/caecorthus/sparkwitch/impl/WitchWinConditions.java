package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionWinContext;
import dev.caecorthus.sparkfactionapi.api.FactionWinResult;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Witch faction round-end rules for SparkFactionAPI.
 * 魔女阵营的自定义结算规则，由 SparkFactionAPI 统一接入回合结束流程。
 */
public final class WitchWinConditions {
    private WitchWinConditions() {
    }

    public static FactionWinResult checkWin(FactionWinContext context) {
        WinSnapshot snapshot = snapshot(context);
        ShadowShowdownAction shadowShowdownAction = shadowShowdownAction(
                snapshot.livingWitchCount(),
                snapshot.livingNativeKillerCount(),
                snapshot.livingAlliedShadowJesterCount(),
                snapshot.livingOtherPlayerCount(),
                snapshot.shadowShowdownActive()
        );
        if (shadowShowdownAction == ShadowShowdownAction.TRIGGER_AND_BLOCK) {
            ShadowJesterShowdownBridge.activateShowdown(context.world(), snapshot.boundShadowJesters());
            return FactionWinResult.block();
        }
        if (shadowShowdownAction == ShadowShowdownAction.BLOCK) {
            return FactionWinResult.block();
        }

        return switch (winAction(snapshot.livingPlayerCount(), snapshot.livingWitchCount(), context.currentStatus())) {
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

    public static ShadowShowdownAction shadowShowdownAction(
            int livingWitchCount,
            int livingNativeKillerCount,
            int livingAlliedShadowJesterCount,
            int livingOtherPlayerCount,
            boolean shadowShowdownActive
    ) {
        if (livingWitchCount <= 0 || livingAlliedShadowJesterCount <= 0 || livingOtherPlayerCount > 0) {
            return ShadowShowdownAction.NONE;
        }
        if (shadowShowdownActive) {
            return ShadowShowdownAction.BLOCK;
        }
        return ShadowShowdownAction.TRIGGER_AND_BLOCK;
    }

    static boolean countsAsShadowShowdownOther(boolean lastStandTriggeredThisRound) {
        return !lastStandTriggeredThisRound;
    }

    private static WinSnapshot snapshot(FactionWinContext context) {
        int livingPlayerCount = 0;
        int livingWitchCount = 0;
        int livingNativeKillerCount = 0;
        int livingAlliedShadowJesterCount = 0;
        int livingOtherPlayerCount = 0;
        boolean shadowShowdownActive = false;
        List<ServerPlayerEntity> boundShadowJesters = new ArrayList<>();

        for (ServerPlayerEntity player : context.world().getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            livingPlayerCount++;
            Role role = context.gameComponent().getRole(player);
            if (GrandWitchRules.isWitchFactionMember(role)) {
                livingWitchCount++;
            } else if (NoellesRoleIds.isShadowJester(role) && ShadowJesterShowdownBridge.isAllied(player)) {
                livingAlliedShadowJesterCount++;
                boundShadowJesters.add(player);
                shadowShowdownActive = shadowShowdownActive || ShadowJesterShowdownBridge.isShowdownActive(player);
            } else if (role != null && role.canUseKiller()) {
                livingNativeKillerCount++;
            } else if (countsAsShadowShowdownOther(
                    SparkTraitsLastStandBridge.hasTriggeredThisRound(context.world(), player.getUuid())
            )) {
                // Last Stand survivors already paid a death for Shadow Jester showdown gating only.
                // 触发过背水一战的好人只在双影谢幕门槛里视作已经死过，不改变其他结算。
                livingOtherPlayerCount++;
            }
        }

        return new WinSnapshot(
                livingPlayerCount,
                livingWitchCount,
                livingNativeKillerCount,
                livingAlliedShadowJesterCount,
                livingOtherPlayerCount,
                shadowShowdownActive,
                List.copyOf(boundShadowJesters)
        );
    }

    public enum WinAction {
        NONE,
        BLOCK,
        FACTION_WIN
    }

    public enum ShadowShowdownAction {
        NONE,
        TRIGGER_AND_BLOCK,
        BLOCK
    }

    private record WinSnapshot(
            int livingPlayerCount,
            int livingWitchCount,
            int livingNativeKillerCount,
            int livingAlliedShadowJesterCount,
            int livingOtherPlayerCount,
            boolean shadowShowdownActive,
            List<ServerPlayerEntity> boundShadowJesters
    ) {
    }
}
