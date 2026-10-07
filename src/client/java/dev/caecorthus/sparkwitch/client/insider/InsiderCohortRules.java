package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Insider name-tag cohort rules: the Insider and the Corrupt Cop read each other as "嘉豪同伙" (D7, C9). Killers
 * get no cohort line on the Insider (owner 2026-10-05, replacing D3's "杀手同伙").
 * 内应名牌同伙提示的纯规则：内应与黑警互相显示“嘉豪同伙”（D7、C9）。杀手看内应时没有同伙提示（所有者 2026-10-05
 * 决定，取代 D3 的“杀手同伙”）。
 */
public final class InsiderCohortRules {
    /** Lang key of the Team Jiahao gold "嘉豪同伙" label. / 嘉豪阵营金色“嘉豪同伙”标签的语言键。 */
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
}
