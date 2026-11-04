package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 运动员 / Athlete: a task civilian with unlimited stamina (registry {@code maxSprintTime(-1)}) whose ability key dashes
 * up to five blocks forward along the ground. Real role only: the Black Raven can never disguise as it (DENYLIST).
 * 运动员：体力无限（注册时 {@code maxSprintTime(-1)}）的任务平民，按技能键沿地面向前冲刺最多 5 格。只认真实职业：
 * 黑羽鸦永远不能伪装成运动员（DENYLIST）。
 */
public final class AthleteRules {
    public static final Identifier ROLE_ID = SparkWitch.id("athlete");
    public static final int COLOR = 0x6FD08C;

    /** Replay skill id and forced-cooldown store id of the dash. / 冲刺的回放技能 id 与强制冷却存储 id。 */
    public static final Identifier DASH_SKILL_ID = SparkWitch.id("athlete_dash");
    /** Farthest dash, in blocks. / 冲刺最远距离（格）。 */
    public static final double DASH_DISTANCE = 5.0D;
    /** A dash shorter than this is refused and costs nothing. / 短于此距离的冲刺被拒绝，不消耗冷却。 */
    public static final double MIN_DASH_DISTANCE = 0.5D;
    /** Sampling step along the path. / 沿路径的采样步长。 */
    public static final double DASH_STEP = 1.0D / 16.0D;
    /**
     * Rise increment when stepping up, so a step needs only the headroom walking would (carpet: 1/16 more).
     * 跨步时的升高增量，使跨步所需净空与走路相同（地毯只需多 1/16）。
     */
    public static final double STEP_RISE = 1.0D / 16.0D;
    /** Largest drop still treated as walking down; anything deeper is a ledge. / 仍算走下去的最大落差，更深即为边缘。 */
    public static final double MAX_STEP_DOWN = 0.6D;
    /**
     * How far below the feet an airborne start may find its floor; a vanilla jump peaks at about 1.25.
     * 起跳时在脚下多深范围内寻找地面；原版跳跃最高约 1.25 格。
     */
    public static final double MAX_START_DROP = 1.3D;
    /** Slack added to a fall query so "nothing within the limit" is distinguishable. / 下落查询的余量，用于区分“限度内无地面”。 */
    public static final double FALL_SLACK = 1.0D / 32.0D;
    /**
     * A dash aimed within 30° of a block axis slides along a wall on that axis (narrow train corridors); a steeper
     * dash stops at the wall.
     * 朝向与方块轴线夹角在 30° 以内的冲刺会沿该轴贴墙滑行（列车窄走廊）；角度更大时在墙前停下。
     */
    public static final double SLIDE_MIN_AXIS_SHARE = Math.cos(Math.toRadians(30.0D));
    /** Opening cooldown on assignment and the cooldown after each dash (15 s). / 获得身份时与每次冲刺后的冷却（15 秒）。 */
    public static final int COOLDOWN_TICKS = 300;

    private AthleteRules() {
    }

    public static boolean isAthlete(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
