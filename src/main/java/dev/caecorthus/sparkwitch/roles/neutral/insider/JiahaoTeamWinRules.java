package dev.caecorthus.sparkwitch.roles.neutral.insider;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Team Jiahao win decisions (D2, D7), pure: member snapshots in, values out, no world access. They feed the three
 * wraps inside NoellesRoles' Corrupt Cop win branch, so NoellesRoles keeps its own win order and its
 * {@code aliveCount == 1} test, and the round-end row order that picks Wathe's end title.
 * <p>
 * Rule: members are every Corrupt Cop and every Insider. The team wins when at least one member is alive and not
 * swallowed and every other alive, not-swallowed player is a member. Every member present wins, dead or alive; an
 * Insider leads the winners when the round has one. While any member is alive, killers and passengers cannot win.
 * <p>
 * 嘉豪阵营胜负判断（D2、D7），纯规则：输入成员快照，输出结果，不访问世界。结果供 NoellesRoles 黑警胜负分支里的三个
 * 包装使用，因此 NoellesRoles 保留自己的胜利优先级与 {@code aliveCount == 1} 判断；另决定回合结算行的顺序，
 * 即 Wathe 结算标题读取哪一行。
 * <p>
 * 规则：所有黑警与所有内应都是成员。至少一名成员存活且未被吞，并且其余存活且未被吞的玩家全是成员时，阵营获胜。
 * 在场的所有成员不论死活一起获胜；本局有内应时由内应领衔。只要有成员存活，杀手和乘客都无法获胜。
 */
public final class JiahaoTeamWinRules {
    private static final int DEAD_RANK = 4;

    private JiahaoTeamWinRules() {
    }

    /**
     * One Team Jiahao member at win-check time. {@code alive} is Wathe's playing-and-alive (so it implies
     * {@code online}); {@code swallowed} is NoellesRoles' Taotie swallow flag.
     * 胜负判定时的一名嘉豪阵营成员。{@code alive} 即 Wathe 的“参与且存活”（因此必然 {@code online}）；
     * {@code swallowed} 为 NoellesRoles 饕餮吞噬标记。
     */
    public record Member(UUID id, boolean insider, boolean online, boolean alive, boolean swallowed) {
    }

    /** The team's winners: the primary drives the end title, co-winners are marked winners too. / 阵营胜者：主胜者决定结算标题，共同胜者同样计为获胜。 */
    public record Winners(UUID primary, List<UUID> coWinners) {
    }

    /** One Wathe round-end row as the end-title loop reads it. / Wathe 结算标题循环读取的一行结算数据。 */
    public record RoundEndRow(Identifier role, boolean winner) {
    }

    /**
     * Replacement for NoellesRoles' "all Corrupt Cops" lookup list: every member, ordered so the branch's
     * first-alive pick is the team's {@link #representative}. Preference: alive and not swallowed, then alive but
     * swallowed, then the rest; a Corrupt Cop before an Insider inside each group; snapshot order otherwise.
     * 替换 NoellesRoles“所有黑警”查找列表：包含全部成员，排序保证该分支“第一个存活者”恰为阵营的
     * {@link #representative}。优先级：存活且未被吞，其次存活但被吞，最后其余成员；同组内黑警先于内应，其余保持快照顺序。
     */
    public static List<UUID> lookupOrder(List<Member> members) {
        List<Member> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparingInt(JiahaoTeamWinRules::lookupRank));
        return ordered.stream().map(Member::id).toList();
    }

    /**
     * The member NoellesRoles' branch treats as its living Corrupt Cop, or {@code null} when no member is alive (the
     * branch then neither blocks nor wins). Any alive member, even a swallowed one, keeps the killer/passenger block.
     * NoellesRoles 分支视作“存活黑警”的成员；没有存活成员时为 {@code null}（分支既不阻止也不获胜）。任何存活成员，
     * 即使被吞，也会维持对杀手与乘客的阻止。
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
     * NoellesRoles' {@code aliveCount} loop: the alive team counts once, through its representative, so
     * {@code aliveCount == 1} holds exactly when only members are alive and not swallowed. Non-members keep the host
     * answer.
     * NoellesRoles 的 {@code aliveCount} 循环：存活的阵营只通过其代表计一次，因此 {@code aliveCount == 1} 恰在只剩
     * 未被吞的成员存活时成立。非成员保持宿主结果。
     */
    public static boolean countsTowardAliveCount(boolean hostAlive, boolean member, boolean representative) {
        return hostAlive && (!member || representative);
    }

    /**
     * Winners once NoellesRoles grants the branch's win to {@code livingMember}. Primary (D7): the first alive
     * Insider, else the first Insider, else {@code livingMember}; only online players can be named. Co-winners: every
     * other online member, dead or alive.
     * NoellesRoles 将分支胜利判给 {@code livingMember} 后的胜者。主胜者（D7）：第一个存活内应，否则第一个内应，否则
     * {@code livingMember}；只能列出在线玩家。共同胜者：其余所有在线成员，不论死活。
     */
    public static Winners winners(List<Member> members, UUID livingMember) {
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
     * Whether the winners are exactly NoellesRoles' own single winner (a lone Corrupt Cop): the host call then runs
     * unchanged.
     * 胜者是否恰为 NoellesRoles 自身的单一胜者（只有一名黑警）：此时原样执行宿主调用。
     */
    public static boolean isHostWin(Winners winners, UUID livingMember) {
        return winners.coWinners().isEmpty() && winners.primary().equals(livingMember);
    }

    /**
     * Wathe titles a neutral win with the first winning row, and rows follow its role map, not the primary. Returns
     * the index of the first winning Insider row when a non-Insider row would title the screen, else {@code -1} (no
     * winning Insider, or an Insider already leads). Only a Team Jiahao win has a winning Insider.
     * Wathe 以第一行获胜数据作为中立胜利标题，而行顺序来自其职业表而非主胜者。当标题会取自非内应行时，返回第一条获胜
     * 内应行的下标，否则返回 {@code -1}（没有获胜内应，或已由内应领衔）。只有嘉豪阵营胜利才会出现获胜的内应。
     */
    public static int titleLeadRow(List<RoundEndRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            RoundEndRow row = rows.get(i);
            if (!row.winner()) {
                continue;
            }
            if (InsiderRules.ROLE_ID.equals(row.role())) {
                return -1;
            }
            for (int j = i + 1; j < rows.size(); j++) {
                if (rows.get(j).winner() && InsiderRules.ROLE_ID.equals(rows.get(j).role())) {
                    return j;
                }
            }
            return -1;
        }
        return -1;
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
