package dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchRules;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;

/**
 * Pure rules for Murderous Witch's neutral-killer bridge.
 * 杀意魔女的中立杀手式桥接规则，保持纯判断方便测试和复用。
 */
public final class MurderousWitchRules {
    public static final int INSTINCT_COLOR = 0xC13838;
    // Ordinary Murderous Witch instinct must lose to Wathe hard skips for hidden targets.
    // 普通杀意魔女本能必须让位于 wathe 对隐藏目标的硬跳过规则。
    public static final int INSTINCT_PRIORITY = 90;
    public static final int HIDDEN_PHANTOM_SKIP_PRIORITY = 1_000;

    private MurderousWitchRules() {
    }

    public static boolean isMurderousWitch(Role role) {
        return role != null && role == SparkWitchRoles.murderousWitch();
    }

    public static boolean usesKillerStyleInstinctLight(Role role) {
        return isMurderousWitch(role);
    }

    public static boolean shouldHardSkipInvisiblePhantom(
            Role viewerRole,
            Role targetRole,
            boolean targetInvisible
    ) {
        return isMurderousWitch(viewerRole)
                && targetInvisible
                && NoellesRoleIds.isPhantom(targetRole);
    }

    /**
     * Murderous Witch's custom instinct follows Wathe's alive state, not the game mode: a living Rift Gate occupant
     * (alive spectator) keeps it, so it never reaches SparkFactionAPI's role-revealing faction-colour fallback; only
     * dead spectators use Wathe defaults.
     * 杀意魔女的自定义本能取决于 wathe 存活状态而非游戏模式：存活的裂隙门内玩家（存活旁观者）保留该本能，
     * 因而不会落入暴露身份的 SparkFactionAPI 阵营色兜底；只有死亡旁观者使用 wathe 默认逻辑。
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
