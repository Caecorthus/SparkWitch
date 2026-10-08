package dev.caecorthus.sparkwitch.roles.civilian.usec;

/**
 * Stable contract: pure USEC ballistics math (no world access), shared by the server tracer and the client reticle so
 * the holdover marks always match real hits. AP energy is tracked as the fraction spent (0..1): each block flown costs
 * {@link #flightCostPerBlock(double)}, each penetrated block costs {@link UsecRules#AP_PENETRATION_COST}, and the
 * round vanishes at 100%. The sink rate (blocks of drop per block flown) is a step function of the fraction spent
 * ({@link UsecRules#AP_SINK_BAND_STARTS} / {@link UsecRules#AP_SINK_RATES}), so the cumulative drop is its integral:
 * a penetration starts the sink earlier and steeper, but the path never jumps. FMJ never drops.
 * 稳定契约：USEC 纯弹道数学（不访问世界），由服务端射线追踪与客户端分划共用，保证抬枪刻度与实际命中一致。AP 能量以已用比例
 * （0..1）记录：每飞 1 格消耗 {@link #flightCostPerBlock(double)}，每穿透 1 个方块消耗 {@link UsecRules#AP_PENETRATION_COST}，
 * 用到 100% 子弹消失。下沉率（每飞 1 格下沉的格数）是已用比例的分段常数函数，因此累计下沉是它的积分：穿墙会让下坠更早、
 * 更陡，但弹道永不跳变。FMJ 没有下坠。
 */
public final class UsecBallistics {
    /** Spent fractions within this of 100% count as exhausted (float noise). / 与 100% 相差在此范围内视为耗尽（浮点误差）。 */
    public static final double EXHAUSTED_EPSILON = 1.0E-9;

    private static final double[] BAND_STARTS = toArray(UsecRules.AP_SINK_BAND_STARTS);
    private static final double[] RATES = toArray(UsecRules.AP_SINK_RATES);

    static {
        if (BAND_STARTS.length == 0 || BAND_STARTS.length != RATES.length) {
            throw new IllegalStateException("USEC sink table needs one rate per band");
        }
    }

    private UsecBallistics() {
    }

    /**
     * Clamps a Marksman multiplier into its bounds; NaN or infinite reads as no trait (1.0).
     * 将精确枪手倍率钳制到上下限内；NaN 或无穷大视为没有词条（1.0）。
     */
    public static double sanitizeMarksman(double marksmanMultiplier) {
        if (!Double.isFinite(marksmanMultiplier)) {
            return UsecRules.MIN_MARKSMAN_MULTIPLIER;
        }
        return Math.max(UsecRules.MIN_MARKSMAN_MULTIPLIER,
                Math.min(UsecRules.MAX_MARKSMAN_MULTIPLIER, marksmanMultiplier));
    }

    /** FMJ range: {@link UsecRules#FMJ_RANGE} x Marksman (50, or 65 at x1.3). / FMJ 射程：50 x 精确枪手倍率（x1.3 时为 65）。 */
    public static double fmjRange(double marksmanMultiplier) {
        return UsecRules.FMJ_RANGE * sanitizeMarksman(marksmanMultiplier);
    }

    /** Energy fraction spent per block flown; Marksman divides it. / 每飞 1 格消耗的能量比例；精确枪手倍率作除数。 */
    public static double flightCostPerBlock(double marksmanMultiplier) {
        return UsecRules.AP_FLIGHT_COST_PER_BLOCK / sanitizeMarksman(marksmanMultiplier);
    }

    /** Open-flight AP range: 200 blocks, 260 at Marksman x1.3. / AP 无障碍射程：200 格，精确枪手 x1.3 时为 260 格。 */
    public static double apMaxDistance(double marksmanMultiplier) {
        return 1.0 / flightCostPerBlock(marksmanMultiplier);
    }

    /**
     * Blocks of sink per block flown at a spent fraction. NaN and negatives read as the first band; anything at or
     * beyond the last band start reads as the last rate.
     * 指定已用比例下每飞 1 格的下沉格数。NaN 与负值按第一段处理；达到或超过最后一段起点的值按最后一段处理。
     */
    public static double sinkRate(double spentFraction) {
        if (Double.isNaN(spentFraction)) {
            return RATES[0];
        }
        for (int band = BAND_STARTS.length - 1; band > 0; band--) {
            if (spentFraction >= BAND_STARTS[band]) {
                return RATES[band];
            }
        }
        return RATES[0];
    }

