package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import net.minecraft.util.math.random.Random;

/**
 * Pure weighted draw over {@link FisherCatch}; the server passes its own world random.
 * 对 {@link FisherCatch} 的纯加权抽取；服务端传入自己的世界随机数。
 */
public final class FisherCatchTable {
    public static final int TOTAL_WEIGHT = totalWeight();

    private FisherCatchTable() {
    }

    public static FisherCatch roll(Random random) {
        return pick(random.nextInt(TOTAL_WEIGHT));
    }

    /** Maps a roll in {@code [0, TOTAL_WEIGHT)} to its catch. / 把 {@code [0, TOTAL_WEIGHT)} 内的点数映射到渔获。 */
    static FisherCatch pick(int roll) {
        if (roll < 0 || roll >= TOTAL_WEIGHT) {
            throw new IllegalArgumentException("roll out of range: " + roll);
        }
        int cumulative = 0;
        for (FisherCatch fisherCatch : FisherCatch.values()) {
            cumulative += fisherCatch.weight();
            if (roll < cumulative) {
                return fisherCatch;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static int totalWeight() {
        int total = 0;
        for (FisherCatch fisherCatch : FisherCatch.values()) {
            total += fisherCatch.weight();
        }
        return total;
    }
}
