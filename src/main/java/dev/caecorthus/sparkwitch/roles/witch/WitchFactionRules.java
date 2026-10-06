package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.doctor4t.wathe.api.Role;
import java.util.OptionalInt;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;


/**
 * Pure Witch faction rule constants and predicates.
 * 魔女阵营的纯规则集中在这里，事件和 UI 只读取这些判断。
 */
public final class WitchFactionRules {
    public static final int WITCH_TEAM_KILL_MONEY_REWARD = 25;
    public static final int WITCH_TASK_MONEY_REWARD = 50;

    public static final int OTHER_WITCH_INSTINCT_COLOR = 0x7AB8FF;
    public static final int NON_WITCH_INSTINCT_COLOR = 0x36E51B;
    public static final int DROPPED_ITEM_INSTINCT_COLOR = 0xDB9D00;
    // Ordinary Witch instinct must stay below Wathe hard skips such as Last Stand and hidden Survival Master.
    // 普通魔女本能必须低于 wathe 硬跳过规则，例如背水一战和被遮挡的生存大师。
    public static final int INSTINCT_PRIORITY = 90;
    public static final int HIDDEN_PHANTOM_SKIP_PRIORITY = 1_000;

    private WitchFactionRules() {
    }

    public static boolean isGrandWitch(Role role) {
        return role != null && role == SparkWitchRoles.grandWitch();
    }

    public static boolean isAccomplice(Role role) {
        return role != null && role == SparkWitchRoles.accomplice();
    }

    /**
     * The plain Accomplice or any registered special accomplice. Use it for "basic accomplice" rules; keep
     * {@link #isAccomplice} exact for rules owned by the plain Accomplice itself (such as its shop).
     * 普通共犯或任一已注册的特殊共犯。"共犯基础功能"规则用它；普通共犯自有的规则（如其商店）仍用精确的 isAccomplice。
     */
    public static boolean isAccompliceLike(Role role) {
        return isAccomplice(role) || AccompliceVariants.isVariant(role);
    }

