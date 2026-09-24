package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import java.util.Collection;
import java.util.Map;

/** Pure, conservative recruitment arithmetic. / 纯规则：保守折算、累计名额。 */
public final class GrandWitchRecruitmentRules {
    public static final int UNKNOWN_ITEM_PRICE = 25;

    private GrandWitchRecruitmentRules() { }

    public static int limit(int openingParticipants) {
        return openingParticipants < 18 ? 0 : (openingParticipants - 18) / 6;
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
