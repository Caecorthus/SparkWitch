package dev.caecorthus.sparkwitch.roles.witch;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaRules;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.doctor4t.wathe.api.Role;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;


/**
 * Pure Witch faction rule constants and predicates.
 * 魔女阵营的纯规则集中在这里，事件和 UI 只读取这些判断。
 */
public final class WitchFactionRules {
    public static final int WITCH_TEAM_KILL_MONEY_REWARD = 25;
    public static final int WITCH_TASK_MONEY_REWARD = 25;

    public static final int OTHER_WITCH_INSTINCT_COLOR = 0x7AB8FF;
    public static final int NON_WITCH_INSTINCT_COLOR = 0x36E51B;
    public static final int DROPPED_ITEM_INSTINCT_COLOR = 0xDB9D00;
    // Ordinary Witch instinct must stay below Wathe hard skips such as Last Stand and hidden Survival Master.
    // 普通魔女本能必须低于 wathe 硬跳过规则，例如背水一战和被遮挡的生存大师。
    public static final int INSTINCT_PRIORITY = 90;
    public static final int HIDDEN_PHANTOM_SKIP_PRIORITY = 1_000;

    /**
     * Role ids of the 魔裁 characters (see {@link #isWitchTrialGirl}). A new 魔裁 role adds its id here. Ids, not
     * {@link Role} instances, so the set needs no registration order; 橘雪莉 ({@code sparkwitch:sherry}, PR #206) is
     * listed ahead of its merge and matches nothing until that role registers.
     * 魔裁角色的职业 ID（见 {@link #isWitchTrialGirl}）。新增魔裁职业时把 ID 加到这里。用 ID 而非 {@link Role} 实例，
     * 因此不依赖注册顺序；橘雪莉（{@code sparkwitch:sherry}，PR #206）在合并前先行列入，该职业注册之前不会匹配任何对象。
     */
    private static final Set<Identifier> WITCH_TRIAL_GIRL_IDS = Set.of(
            EmmaRules.ROLE_ID,
            NoaRules.ROLE_ID,
            HannaRules.ROLE_ID,
            NatsumeRules.ROLE_ID,
            SparkWitch.id("sherry")
    );

    private WitchFactionRules() {
    }

    public static boolean isGrandWitch(Role role) {
        return role != null && role == SparkWitchRoles.grandWitch();
    }

    public static boolean isAccomplice(Role role) {
        return role != null && role == SparkWitchRoles.accomplice();
    }

    /**
     * Exactly the Bewitched (魔化使), the not-yet-promoted accomplice dealt at round start. It is accomplice-like (C2)
     * but never an {@link AccompliceVariants} entry, so it is never rolled.
     * 恰为魔化使，即开局发放、尚未晋升的共犯。它属于共犯类（C2），但从不是 {@link AccompliceVariants} 条目，因此永远不会被抽到。
     */
    public static boolean isBewitched(Role role) {
        return role != null && role == SparkWitchRoles.bewitched();
    }

