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
    /** Vanilla player step height: carpets, slabs and stair steps are walked over. / 原版玩家跨步高度：地毯、台阶可直接走上。 */
    public static final double MAX_STEP_UP = 0.6D;
    /** Largest drop still treated as walking down; anything deeper is a ledge. / 仍算走下去的最大落差，更深即为边缘。 */
    public static final double MAX_STEP_DOWN = 0.6D;
    /** How far below the feet an airborne start may find its floor. / 起跳时在脚下多深范围内寻找地面。 */
    public static final double MAX_START_DROP = 1.25D;
    /** Opening cooldown on assignment and the cooldown after each dash (15 s). / 获得身份时与每次冲刺后的冷却（15 秒）。 */
    public static final int COOLDOWN_TICKS = 300;

    private AthleteRules() {
    }

    public static boolean isAthlete(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
