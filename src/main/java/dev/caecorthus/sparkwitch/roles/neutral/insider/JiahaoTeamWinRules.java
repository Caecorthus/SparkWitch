package dev.caecorthus.sparkwitch.roles.neutral.insider;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Team Jiahao win decisions (D2, D7, C13, C14), pure: member snapshots in, values out, no world access. They feed the
 * three wraps inside NoellesRoles' Corrupt Cop win branch, so NoellesRoles keeps its own win order and its
 * {@code aliveCount == 1} test, and the round-end rows that Wathe's end title and win records read.
 * <p>
 * Rule: members are every Corrupt Cop and every Insider. The rules apply only in a round that has an Insider (any
 * role-map entry with the Insider role, online or not, dead or alive); without one, every answer is NoellesRoles' own,
 * so several Corrupt Cops behave exactly as in NoellesRoles (C13). With an Insider, the team wins when at least one
 * member is alive and not swallowed and every other alive, not-swallowed player is a member. Every member wins, dead,
 * alive or offline (C14), and an Insider leads the winners. While any member is alive, killers and passengers cannot
 * win.
 * <p>
 * 嘉豪阵营胜负判断（D2、D7、C13、C14），纯规则：输入成员快照，输出结果，不访问世界。结果供 NoellesRoles 黑警胜负分支里的
 * 三个包装使用，因此 NoellesRoles 保留自己的胜利优先级与 {@code aliveCount == 1} 判断；另决定 Wathe 结算标题与胜负记录
 * 读取的回合结算行。
 * <p>
 * 规则：所有黑警与所有内应都是成员。只有本局有内应（职业表中存在内应条目，不论在线与否、死活）时才生效；没有内应时
 * 所有结果都是 NoellesRoles 原值，多名黑警的表现与 NoellesRoles 完全一致（C13）。有内应时，至少一名成员存活且未被吞，
 * 并且其余存活且未被吞的玩家全是成员，阵营即获胜。所有成员不论死活、是否在线一起获胜（C14），由内应领衔。只要有成员
 * 存活，杀手和乘客都无法获胜。
 */
public final class JiahaoTeamWinRules {
    private static final int DEAD_RANK = 4;

    private JiahaoTeamWinRules() {
    }

    /**
     * One Team Jiahao member at win-check time, one per role-map entry with a team role. {@code alive} is Wathe's
     * playing-and-alive (so it implies {@code online}); {@code swallowed} is NoellesRoles' Taotie swallow flag.
     * 胜负判定时的一名嘉豪阵营成员，对应职业表中每个阵营职业条目。{@code alive} 即 Wathe 的“参与且存活”（因此必然
     * {@code online}）；{@code swallowed} 为 NoellesRoles 饕餮吞噬标记。
     */
    public record Member(UUID id, boolean insider, boolean online, boolean alive, boolean swallowed) {
    }

    /** The team's winners: the primary drives the end title, co-winners are marked winners too. / 阵营胜者：主胜者决定结算标题，共同胜者同样计为获胜。 */
    public record Winners(UUID primary, List<UUID> coWinners) {
    }

    /** One Wathe round-end row as the title seam reads it. / 结算标题接缝读取的一行 Wathe 结算数据。 */
    public record RoundEndRow(Identifier role, boolean winner) {
    }

    /**
     * How the title seam rewrites Wathe's rows: the row indices to mark as winners, then the row to move first
     * ({@code -1} keeps the order).
     * 结算标题接缝对 Wathe 结算行的改写：需要标记为获胜的行下标，以及要移到最前的行（{@code -1} 表示保持顺序）。
     */
    public record RoundEndCredit(List<Integer> newWinners, int leadRow) {
        public static final RoundEndCredit NONE = new RoundEndCredit(List.of(), -1);
    }

