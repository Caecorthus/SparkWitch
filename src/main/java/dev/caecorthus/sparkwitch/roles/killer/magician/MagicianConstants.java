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
    /**
     * Safety bound, not a balance value: the most actions one recording stores (an average of 16 per recorded tick).
     * An unmodified client sends a handful per tick at most, even with a fast auto-clicker, so it never gets near it.
     * 安全上限，不是平衡数值：一次录制最多保存的动作数（平均每个录制刻 16 条）。未修改的客户端即使使用高速连点器，每刻也只会
     * 发出寥寥数条，永远不会接近该上限。
     */
    public static final int MAX_RECORDED_ACTIONS = RECORD_DURATION_TICKS * 16;
    private MagicianConstants() {}
    /** Pure: whether a recording that already holds {@code recorded} actions takes one more. / 纯函数：是否还能再记一条。 */
    static boolean acceptsRecordedAction(int recorded) { return recorded < MAX_RECORDED_ACTIONS; }
}
