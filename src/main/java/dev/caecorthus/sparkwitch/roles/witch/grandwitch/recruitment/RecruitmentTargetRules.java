package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Pure target choice for one recruitment (owner request 2026-10-06): the forced player of this recruitment number when
 * one is set and still pickable, else a uniform pick among the pickable living players.
 * 单次招募的纯选人规则（所有者 2026-10-06 要求）：若本次招募序号设置了强制玩家且其仍可被选中则选中该玩家，否则在可被选中的
 * 存活玩家中均匀随机。
 */
public final class RecruitmentTargetRules {
    private RecruitmentTargetRules() { }

    /**
     * How one living player stands for this recruitment.
     * 单名玩家在本次招募中的状态。
     */
    public enum Standing {
        /** Pickable at random and when forced. / 可被随机选中，也可被强制选中。 */
        PICKABLE,
        /** A role the Grand Witch can never recruit at random (police, Corrupt Cop, Loose End); a forced slot still
         * takes it. / 大魔女随机时永远不会选中的身份（警职、黑警、亡命徒）；强制序号仍会选中。 */
        REFUSED,
        /** Alive and playing but unavailable right now (Depression psycho, swallowed, controlled by a Kidnapper).
         * 存活且参与对局，但当前暂不可选（抑郁狂暴、被吞食、被绑匪控制）。 */
        BUSY,
        /** Not a recruitable living participant at all (offline, dead, spectating, the recruiter, a witch).
         * 根本不是可招募的存活参与者（离线、死亡、旁观、招募者本人、魔女阵营）。 */
        GONE
    }

    public enum Kind {
        /** The forced player of this recruitment number. / 本次招募序号的强制玩家。 */
        FORCED,
        /** A uniform pick from the random pool. / 从随机池中均匀抽取。 */
        RANDOM,
        /** The forced player is alive but busy: fail without spending quota and keep the entry.
         * 强制玩家存活但暂不可选：失败、不消耗名额并保留该条目。 */
        FORCED_BUSY,
        /** Nobody can be picked: fail without spending quota. / 无人可选：失败且不消耗名额。 */
        NONE
    }

    /**
     * @param target         the chosen player, null unless {@link Kind#FORCED} or {@link Kind#RANDOM}
     * @param dropForcedEntry the forced entry of this number can never apply any more (its player is gone) and goes
     *                       / 该序号的强制条目已无法生效（玩家已不在），应当移除
     */
    public record Choice<T>(Kind kind, @Nullable T target, boolean dropForcedEntry) { }

    /**
     * A forced player who is {@link Standing#PICKABLE} or {@link Standing#REFUSED} is taken; a {@link Standing#BUSY}
     * one blocks this attempt; a {@link Standing#GONE} one drops the entry and the random pick runs. {@code pool} is the
     * random pool: pickable players only, already without every player reserved by a forced entry. A null
     * {@code forcedStanding} means this number has no forced entry; an offline forced player is {@code GONE}.
     * 强制玩家为 PICKABLE 或 REFUSED 时直接选中；为 BUSY 时本次尝试失败；为 GONE 时移除条目并执行随机抽取。{@code pool}
     * 为随机池：只含可被选中的玩家，且已排除所有被强制条目预留的玩家。{@code forcedStanding} 为 null 表示该序号没有强制
     * 条目；离线的强制玩家视为 GONE。
     */
    public static <T> Choice<T> choose(
            @Nullable T forced,
            @Nullable Standing forcedStanding,
            List<T> pool,
            RandomGenerator random
    ) {
        boolean drop = false;
        if (forcedStanding != null) {
            switch (forced == null ? Standing.GONE : forcedStanding) {
                case PICKABLE, REFUSED -> {
                    return new Choice<>(Kind.FORCED, forced, false);
                }
                case BUSY -> {
                    return new Choice<>(Kind.FORCED_BUSY, null, false);
                }
                case GONE -> drop = true;
            }
        }
        if (pool.isEmpty()) {
            return new Choice<>(Kind.NONE, null, drop);
        }
        return new Choice<>(Kind.RANDOM, pool.get(random.nextInt(pool.size())), drop);
    }

    /**
     * The order of the recruitment about to happen: one past the successful ones.
     * 即将进行的招募序号：已成功次数加一。
     */
    public static int nextOrder(int recruitedCount) {
        return Math.max(0, recruitedCount) + 1;
    }
}
