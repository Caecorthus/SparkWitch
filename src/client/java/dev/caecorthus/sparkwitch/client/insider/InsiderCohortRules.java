package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Insider name-tag cohort rules: killers read the Insider as a killer cohort (D3), and the Insider and the
 * Corrupt Cop read each other as "嘉豪同伙" (D7, C9). The two never apply to the same viewer/target pair.
 * 内应名牌同伙提示的纯规则：杀手把内应视为“杀手同伙”（D3），内应与黑警互相显示“嘉豪同伙”（D7、C9）。
 * 两者永远不会作用于同一对观察者与目标。
 */
public final class InsiderCohortRules {
    /**
     * Wathe {@code ShouldShowCohort} priority for killer -> Insider. SparkTraits answers every pair it cares about at
     * {@code PRIORITY_HIGH} (100) and never answers a plain or Impostor killer looking at an Insider; this sits above
     * it and below SparkWitch's witch-pair hide (110). A Wraith viewer's hide wraps the whole event and always wins.
     * 杀手 -> 内应的 Wathe {@code ShouldShowCohort} 优先级。SparkTraits 对其关心的配对都以 {@code PRIORITY_HIGH}（100）
     * 作答，且从不为普通杀手或内鬼看内应作答；本值高于它、低于 SparkWitch 魔女配对隐藏（110）。冤魂观察者的隐藏包住整个事件，始终优先。
     */
    public static final int KILLER_COHORT_PRIORITY = 105;
    /** Lang key of the mint "嘉豪同伙" label. / 薄荷青“嘉豪同伙”标签的语言键。 */
    public static final String JIAHAO_COHORT_KEY = "game.tip.sparkwitch.jiahao_cohort";

    private InsiderCohortRules() {
    }

    /**
     * "嘉豪同伙" pair: both roles are Team Jiahao members and at least one is an Insider (C9: Insider <-> Corrupt Cop,
     * both ways). Two Corrupt Cops alone never form a pair, so rounds without an Insider keep NoellesRoles' Corrupt Cop
     * unchanged. The caller also requires viewer != target.
     * “嘉豪同伙”配对：双方都是嘉豪阵营成员，且至少一方是内应（C9：内应与黑警双向）。只有两个黑警时不构成配对，
     * 因此没有内应的对局中 NoellesRoles 黑警保持不变。调用方还需保证观察者不是目标本人。
     */
    public static boolean isJiahaoCohortPair(@Nullable Role viewerRole, @Nullable Role targetRole) {
        return InsiderParticipation.isTeamRole(viewerRole)
                && InsiderParticipation.isTeamRole(targetRole)
                && (InsiderParticipation.isInsiderRole(viewerRole) || InsiderParticipation.isInsiderRole(targetRole));
    }

    /**
     * Whether a killer sees Wathe's red "杀手同伙" on this target (D3). The viewer must be a living SparkTraits
     * effective killer, exactly the viewers that see the tag on a real Impostor: Wathe killer features without the
     * Conscience trait, or the Impostor trait. The target must be another living Insider.
     * 杀手是否在该目标上看到 Wathe 红色“杀手同伙”（D3）。观察者必须是存活的 SparkTraits 有效杀手，即看得到真实内鬼
     * 同伙提示的那些观察者：拥有 Wathe 杀手功能且没有善良词条，或拥有内鬼词条。目标必须是其他存活的内应。
     */
    public static boolean killerSeesInsiderCohort(boolean viewerPlayingAndAlive, boolean viewerCanUseKillerFeatures,
                                                  boolean viewerImpostor, boolean viewerConscience,
                                                  @Nullable Role targetRole, boolean targetPlayingAndAlive,
                                                  boolean samePlayer) {
        boolean effectiveKiller = (viewerCanUseKillerFeatures && !viewerConscience) || viewerImpostor;
        return viewerPlayingAndAlive
                && effectiveKiller
                && !samePlayer
                && targetPlayingAndAlive
                && InsiderParticipation.isInsiderRole(targetRole);
    }
}
