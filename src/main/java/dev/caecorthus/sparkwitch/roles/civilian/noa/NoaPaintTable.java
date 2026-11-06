package dev.caecorthus.sparkwitch.roles.civilian.noa;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure painting table: the weighted outcomes for a sanity value and the pick for a uniform roll. Fixed entries keep
 * their weight; dynamic entries move with sanity inside the table's band and are clamped to
 * [{@link #MIN_DYNAMIC_WEIGHT}, {@link #MAX_DYNAMIC_WEIGHT}], so no weight is ever zero or negative. One-off outcomes
 * already painted this round are left out.
 * 纯作画表：给定理智值的带权结果，以及按均匀随机数抽取。固定项权重不变；动态项随理智在该表区间内移动，并限制在
 * [{@link #MIN_DYNAMIC_WEIGHT}, {@link #MAX_DYNAMIC_WEIGHT}] 内，因此权重永远不会为零或负数。本局已画出的一次性结果会被移出。
 */
public final class NoaPaintTable {
    public static final double MIN_DYNAMIC_WEIGHT = 0.5D;
    public static final double MAX_DYNAMIC_WEIGHT = 25.0D;

    /** Every painting outcome; {@code oneOff} ones are painted at most once per round. / 所有作画结果；一次性结果每局最多画出一次。 */
    public enum Outcome {
        DOG(false),
        SPEED_II(false),
        SANITY_LOSS_HIGH(false),
        KEY(true),
        SPYGLASS(true),
        FIRECRACKER(false),
        NOTE(false),
        KNIFE(true),
        REVOLVER(true),
        WHISKEY(false),
        SANITY_LOSS_LOW(false),
        SPEED_III(false),
        CROWBAR(true),
        SANITY_GAIN_LOW(false);

        private final boolean oneOff;

        Outcome(boolean oneOff) {
            this.oneOff = oneOff;
        }

        public boolean oneOff() {
            return oneOff;
        }
    }

    /** One weighted outcome. / 一个带权结果。 */
    public record Entry(Outcome outcome, double weight) {
    }

    private NoaPaintTable() {
    }

    /**
     * The table for {@code sanity}, without one-off outcomes already in {@code painted}. High table above 25%
     * (self-preservation), low table otherwise (weapons).
     * 给定理智的结果表，去掉 {@code painted} 中已画出的一次性结果。高于 25% 走高理智表（偏自保），否则走低理智表（给武器）。
     */
    public static List<Entry> table(float sanity, Set<Outcome> painted) {
        List<Entry> entries = new ArrayList<>();
        if (NoaRules.paintsHigh(sanity)) {
            double high = Math.min(1.0D, sanity);
            entries.add(new Entry(Outcome.DOG, dynamic(10.0D * (1.0D - high) / 0.5D)));
            entries.add(new Entry(Outcome.SPEED_II, dynamic(10.0D * (high - 0.25D) / 0.25D)));
            entries.add(new Entry(Outcome.SANITY_LOSS_HIGH, 25.0D));
            entries.add(new Entry(Outcome.KEY, dynamic(15.0D * (high - 0.25D) / 0.25D)));
            entries.add(new Entry(Outcome.SPYGLASS, dynamic(20.0D * (1.0D - high) / 0.5D)));
            entries.add(new Entry(Outcome.FIRECRACKER, 10.0D));
            entries.add(new Entry(Outcome.NOTE, 10.0D));
        } else {
            double low = Math.max(-1.0D, sanity);
            entries.add(new Entry(Outcome.KNIFE, dynamic(15.0D * (0.25D - low) / 0.25D)));
            entries.add(new Entry(Outcome.REVOLVER, dynamic(15.0D * (1.0D + low / 0.25D))));
            entries.add(new Entry(Outcome.WHISKEY, dynamic(15.0D * (1.0D + low / 0.25D))));
            entries.add(new Entry(Outcome.SANITY_LOSS_LOW, 10.0D));
            entries.add(new Entry(Outcome.SPEED_III, 20.0D));
            entries.add(new Entry(Outcome.CROWBAR, dynamic(15.0D * (0.25D - low) / 0.25D)));
            entries.add(new Entry(Outcome.SANITY_GAIN_LOW, 10.0D));
        }
        entries.removeIf(entry -> entry.outcome().oneOff() && painted.contains(entry.outcome()));
        return entries;
    }

    /**
     * Picks with a uniform {@code unitRoll} in [0, 1): the first entry whose running weight exceeds
     * {@code unitRoll × total}. The list is never empty (each table has more repeatable outcomes than one-offs).
     * 以 [0, 1) 内的均匀随机数抽取：第一个累计权重超过 {@code unitRoll × 总权重} 的条目。列表永远非空（每张表的可重复结果
     * 都比一次性结果多）。
     */
    public static Outcome pick(List<Entry> entries, double unitRoll) {
        double total = 0.0D;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        double target = Math.clamp(unitRoll, 0.0D, 1.0D) * total;
        double running = 0.0D;
        for (Entry entry : entries) {
            running += entry.weight();
            if (target < running) {
                return entry.outcome();
            }
        }
        return entries.getLast().outcome();
    }

    static double dynamic(double weight) {
        return Math.clamp(weight, MIN_DYNAMIC_WEIGHT, MAX_DYNAMIC_WEIGHT);
    }
}
