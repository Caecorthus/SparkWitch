package dev.caecorthus.sparkwitch.roles.civilian.blind;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-approved Blind (盲人) ids and numbers (docs/plans/civ-blind/decisions.md). Pure constants; every duration is in
 * server ticks at 20 tps. Multipliers scale only the maximum perception range (C3), never the sound or cane radii.
 * 所有者批准的盲人 id 与数值（见 decisions 文件）。纯常量；所有时长均为 20 tps 下的服务端刻。倍率只放大最大感知距离（C3），
 * 不放大声音与盲杖半径。
 */
public final class BlindRules {
    public static final Identifier ROLE_ID = SparkWitch.id("blind");
    public static final int COLOR = 0xB8C7D9;

    public static final Identifier WHITE_CANE_ID = SparkWitch.id("white_cane");
    public static final Identifier COMTAC_ID = SparkWitch.id("comtac_viii");
    /** Role-owned skill id; deliberately not {@code sparktraits:focus} and not a witch skill. / 职业自有技能 id。 */
    public static final Identifier ATTUNE_ID = SparkWitch.id("blind_attune");
    /** Shop entry id; its name keys are {@code shop.sparkwitch.comtac_viii(.description)}. / 商店条目 id。 */
    public static final String COMTAC_SHOP_ENTRY_ID = "comtac_viii";

    /** Blocks of environment a sound lights up around its source (never multiplied). / 声音照亮声源周围的格数。 */
    public static final int SOUND_REVEAL_RADIUS = 4;
    /** Maximum distance at which a sound reaches the Blind, before multipliers. / 倍率前的最大感知距离。 */
    public static final int BASE_PERCEPTION_RANGE = 10;
    public static final int COMTAC_RANGE_MULTIPLIER = 3;
    public static final int ATTUNE_RANGE_MULTIPLIER = 5;

    public static final int CANE_ENVIRONMENT_RADIUS = 15;
    public static final int CANE_PLAYER_RADIUS = 5;
    public static final int CANE_ACTIVE_TICKS = 100;
    /** Counted from the end of the active window (C2). / 从持续窗口结束后开始计算（C2）。 */
    public static final int CANE_COOLDOWN_TICKS = 200;
    public static final int CANE_ROUND_START_COOLDOWN_TICKS = 600;

    public static final int ATTUNE_ACTIVE_TICKS = 200;
    /** Counted from the end of the active window (C2). / 从持续窗口结束后开始计算（C2）。 */
    public static final int ATTUNE_COOLDOWN_TICKS = 900;
    public static final int ATTUNE_ROUND_START_COOLDOWN_TICKS = 1800;
    /** Ambient-category volume factor on the Blind's own client while Attune is active (C8). / 凝神期间氛围音音量系数。 */
    public static final float ATTUNE_AMBIENT_VOLUME = 0.25f;

    /** Lifetime of one sound pulse (expand + fade, C4). / 单个声音脉冲的总时长（扩张 + 淡出，C4）。 */
    public static final int SOUND_PULSE_TICKS = 30;
    /** How long a sounding player stays perceived (C4). / 发声玩家保持被感知的时长（C4）。 */
    public static final int PLAYER_PULSE_TICKS = 30;
    /** At most one pulse per (emitter, Blind) pair in this window. / 同一（发声者，盲人）在此窗口内至多一个脉冲。 */
    public static final int EMITTER_THROTTLE_TICKS = 10;

    public static final int COMTAC_PRICE = 100;
    public static final int TASK_REWARD = 50;

    private BlindRules() {
    }

    public static boolean isBlind(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Maximum perception range in blocks: 10, ×3 with a worn ComTac, ×5 while Attune is active (10 / 30 / 50 / 150).
     * 最大感知距离（格）：10；戴 ComTac ×3；凝神期间 ×5（10 / 30 / 50 / 150）。
     */
    public static int perceptionRange(boolean comTacWorn, boolean attuneActive) {
        int range = BASE_PERCEPTION_RANGE;
        if (comTacWorn) {
            range *= COMTAC_RANGE_MULTIPLIER;
        }
        if (attuneActive) {
            range *= ATTUNE_RANGE_MULTIPLIER;
        }
        return range;
    }
}
