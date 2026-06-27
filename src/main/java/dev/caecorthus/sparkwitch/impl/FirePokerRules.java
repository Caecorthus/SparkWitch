package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.game.GameConstants;

/**
 * Tunable Fire Poker combat constants.
 * 烧火棍战斗数值集中在这里，避免事件层散落魔法数字。
 */
public final class FirePokerRules {
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(0, 10);
    public static final int FALL_ATTRIBUTION_WINDOW_TICKS = GameConstants.getInTicks(0, 10);
    public static final int MANA_COST = 20;
    public static final int EFFECT_DURATION_TICKS = GameConstants.getInTicks(0, 3);
    public static final int SLOWNESS_AMPLIFIER = 2;
    public static final int SPEED_AMPLIFIER = 2;
    public static final double KNOCKBACK_STRENGTH = 10.0;
    public static final double KNOCKBACK_UPWARD_VELOCITY = 0.35;

    private FirePokerRules() {
    }

    public static boolean shouldSpendMana(boolean hasManaSystem, int currentMana) {
        return hasManaSystem && currentMana >= MANA_COST;
    }
}
