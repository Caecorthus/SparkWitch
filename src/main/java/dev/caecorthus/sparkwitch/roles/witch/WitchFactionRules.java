package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import java.util.OptionalInt;


/**
 * Pure Witch faction rule constants and predicates.
 * 魔女阵营的纯规则集中在这里，事件和 UI 只读取这些判断。
 */
public final class WitchFactionRules {
    public static final int WITCH_TEAM_KILL_MONEY_REWARD = 25;

    public static final int OTHER_WITCH_INSTINCT_COLOR = 0x7AB8FF;
    public static final int NON_WITCH_INSTINCT_COLOR = 0x36E51B;
    public static final int DROPPED_ITEM_INSTINCT_COLOR = 0xDB9D00;
    // Ordinary Witch instinct must stay below Wathe hard skips such as Last Stand and hidden Survival Master.
    // 普通魔女本能必须低于 wathe 硬跳过规则，例如背水一战和被遮挡的生存大师。
    public static final int INSTINCT_PRIORITY = 90;

    private WitchFactionRules() {
    }

    public static boolean isGrandWitch(Role role) {
        return role != null && role == SparkWitchRoles.grandWitch();
    }

    public static boolean isAccomplice(Role role) {
        return role != null && role == SparkWitchRoles.accomplice();
    }

    public static boolean isWitchFactionMember(Role role) {
        return role != null && (role == SparkWitchRoles.grandWitch() || role == SparkWitchRoles.accomplice());
    }

    public static boolean usesKillerStyleInstinctLight(Role role) {
        return isWitchFactionMember(role);
    }

    public static OptionalInt droppedItemInstinctColor(Role viewerRole) {
        return isWitchFactionMember(viewerRole)
                ? OptionalInt.of(DROPPED_ITEM_INSTINCT_COLOR)
                : OptionalInt.empty();
    }

    /**
     * Grand Witch faction highlights are living-player instincts; dead spectators use Wathe defaults.
     * 大魔女阵营高亮只属于存活玩家本能；死亡旁观者使用 wathe 默认逻辑。
     */
    public static boolean shouldUseCustomInstinctHighlight(boolean viewerAlive, boolean viewerSpectatingOrCreative) {
        return viewerAlive && !viewerSpectatingOrCreative;
    }

    public static boolean shouldUseCustomInstinctHighlight(boolean viewerAlive) {
        return shouldUseCustomInstinctHighlight(viewerAlive, false);
    }

    /**
     * Obscure blocks only active non-Witch instinct users; spectators keep Wathe information vision.
     * 障眼只遮蔽正在游玩的非魔女本能使用者；旁观者保留 wathe 信息透视。
     */
    public static boolean shouldObscureInstinct(
            boolean instinctObscured,
            Role viewerRole,
            boolean viewerAlive,
            boolean viewerSpectatingOrCreative
    ) {
        return shouldUseCustomInstinctHighlight(viewerAlive, viewerSpectatingOrCreative)
                && instinctObscured
                && isAffectedByWitchAreaSpell(viewerRole);
    }

    /**
     * Suppresses every instinct outline path for affected players while preserving global final-moment reveals.
     * 在恐惧/障眼期间压制受影响玩家的所有本能描边，但保留终局时刻这类全局揭示。
     */
    public static boolean shouldSuppressAffectedInstinctHighlight(
            boolean fearActive,
            boolean instinctObscured,
            Role viewerRole,
            boolean viewerAlive,
            boolean viewerSpectatingOrCreative,
            boolean finalMomentActive
    ) {
        if (finalMomentActive || !shouldUseCustomInstinctHighlight(viewerAlive, viewerSpectatingOrCreative)) {
            return false;
        }
        boolean affectedByFear = fearActive && isAffectedByFear(viewerRole);
        boolean affectedByObscure = instinctObscured && isAffectedByWitchAreaSpell(viewerRole);
        return affectedByFear || affectedByObscure;
    }

    public static boolean isOtherWitchRole(Role role) {
        return role != null && (role == SparkWitchRoles.murderousWitch() || role == SparkWitchRoles.apprenticeWitch());
    }

    public static boolean isAffectedByWitchAreaSpell(Role role) {
        return !isWitchFactionMember(role);
    }

    public static boolean isAffectedByFear(Role role) {
        return role != null && isAffectedByWitchAreaSpell(role);
    }

    public static OptionalInt instinctColor(Role viewerRole, Role targetRole) {
        if (isGrandWitch(viewerRole)) {
            if (targetRole == SparkWitchRoles.grandWitch()) {
                return OptionalInt.of(SparkWitchRoles.grandWitch().color());
            }
            if (targetRole == SparkWitchRoles.accomplice()) {
                return OptionalInt.of(SparkWitchRoles.accomplice().color());
            }
            if (isOtherWitchRole(targetRole)) {
                return OptionalInt.of(OTHER_WITCH_INSTINCT_COLOR);
            }
            return OptionalInt.of(NON_WITCH_INSTINCT_COLOR);
        }
        if (isAccomplice(viewerRole)) {
            if (targetRole == SparkWitchRoles.grandWitch()) {
                return OptionalInt.of(SparkWitchRoles.grandWitch().color());
            }
            if (targetRole == SparkWitchRoles.accomplice()) {
                return OptionalInt.of(SparkWitchRoles.accomplice().color());
            }
            return OptionalInt.of(NON_WITCH_INSTINCT_COLOR);
        }
        return OptionalInt.empty();
    }

    public static Boolean economyDecision(Role role, FactionEconomyPolicy.RewardKind rewardKind) {
        if (isGrandWitch(role)) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL) {
                return true;
            }
            if (rewardKind == FactionEconomyPolicy.RewardKind.PASSIVE) {
                return false;
            }
            return null;
        }
        if (isAccomplice(role)) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL
                    || rewardKind == FactionEconomyPolicy.RewardKind.PASSIVE) {
                return true;
            }
        }
        return null;
    }

    /**
     * Grand Witch direct kills grant the living Accomplice an additional teammate reward.
     * 大魔女直接击杀会给存活的共犯队友额外发放一份队友奖励。
     */
    public static boolean shouldAwardWitchTeamKillMoney(
            Role killerRole,
            Role teammateRole,
            boolean samePlayer,
            boolean teammateAlive
    ) {
        return isGrandWitch(killerRole)
                && isAccomplice(teammateRole)
                && !samePlayer
                && teammateAlive;
    }

}