    /**
     * Whether the Team Jiahao rules apply (C13): the snapshot holds an Insider. The snapshot has every role-map entry
     * with a team role, so this is "the round has an Insider", online or not, dead or alive.
     * 嘉豪阵营规则是否生效（C13）：快照中有内应。快照包含职业表中每个阵营职业条目，因此这就是“本局有内应”，不论在线与否、死活。
     */
    public static boolean appliesTo(List<Member> members) {
        for (Member member : members) {
            if (member.insider()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replacement for NoellesRoles' "all Corrupt Cops" lookup list, or {@code hostLookup} unchanged when the rules do
     * not apply. Otherwise every member, ordered so the branch's first-alive pick is the team's
     * {@link #representative}: alive and not swallowed, then alive but swallowed, then the rest; a Corrupt Cop before
     * an Insider inside each group; snapshot order otherwise.
     * 替换 NoellesRoles“所有黑警”查找列表；规则不生效时原样返回 {@code hostLookup}。否则包含全部成员，排序保证该分支
     * “第一个存活者”恰为阵营的 {@link #representative}：存活且未被吞，其次存活但被吞，最后其余成员；同组内黑警先于
     * 内应，其余保持快照顺序。
     */
    public static List<UUID> lookup(List<Member> members, List<UUID> hostLookup) {
        return appliesTo(members) ? lookupOrder(members) : hostLookup;
    }

    /** The team lookup order alone, see {@link #lookup}. / 仅阵营查找顺序，见 {@link #lookup}。 */
    public static List<UUID> lookupOrder(List<Member> members) {
        List<Member> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparingInt(JiahaoTeamWinRules::lookupRank));
        return ordered.stream().map(Member::id).toList();
    }

    /**
     * The member NoellesRoles' branch treats as its living Corrupt Cop when the rules apply, or {@code null} when no
     * member is alive (the branch then neither blocks nor wins). Any alive member, even a swallowed one, keeps the
     * killer/passenger block.
     * 规则生效时 NoellesRoles 分支视作“存活黑警”的成员；没有存活成员时为 {@code null}（分支既不阻止也不获胜）。任何存活
     * 成员，即使被吞，也会维持对杀手与乘客的阻止。
     */
    public static @Nullable UUID representative(List<Member> members) {
        Member best = null;
        for (Member member : members) {
            if (member.alive() && (best == null || lookupRank(member) < lookupRank(best))) {
                best = member;
            }
        }
        return best == null ? null : best.id();
    }

    /**
     * NoellesRoles' {@code aliveCount} loop, one answer per online player: when the rules apply, the alive team counts
     * once, through its representative, so {@code aliveCount == 1} holds exactly when only members are alive and not
     * swallowed. Non-members, and everyone in a round without an Insider, keep the host answer.
     * NoellesRoles 的 {@code aliveCount} 循环，每名在线玩家一个结果：规则生效时存活的阵营只通过其代表计一次，因此
     * {@code aliveCount == 1} 恰在只剩未被吞的成员存活时成立。非成员，以及没有内应的回合中的所有人，保持宿主结果。
     */
    public static boolean countsTowardAliveCount(boolean hostAlive, UUID player, List<Member> members) {
        if (!hostAlive || !appliesTo(members)) {
            return hostAlive;
        }
        boolean member = false;
        for (Member candidate : members) {
            member |= candidate.id().equals(player);
        }
        return !member || player.equals(representative(members));
    }

    /**
     * Winners once NoellesRoles grants the branch's win to {@code livingMember}. Without an Insider in the round this
     * is exactly NoellesRoles' own single winner (C13). Otherwise the primary (D7) is the first alive Insider, else the
     * first online Insider, else {@code livingMember}; co-winners are every other online member, dead or alive. Only
     * online players can be named here; offline members are credited on the round-end rows ({@link #roundEndCredit}).
     * NoellesRoles 将分支胜利判给 {@code livingMember} 后的胜者。本局没有内应时恰为 NoellesRoles 自身的单一胜者（C13）。
     * 否则主胜者（D7）为第一个存活内应，其次第一个在线内应，最后 {@code livingMember}；共同胜者为其余所有在线成员，不论死活。
     * 此处只能列出在线玩家；离线成员在回合结算行中计为获胜（{@link #roundEndCredit}）。
     */
    public static Winners winners(List<Member> members, UUID livingMember) {
        if (!appliesTo(members)) {
            return new Winners(livingMember, List.of());
        }
        UUID primary = primary(members, livingMember);
        List<UUID> coWinners = new ArrayList<>();
        for (Member member : members) {
            if (member.online() && !member.id().equals(primary)) {
                coWinners.add(member.id());
            }
        }
        return new Winners(primary, List.copyOf(coWinners));
    }

    /**
     * Whether the winners are exactly NoellesRoles' own single winner: the host call's result is then kept unchanged.
     * 胜者是否恰为 NoellesRoles 自身的单一胜者：此时保留宿主调用的结果。
     */
    public static boolean isHostWin(Winners winners, UUID livingMember) {
        return winners.coWinners().isEmpty() && winners.primary().equals(livingMember);
    }

    /**
     * Round-end rows of an explicit-winner (neutral) win, just before Wathe syncs them. A Team Jiahao win is being
     * recorded when the round has an Insider and a winning row has a team role (only NoellesRoles' wrapped Corrupt Cop
     * branch names a team member as a neutral winner). Then every other team row, including offline {@code LEFT} /
     * {@code LEFT_DEAD} rows, becomes a winner (C14), and an Insider row must be the first winning row, because Wathe
     * titles a neutral win with it (D7): the first Insider row the win already named, else the first Insider row. Every
     * other win, and every round without an Insider, returns {@link RoundEndCredit#NONE}.
     * 显式胜者（中立）胜利在 Wathe 同步前的回合结算行。本局有内应且某条获胜行是阵营职业时，即在记录嘉豪阵营胜利（只有
     * 被包装的 NoellesRoles 黑警分支会把阵营成员作为中立胜者）。此时其余所有阵营行（含离线的 {@code LEFT} /
     * {@code LEFT_DEAD} 行）都计为获胜（C14），并且第一条获胜行必须是内应行，因为 Wathe 以它作为中立胜利标题（D7）：优先
     * 选择胜利已列出的第一条内应行，否则选第一条内应行。其他胜利以及没有内应的回合返回 {@link RoundEndCredit#NONE}。
     */
    public static RoundEndCredit roundEndCredit(List<RoundEndRow> rows, boolean roundHasInsider) {
        if (!roundHasInsider || !hasWinningTeamRow(rows)) {
            return RoundEndCredit.NONE;
        }
        List<Integer> newWinners = new ArrayList<>();
        int namedInsider = -1;
        int anyInsider = -1;
        int firstWinner = -1;
        for (int i = 0; i < rows.size(); i++) {
            RoundEndRow row = rows.get(i);
            boolean team = isTeamRoleId(row.role());
            if (team && !row.winner()) {
                newWinners.add(i);
            }
            if ((row.winner() || team) && firstWinner < 0) {
                firstWinner = i;
            }
            if (InsiderRules.ROLE_ID.equals(row.role())) {
                if (anyInsider < 0) {
                    anyInsider = i;
                }
                if (row.winner() && namedInsider < 0) {
                    namedInsider = i;
                }
            }
        }
        int lead = namedInsider >= 0 ? namedInsider : anyInsider;
        return new RoundEndCredit(List.copyOf(newWinners), lead == firstWinner ? -1 : lead);
    }

    private static boolean hasWinningTeamRow(List<RoundEndRow> rows) {
        for (RoundEndRow row : rows) {
            if (row.winner() && isTeamRoleId(row.role())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTeamRoleId(Identifier role) {
        return InsiderRules.ROLE_ID.equals(role) || InsiderRules.CORRUPT_COP_ID.equals(role);
    }

    private static UUID primary(List<Member> members, UUID livingMember) {
        UUID firstInsider = null;
        for (Member member : members) {
            if (!member.insider() || !member.online()) {
                continue;
            }
            if (member.alive()) {
                return member.id();
            }
            if (firstInsider == null) {
                firstInsider = member.id();
            }
        }
        return firstInsider != null ? firstInsider : livingMember;
    }

    private static int lookupRank(Member member) {
        if (!member.alive()) {
            return DEAD_RANK;
        }
        return (member.swallowed() ? 2 : 0) + (member.insider() ? 1 : 0);
    }
}
