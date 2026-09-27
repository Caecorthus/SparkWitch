package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Owner-approved Control Expert numbers and side-neutral pure predicates.
 * 已批准的控场专家数值与两端通用的纯判定。
 */
public final class ControlExpertRules {
    public static final Identifier ROLE_ID = SparkWitch.id("control_expert");
    public static final int COLOR = 0x4DD0E1;
    public static final Identifier VIGILANTE_ID = Identifier.of("wathe", "vigilante");

    public static final Identifier DISRUPTOR_ID = SparkWitch.id("disruptor");
    public static final Identifier TASER_ID = SparkWitch.id("taser");
    public static final Identifier SHOCK_DEVICE_ID = SparkWitch.id("shock_device");

    public static final String DISRUPTOR_ENTRY_ID = "control_expert_disruptor";
    public static final String TASER_ENTRY_ID = "control_expert_taser";
    public static final String SHOCK_DEVICE_ENTRY_ID = "control_expert_shock_device";
    public static final int DISRUPTOR_PRICE = 100;
    public static final int TASER_PRICE = 100;
    public static final int SHOCK_DEVICE_PRICE = 150;
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;

    /** Round-start floor on all three items, written exactly. / 三件道具的开局冷却下限，精确写入。 */
    public static final int INITIAL_COOLDOWN = 30 * 20;
    public static final int DISRUPTOR_COOLDOWN = 90 * 20;
    public static final int TASER_COOLDOWN = 30 * 20;
    public static final int SHOCK_DEVICE_COOLDOWN = 60 * 20;

    public static final double DISRUPT_RADIUS = 6.0;
    public static final double DISRUPT_RADIUS_SQUARED = DISRUPT_RADIUS * DISRUPT_RADIUS;
    public static final int DISRUPT_TICKS = 10 * 20;

    public static final double TASER_BASE_RANGE = 6.0;
    public static final double TASER_BOX_EXPANSION = 0.2;
    /** Bounds for the SparkTraits Marksman multiplier. / SparkTraits 精确枪手倍率的上下限。 */
    public static final double MIN_RANGE_MULTIPLIER = 1.0;
    public static final double MAX_RANGE_MULTIPLIER = 1.3;

    public static final int STUN_TICKS = 5 * 20;
    public static final int TASER_TAIL_TICKS = 10 * 20;
    public static final int SHOCK_TAIL_TICKS = 15 * 20;
    public static final int STUN_SLOWNESS_AMPLIFIER = 2;
    public static final int TAIL_SLOWNESS_AMPLIFIER = 0;

    public static final double SHOCK_HALF_WIDTH = 1.5;
    public static final double SHOCK_DEPTH_BELOW = 1.0;
    public static final double SHOCK_HEIGHT_ABOVE = 2.0;
    /** Wathe grenade throw numbers (slow lob, vanilla divergence). / 沿用 Wathe 手雷的投掷参数（慢速抛物、原版散布）。 */
    public static final float SHOCK_THROW_SPEED = 0.5F;
    public static final float SHOCK_THROW_DIVERGENCE = 1.0F;

    /** Action ids passed to SparkFactionApi.canAffectPlayer. / 传给 SparkFactionApi.canAffectPlayer 的动作 id。 */
    public static final Identifier DISRUPTOR_ACTION = SparkWitch.id("control_expert_disruptor");
    public static final Identifier TASER_ACTION = SparkWitch.id("control_expert_taser");
    public static final Identifier SHOCK_ACTION = SparkWitch.id("control_expert_shock");

    private ControlExpertRules() {
    }

    public static boolean isControlExpert(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * True only when Wathe's gun path asks about the native Vigilante and the shooter is a Control Expert,
     * so police gun exemptions apply without changing any other role's answer.
     * 仅当 Wathe 枪械路径询问原生义警且射手是控场专家时为真，从而获得警用枪械豁免而不改变其他职业的结果。
     */
    public static boolean countsAsNativeVigilanteForGun(@Nullable Role queried, @Nullable Role actual) {
        return queried != null && VIGILANTE_ID.equals(queried.identifier()) && isControlExpert(actual);
    }

    /**
     * Closed 6-block sphere on squared distance (no line-of-sight rule); NaN and negatives never qualify.
     * 基于平方距离的 6 格闭球（不要求视线）；NaN 与负值永不命中。
     */
    public static boolean inDisruptRange(double squaredDistance) {
        return squaredDistance >= 0.0 && squaredDistance <= DISRUPT_RADIUS_SQUARED;
    }

    /** 3x3 horizontal area, one block below to two above the landing point. / 落点周围 3x3，向下 1 格、向上 2 格。 */
    public static Box shockArea(Vec3d landing) {
        return new Box(
                landing.x - SHOCK_HALF_WIDTH, landing.y - SHOCK_DEPTH_BELOW, landing.z - SHOCK_HALF_WIDTH,
                landing.x + SHOCK_HALF_WIDTH, landing.y + SHOCK_HEIGHT_ABOVE, landing.z + SHOCK_HALF_WIDTH);
    }

    /** Bounding-box overlap, so flat sleeping boxes are still caught. / 以碰撞箱重叠判定，睡眠玩家的扁平碰撞箱也会命中。 */
    public static boolean inShockArea(Vec3d landing, Box target) {
        return shockArea(landing).intersects(target);
    }

    /**
     * Whether a flying Shock Device stops on a player: never on its thrower, and otherwise only when the thrower may
     * affect that player. The veto is consulted only for non-throwers.
     * 飞行中的电击装置是否会被某名玩家挡下：投掷者本人永不，其余玩家仅当投掷者可以影响其时才会。
     * 只有对非投掷者才会查询否决判定。
     */
    public static boolean shockDeviceCollides(boolean thrower, BooleanSupplier throwerCanAffect) {
        return !thrower && throwerCanAffect.getAsBoolean();
    }

    /** Non-finite multipliers fail closed to the base range. / 非有限倍率回退到基础射程。 */
    public static double taserRange(double multiplier) {
        return TASER_BASE_RANGE * clampRangeMultiplier(multiplier);
    }

    public static double clampRangeMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)) {
            return MIN_RANGE_MULTIPLIER;
        }
        return Math.min(MAX_RANGE_MULTIPLIER, Math.max(MIN_RANGE_MULTIPLIER, multiplier));
    }
}