    /**
     * Id form of the variant half of {@link #isAccompliceLike}, for rules keyed on role ids whose id sets are built
     * at class initialization, before any variant registers. It reads the live registry on every call.
     * {@link #isAccompliceLike} 中特殊共犯部分的职业 ID 形式，供以职业 ID 为键的规则使用（这些 ID 集合在类初始化时建立，
     * 早于任何特殊共犯注册）。每次调用都读取实时注册表。
     */
    public static boolean isAccompliceVariantId(@Nullable Identifier roleId) {
        if (roleId == null) {
            return false;
        }
        for (Role variant : AccompliceVariants.variants()) {
            if (roleId.equals(variant.identifier())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grand Witch, any accomplice (plain or special) and the promoted Curser. Win counts, blackout, Fear/Obscure
     * immunity, the cohort label and Curser visibility all read this.
     * 大魔女、任一共犯（普通或特殊）与晋升的诅咒者。胜利计数、停电、恐惧/遮蔽免疫、同伙标签和诅咒者可见性都读取它。
     */
    public static boolean isWitchFactionMember(Role role) {
        return role != null && (role == SparkWitchRoles.grandWitch()
                || isAccompliceLike(role)
                || role == SparkWitchRoles.curser());
    }

    /**
     * Killer-style instinct visuals (lightmap, dropped-item outline, hidden-Phantom hard skip) for the Grand Witch and
     * every accomplice, special accomplices included (owner-approved parity). Still narrower than generic Witch-faction
     * membership: the Curser keeps its own rules.
     * 杀手式本能视觉（亮度过渡、掉落物描边、隐身幽灵硬跳过）属于大魔女和所有共犯，包括特殊共犯（所有者批准对齐）。
     * 仍窄于通用魔女阵营成员关系：诅咒者沿用自己的规则。
     */
    public static boolean usesKillerStyleInstinctLight(Role role) {
        return isGrandWitch(role) || isAccompliceLike(role);
    }

    public static boolean shouldHardSkipInvisiblePhantom(
            Role viewerRole,
            Role targetRole,
            boolean targetInvisible
    ) {
        return usesKillerStyleInstinctLight(viewerRole)
                && targetInvisible
                && NoellesRoleIds.isPhantom(targetRole);
    }

    public static OptionalInt droppedItemInstinctColor(Role viewerRole) {
        return usesKillerStyleInstinctLight(viewerRole)
                ? OptionalInt.of(DROPPED_ITEM_INSTINCT_COLOR)
                : OptionalInt.empty();
    }

    /**
     * A Wathe-alive viewer keeps its own role instinct in every game mode; only a Wathe-dead spectator falls through to
     * Wathe's spectator information colours. A Rift Gate occupant is an alive spectator whose instinct colours stay
     * (D10), and a null answer for it would hand it SparkFactionAPI's faction-colour fallback, which reveals roles.
     * 存活（wathe 判定）的观察者在任何游戏模式下都保留自身职业本能；只有已死亡的旁观者回落到 wathe 旁观信息颜色。
     * 裂隙门内的玩家是存活的旁观者，本能颜色保留（D10）；若对其返回 null，会落入 SparkFactionAPI 的阵营色兜底而暴露身份。
     */
    public static boolean shouldUseCustomInstinctHighlight(boolean viewerAlive) {
        return viewerAlive;
    }

    /**
     * Obscure blocks only living non-Witch instinct users; dead spectators keep Wathe information vision.
     * 障眼只遮蔽存活的非魔女本能使用者；已死亡的旁观者保留 wathe 信息透视。
     */
    public static boolean shouldObscureInstinct(
            boolean instinctObscured,
            Role viewerRole,
            boolean viewerAlive
    ) {
        return shouldUseCustomInstinctHighlight(viewerAlive)
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
            boolean finalMomentActive
    ) {
        if (finalMomentActive || !shouldUseCustomInstinctHighlight(viewerAlive)) {
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

    /**
     * Witch instinct colors. The Grand Witch and every accomplice see each accomplice (plain or special) in that
     * target's own role color, so the Grand Witch can tell which special accomplice she recruited.
     * 魔女本能颜色。大魔女和所有共犯都以目标自身的职业颜色看到每个共犯（普通或特殊），因此大魔女能分辨招到的是哪种特殊共犯。
     */
    public static OptionalInt instinctColor(Role viewerRole, Role targetRole) {
        if (isGrandWitch(viewerRole)) {
            if (targetRole == SparkWitchRoles.grandWitch()) {
                return OptionalInt.of(SparkWitchRoles.grandWitch().color());
            }
            if (isAccompliceLike(targetRole)) {
                return OptionalInt.of(targetRole.color());
            }
            if (isOtherWitchRole(targetRole)) {
                return OptionalInt.of(OTHER_WITCH_INSTINCT_COLOR);
            }
            return OptionalInt.of(NON_WITCH_INSTINCT_COLOR);
        }
        if (isAccompliceLike(viewerRole) || viewerRole == SparkWitchRoles.curser()) {
            if (targetRole == SparkWitchRoles.grandWitch()) {
                return OptionalInt.of(SparkWitchRoles.grandWitch().color());
            }
            if (isAccompliceLike(targetRole)) {
                return OptionalInt.of(targetRole.color());
            }
            return OptionalInt.of(NON_WITCH_INSTINCT_COLOR);
        }
        return OptionalInt.empty();
    }

    public static Boolean economyDecision(Role role, FactionEconomyPolicy.RewardKind rewardKind) {
        if (role == SparkWitchRoles.curser()) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL
                    || rewardKind == FactionEconomyPolicy.RewardKind.PASSIVE) {
                return false;
            }
            return null;
        }
        if (isGrandWitch(role)) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL) {
                return true;
            }
            if (rewardKind == FactionEconomyPolicy.RewardKind.PASSIVE) {
                return false;
            }
            return null;
        }
        if (isAccompliceLike(role)) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL
                    || rewardKind == FactionEconomyPolicy.RewardKind.PASSIVE) {
                return true;
            }
        }
        return null;
    }

    /**
     * Grand Witch direct kills grant every living accomplice teammate (plain or special) an additional reward.
     * 大魔女直接击杀会给每个存活的共犯队友（普通或特殊）额外发放一份队友奖励。
     */
    public static boolean shouldAwardWitchTeamKillMoney(
            Role killerRole,
            Role teammateRole,
            boolean samePlayer,
            boolean teammateAlive
    ) {
        return isGrandWitch(killerRole)
                && isAccompliceLike(teammateRole)
                && !samePlayer
                && teammateAlive;
    }

    /**
     * Task pay (+{@link #WITCH_TASK_MONEY_REWARD}): an active, living, participating Grand Witch or accomplice (plain or
     * special) that is not a spectator, creative or Wraith-restricted. No Impostor skip, as for the Insider: witch roles
     * roll only universal SparkTraits, and SparkTraits bonuses stack on top. The Curser earns nothing here.
     * 任务收入（+{@link #WITCH_TASK_MONEY_REWARD}）：处于 ACTIVE、存活且参与对局、非旁观/创造、未受灵体限制的大魔女或共犯
     * （普通或特殊）。与内应一样不跳过内鬼：魔女职业只抽通用 SparkTraits 词条，其加成叠加在此之上。诅咒者不在此领钱。
     */
    public static boolean earnsTaskMoney(
            boolean active,
            boolean playingAndAlive,
            @Nullable Role role,
            boolean spectator,
            boolean creative,
            boolean wraithRestricted
    ) {
        return active && playingAndAlive && (isGrandWitch(role) || isAccompliceLike(role))
                && !spectator && !creative && !wraithRestricted;
    }

}
