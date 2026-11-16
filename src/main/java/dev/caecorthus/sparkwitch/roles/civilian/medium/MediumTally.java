package dev.caecorthus.sparkwitch.roles.civilian.medium;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The weighted count both window kinds share. Votes arrive in casting order (a changed vote sits at the end); a voter
 * the {@code counts} gate refuses is dropped; a bound voter weighs {@link MediumRules#BIND_VOTE_WEIGHT}. The option
 * with the highest total wins; several leaders are a tie, broken by the last counted vote cast for one of the leaders
 * (with two options that is simply the last vote cast).
 * 两种窗口共用的加权计票。票按投出的先后排列（改票排在最后）；被 {@code counts} 门槛拒绝的投票者被剔除；被招魂绑定的
 * 投票者记 {@link MediumRules#BIND_VOTE_WEIGHT} 票。总票数最高的选项获胜；多个选项并列最高即平票，由投给并列选项的
 * 最后一张有效票定夺（只有两个选项时就是最后投出的一票）。
 *
 * @param winner the winning option index, or {@link #NO_WINNER} when no vote counted / 获胜选项序号；无有效票时为 NO_WINNER
 * @param tie    several options shared the highest total / 多个选项并列最高
 */
record MediumTally(int winner, boolean tie) {
    static final int NO_WINNER = -1;

    boolean hasWinner() {
        return winner != NO_WINNER;
    }

    static MediumTally count(Map<UUID, Integer> votesInCastingOrder, Predicate<UUID> counts, Predicate<UUID> bound,
                             int optionCount) {
        int[] totals = new int[Math.max(0, optionCount)];
        boolean any = false;
        for (Map.Entry<UUID, Integer> entry : votesInCastingOrder.entrySet()) {
            Integer choice = entry.getValue();
            if (choice == null || choice < 0 || choice >= totals.length || !counts.test(entry.getKey())) {
                continue;
            }
            totals[choice] += bound.test(entry.getKey()) ? MediumRules.BIND_VOTE_WEIGHT : 1;
            any = true;
        }
        if (!any) {
            return new MediumTally(NO_WINNER, false);
        }
        int best = 0;
        for (int total : totals) {
            best = Math.max(best, total);
        }
        int leaders = 0;
        for (int total : totals) {
            if (total == best) {
                leaders++;
            }
        }
        // The last counted vote for a leader decides; with a single leader that is the leader itself.
        // 投给领先选项的最后一张有效票定夺；只有一个领先选项时就是它本身。
        int winner = NO_WINNER;
        for (Map.Entry<UUID, Integer> entry : votesInCastingOrder.entrySet()) {
            Integer choice = entry.getValue();
            if (choice != null && choice >= 0 && choice < totals.length && totals[choice] == best
                    && counts.test(entry.getKey())) {
                winner = choice;
            }
        }
        return new MediumTally(winner, leaders > 1);
    }
}