    public static boolean isExhausted(double spentFraction) {
        return spentFraction >= 1.0 - EXHAUSTED_EPSILON;
    }

    /** Spent fraction after a straight flight, capped at 100%. / 一段直线飞行后的已用比例，上限 100%。 */
    public static double spentAfterFlight(double spentStart, double blocksFlown, double flightCostPerBlock) {
        double start = Double.isFinite(spentStart) ? Math.max(0.0, spentStart) : 0.0;
        double blocks = Double.isFinite(blocksFlown) ? Math.max(0.0, blocksFlown) : 0.0;
        double cost = flightCostPerBlock > 0.0 ? flightCostPerBlock : 0.0;
        return Math.min(1.0, start + blocks * cost);
    }

    /** Spent fraction after one more penetrated block, capped at 100%. / 再穿透一个方块后的已用比例，上限 100%。 */
    public static double spentAfterPenetration(double spentStart) {
        double start = Double.isFinite(spentStart) ? Math.max(0.0, spentStart) : 0.0;
        return Math.min(1.0, start + UsecRules.AP_PENETRATION_COST);
    }

    /** Blocks the round can still fly before its energy runs out. / 能量耗尽前子弹还能飞行的格数。 */
    public static double remainingFlight(double spentFraction, double flightCostPerBlock) {
        double start = Double.isFinite(spentFraction) ? Math.max(0.0, spentFraction) : 0.0;
        if (isExhausted(start)) {
            return 0.0;
        }
        if (!(flightCostPerBlock > 0.0)) {
            return Double.POSITIVE_INFINITY;
        }
        return (1.0 - start) / flightCostPerBlock;
    }

    /**
     * Drop accumulated over one straight flight segment that starts at {@code spentStart}: the piecewise integral of
     * {@link #sinkRate(double)} over the energy spent, converted back to blocks. Flight past exhaustion adds nothing.
     * The tracer calls it per segment, adding {@link UsecRules#AP_PENETRATION_COST} to the spent fraction between
     * segments; a non-positive cost means the energy never drains (constant rate).
     * 从 {@code spentStart} 开始的一段直线飞行累计的下沉：{@link #sinkRate(double)} 对已用能量的分段积分，再换算回格数。
     * 能量耗尽之后的飞行不再增加下沉。射线追踪逐段调用，并在段与段之间把 {@link UsecRules#AP_PENETRATION_COST}
     * 加到已用比例上；耗能不为正时视为能量永不减少（恒定下沉率）。
     */
    public static double sinkAfter(double spentStart, double blocksFlown, double flightCostPerBlock) {
        if (!Double.isFinite(spentStart) || !Double.isFinite(blocksFlown) || blocksFlown <= 0.0) {
            return 0.0;
        }
        double start = Math.max(0.0, spentStart);
        if (isExhausted(start)) {
            return 0.0;
        }
        if (!(flightCostPerBlock > 0.0)) {
            return sinkRate(start) * blocksFlown;
        }
        double end = Math.min(1.0, start + blocksFlown * flightCostPerBlock);
        double integral = 0.0;
        for (int band = 0; band < BAND_STARTS.length; band++) {
            double bandEnd = band + 1 < BAND_STARTS.length ? BAND_STARTS[band + 1] : 1.0;
            double low = Math.max(start, BAND_STARTS[band]);
            double high = Math.min(end, bandEnd);
            if (high > low) {
                integral += RATES[band] * (high - low);
            }
        }
        return integral / flightCostPerBlock;
    }

    /**
     * Cumulative AP drop at a distance with no obstacles (0 up to 75 and about 2.75 at 200 for m = 1); distances past
     * {@link #apMaxDistance(double)} return the drop where the round vanished. Used for the reticle holdover marks.
     * 无障碍时指定距离的 AP 累计下沉（m = 1 时 75 格内为 0，200 格约 2.75）；超出 {@link #apMaxDistance(double)}
     * 的距离返回子弹消失处的下沉。用于分划抬枪刻度。
     */
    public static double apDropAt(double distance, double marksmanMultiplier) {
        return sinkAfter(0.0, distance, flightCostPerBlock(marksmanMultiplier));
    }

    private static double[] toArray(java.util.List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
