package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;

/**
 * Pure rules for Murderous Witch's neutral-killer bridge.
 * 杀意魔女的中立杀手式桥接规则，保持纯判断方便测试和复用。
 */
public final class MurderousWitchRules {
    public static final int INSTINCT_COLOR = 0xC13838;
    public static final int INSTINCT_PRIORITY = 220;

    private MurderousWitchRules() {
    }

    public static boolean isMurderousWitch(Role role) {
        return role != null && role == SparkWitchRoles.murderousWitch();
    }

    public static boolean usesKillerStyleInstinctLight(Role role) {
        return isMurderousWitch(role);
    }

    /**
     * Murderous Witch's custom instinct is active-only; dead spectators use Wathe defaults.
     * 杀意魔女的自定义本能只在存活时生效；死亡旁观者使用 wathe 默认逻辑。
     */
    public static boolean shouldUseCustomInstinctHighlight(boolean viewerAlive) {
        return viewerAlive;
    }

    public static Boolean economyDecision(Role role, FactionEconomyPolicy.RewardKind rewardKind) {
        if (!isMurderousWitch(role)) {
            return null;
        }
        return switch (rewardKind) {
            case PASSIVE, DIRECT_KILL -> Boolean.TRUE;
            case TEAM_KILL -> Boolean.FALSE;
            case TASK -> null;
        };
    }

    public static boolean shouldHighlightInstinctTarget(
            boolean viewerAlive,
            boolean samePlayer,
            boolean targetAlive,
            boolean targetSpectatingOrCreative
    ) {
        return shouldUseCustomInstinctHighlight(viewerAlive)
                && !samePlayer
                && targetAlive
                && !targetSpectatingOrCreative;
    }

    public static WinAction winAction(
            int livingPlayerCount,
            int livingMurderousWitchCount,
            GameFunctions.WinStatus currentStatus
    ) {
        if (livingPlayerCount == 1 && livingMurderousWitchCount == 1) {
            return WinAction.NEUTRAL_WIN;
        }
        if (livingMurderousWitchCount > 0
                && (currentStatus == GameFunctions.WinStatus.KILLERS
                || currentStatus == GameFunctions.WinStatus.PASSENGERS)) {
            return WinAction.BLOCK;
        }
        return WinAction.NONE;
    }

    public enum WinAction {
        NONE,
        BLOCK,
        NEUTRAL_WIN
    }
}
