package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.game.GameConstants;

/**
 * Pure money rules for Witch faction bridges.
 * 魔女阵营金币规则集中在这里，避免事件层复制 wathe 公式。
 */
public final class WitchEconomyRules {
    private static final int MONEY_PER_EXCESS_PLAYER = 15;

    private WitchEconomyRules() {
    }

    public static int killerStartingMoney(int totalPlayers, int killerCount, int killerDividend) {
        int base = GameConstants.MONEY_START;
        if (killerDividend <= 0) {
            return base;
        }
        int excessPlayers = Math.max(0, totalPlayers - (Math.max(0, killerCount) * killerDividend));
        return base + (excessPlayers * MONEY_PER_EXCESS_PLAYER);
    }
}
