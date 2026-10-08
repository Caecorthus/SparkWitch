package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.doctor4t.wathe.game.GameConstants;

/** 魔术师统一数值；后续平衡调整只改这里。 */
public final class MagicianConstants {
    public static final int ROLE_COLOR = 0x6B17B0;
    public static final int INITIAL_COOLDOWN_TICKS = GameConstants.getInTicks(0, 30);
    public static final int RECORD_DURATION_TICKS = GameConstants.getInTicks(0, 30);
    public static final int PLAYBACK_COOLDOWN_TICKS = GameConstants.getInTicks(0, 15);
    public static final int FORCED_END_REWARD_COINS = 50;
    public static final int ABILITY_DEBOUNCE_TICKS = 4;
    public static final double KNIFE_RANGE = 3.0D;
    public static final double REVOLVER_RANGE = 30.0D;
    private MagicianConstants() {}
}