    /**
     * The plain Accomplice, any registered special accomplice, or the Bewitched (C2: it shares every basic accomplice
     * rule before its promotion). Use it for "basic accomplice" rules; keep {@link #isAccomplice} exact for rules owned
     * by the plain Accomplice itself (such as its shop).
     * 普通共犯、任一已注册的特殊共犯或魔化使（C2：晋升前即共享所有共犯基础规则）。"共犯基础功能"规则用它；普通共犯自有的
     * 规则（如其商店）仍用精确的 isAccomplice。
     */
    public static boolean isAccompliceLike(Role role) {
        return isAccomplice(role) || AccompliceVariants.isVariant(role) || isBewitched(role);
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
     * Grand Witch, any accomplice (plain, special or Bewitched) and the promoted Curser. Win counts, blackout,
     * Fear/Obscure immunity, the cohort label and Curser visibility all read this.
     * 大魔女、任一共犯（普通、特殊或魔化使）与晋升的诅咒者。胜利计数、停电、恐惧/遮蔽免疫、同伙标签和诅咒者可见性都读取它。
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
     * Whether the Witch instinct policy must answer an explicit skip for a player target when it gives the viewer no
     * Witch instinct (an ineligible Curser, or a viewer that is not Wathe-alive). A null answer for a Witch-faction
     * viewer reaches SparkFactionAPI's client fallback, which outlines every player in its faction colour on the
     * instinct key and so tells killers and neutrals apart. Only a Wathe-dead spectator keeps the null answer: it
     * falls through to Wathe's spectator information colours (the same gate SparkFactionAPI applies). Other viewers
     * are not this policy's to answer.
     * 魔女本能策略不给观察者魔女本能时（不合格的诅咒者，或 wathe 判定未存活的观察者），是否必须对玩家目标显式跳过。
     * 对魔女阵营观察者返回 null 会落入 SparkFactionAPI 客户端兜底：按住本能键时以阵营色描边所有玩家，从而分辨杀手与中立。
     * 只有已死亡的旁观者保留 null，回落到 wathe 旁观信息颜色（与 SparkFactionAPI 的门槛一致）；其他观察者不由此策略作答。
     */
    public static boolean shouldSkipUnansweredViewer(
            boolean witchFactionViewer,
            boolean viewerAlive,
            boolean viewerSpectatingOrCreative
    ) {
        return witchFactionViewer && (viewerAlive || !viewerSpectatingOrCreative);
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

    /**
     * The roles the Grand Witch's instinct reads as witches outside her faction ({@link #OTHER_WITCH_INSTINCT_COLOR}):
     * the Murderous Witch, the Apprentice Witch and every Witch Trial girl ({@link #isWitchTrialGirl}). Only
     * {@link #instinctColor} reads it; it is not Witch faction membership.
     * 大魔女本能视为「阵营外的魔女」的职业（{@link #OTHER_WITCH_INSTINCT_COLOR}）：杀意魔女、预备魔女以及所有魔裁少女
     * （{@link #isWitchTrialGirl}）。只有 {@link #instinctColor} 读取它；它不等于魔女阵营成员。
     */
    public static boolean isOtherWitchRole(Role role) {
        return role != null && (role == SparkWitchRoles.murderousWitch()
                || role == SparkWitchRoles.apprenticeWitch()
                || isWitchTrialGirl(role));
    }

    /**
     * Whether the role is a 魔法少女的魔女裁判 (魔裁) character, by role id ({@link #WITCH_TRIAL_GIRL_IDS}). Owner
     * 2026-11-11: they count as witch roles for the Grand Witch's instinct, whatever their own faction. This grants
     * nothing else: no Witch faction membership, mana, factor or kill-mana bonus, Rift Gate access, trait pool or
     * {@code gui.sparkwitch.skills} panel.
     * 该职业是否为《魔法少女的魔女裁判》（魔裁）角色，按职业 ID 判定（{@link #WITCH_TRIAL_GIRL_IDS}）。所有者 2026-11-11：
     * 无论自身阵营，她们在大魔女的本能里都算魔女职业。除此之外不授予任何东西：不是魔女阵营成员，不影响魔力、因子与击杀
     * 魔力加成、裂隙门权限、词条池，也不进入 {@code gui.sparkwitch.skills} 面板。
     */
    public static boolean isWitchTrialGirl(@Nullable Role role) {
        return role != null && WITCH_TRIAL_GIRL_IDS.contains(role.identifier());
    }

    public static boolean isAffectedByWitchAreaSpell(Role role) {
        return !isWitchFactionMember(role);
    }

    public static boolean isAffectedByFear(Role role) {
        return role != null && isAffectedByWitchAreaSpell(role);
    }

    /**
     * Witch instinct colors. The Grand Witch and every accomplice see each accomplice (plain, special or Bewitched) in
     * that target's own role color, so the Grand Witch can tell a Bewitched from each promoted special accomplice.
     * 魔女本能颜色。大魔女和所有共犯都以目标自身的职业颜色看到每个共犯（普通、特殊或魔化使），因此大魔女能分辨魔化使与
     * 晋升后的各种特殊共犯。
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
