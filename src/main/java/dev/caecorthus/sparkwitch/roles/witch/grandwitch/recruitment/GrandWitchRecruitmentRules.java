package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Pure, conservative recruitment arithmetic. / 纯规则：保守折算、累计名额。 */
public final class GrandWitchRecruitmentRules {
    public static final int UNKNOWN_ITEM_PRICE = 25;

    private static final List<String> POLICE_REFUSALS = List.of(
            "message.sparkwitch.recruitment.police_refused.buddha",
            "message.sparkwitch.recruitment.police_refused.jesus",
            "message.sparkwitch.recruitment.police_refused.silver_bullet");
    private static final List<String> CORRUPT_COP_REFUSALS = List.of(
            "message.sparkwitch.recruitment.corrupt_cop_refused.too_hao",
            "message.sparkwitch.recruitment.corrupt_cop_refused.dislike");
    private static final List<String> LAST_STAND_LOOSE_END_REFUSALS = List.of(
            "message.sparkwitch.recruitment.loose_end_refused");

    private GrandWitchRecruitmentRules() { }

    /** Roles the Grand Witch may never recruit (owner rules, 2026-10-02; Last Stand Loose End 2026-10-05).
     * 大魔女永远不能招募的身份（所有者规则，2026-10-02；背水一战亡命徒 2026-10-05）。 */
    public enum Refusal { NONE, POLICE, CORRUPT_COP, LAST_STAND_LOOSE_END }

    /** A Loose End made by Last Stand's Final Moment is refused first. The Corrupt Cop wins over any police
     * registration. The Insider is registered as police only for the tablet channel and stays recruitable.
     * 背水一战终局时刻转成的亡命徒最先拒绝；黑警优先于任何警职登记；内应仅为平板警察频道登记为警职，仍可被招募。 */
    public static Refusal refusal(boolean corruptCop, boolean police, boolean insider, boolean lastStandLooseEnd) {
        if (lastStandLooseEnd) return Refusal.LAST_STAND_LOOSE_END;
        if (corruptCop) return Refusal.CORRUPT_COP;
        return police && !insider ? Refusal.POLICE : Refusal.NONE;
    }

    /** Flavor lines for a refusal; the caller picks one uniformly per attempt.
     * 拒绝时的台词候选；由调用方每次尝试均匀随机选取一句。 */
    public static List<String> refusalMessages(Refusal refusal) {
        return switch (refusal) {
            case POLICE -> POLICE_REFUSALS;
            case CORRUPT_COP -> CORRUPT_COP_REFUSALS;
            case LAST_STAND_LOOSE_END -> LAST_STAND_LOOSE_END_REFUSALS;
            case NONE -> List.of();
        };
    }

    /** 18-23 opening participants grant 1 recruit, then +1 per further 6 (24-29 → 2, ...).
     * 开局 18-23 人提供 1 个名额，此后每多 6 人加 1 个（24-29 人为 2 个，依此类推）。 */
    public static int limit(int openingParticipants) {
        return openingParticipants < 18 ? 0 : (openingParticipants - 18) / 6 + 1;
    }

    public static int remaining(int openingParticipants, int recruited) {
        return Math.max(0, limit(openingParticipants) - Math.max(0, recruited));
    }

    public record Price(int gold, int outputCount) {
        public Price {
            if (gold < 0 || outputCount <= 0) {
                throw new IllegalArgumentException("Invalid physical output price");
            }
        }

        public long refund(int count) {
            return (long) gold * count / outputCount;
        }
    }

    /** Aggregate each item before flooring; duplicate offers use the lowest unit price.
     * 同物品先合并后取整；多个售价采用最低单价，避免重复退款。 */
    public static <T> int refund(Map<T, Integer> removed, Map<T, ? extends Collection<Price>> prices) {
        long total = 0;
        for (var entry : removed.entrySet()) {
            int count = entry.getValue();
            if (count < 0) throw new IllegalArgumentException("Negative item count");
            Collection<Price> offers = prices.get(entry.getKey());
            long amount = offers == null || offers.isEmpty()
                    ? (long) UNKNOWN_ITEM_PRICE * count
                    : offers.stream().mapToLong(price -> price.refund(count)).min().orElseThrow();
            total = Math.addExact(total, amount);
        }
        return Math.toIntExact(total);
    }

    public static int balanceAfter(int oldBalance, int refund) {
        return Math.addExact(oldBalance, refund);
    }
}
