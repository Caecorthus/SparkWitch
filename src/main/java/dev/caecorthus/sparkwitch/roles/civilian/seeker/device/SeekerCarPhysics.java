package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Frozen contract: pure car physics shared by the owner-client simulation and the server replay, using the 0.4x0.6
 * traversal box rather than the entity hit box. Both sides call exactly this code with the same inputs, so a legal
 * client step replays bit-identically on the server. Collision comes from vanilla's static
 * {@link Entity#adjustMovementForCollisions(Entity, Vec3d, Box, World, List)} with a null entity (block shapes only,
 * absent shape context, no world border); step-up, gravity and drag are the car's own.
 * 冻结契约：拥有者客户端模拟与服务端重放共用的纯小车物理，使用 0.4×0.6 通行箱而非实体命中箱。两端以相同输入调用同一段代码，
 * 因此合法的客户端步进在服务端可逐位复现。碰撞使用原版静态 {@link Entity#adjustMovementForCollisions(Entity, Vec3d, Box, World, List)}
 * （实体为 null：只算方块形状、无形状上下文、不含世界边界）；台阶、重力与阻力由小车自行实现。
 */
public final class SeekerCarPhysics {
    /** Fastest fall per tick (vanilla-like terminal velocity). / 每刻最大下落速度（接近原版终端速度）。 */
    public static final double TERMINAL_FALL_SPEED = 3.92;
    /** Downward probe that counts as "supported". / 判定“有支撑”的向下探测距离。 */
    public static final double SUPPORT_PROBE = 0.0625;
    private static final double EPSILON = 1.0E-5;

    private SeekerCarPhysics() {
    }

    public record Result(Vec3d position, Vec3d velocity, boolean onGround, boolean horizontalCollision) {
    }

    /**
     * Collision seam: moves {@code box} by at most {@code movement} and returns the allowed movement. Production uses
     * {@link #worldCollider(World)}; tests may supply fixed shapes.
     * 碰撞接缝：把 {@code box} 最多移动 {@code movement}，返回允许的位移。生产环境使用 {@link #worldCollider(World)}；测试可提供固定形状。
     */
    @FunctionalInterface
    public interface Collider {
        Vec3d adjust(Box box, Vec3d movement);
    }

    /** Block collision of {@code world}, identical on both logical sides. / 两个逻辑端一致的世界方块碰撞。 */
    public static Collider worldCollider(World world) {
        return (box, movement) -> Entity.adjustMovementForCollisions(null, movement, box, world, List.of());
    }

    /** Traversal box centred horizontally on {@code position}, feet at its y. / 以位置为水平中心、脚底为 y 的通行箱。 */
    public static Box traversalBox(Vec3d position) {
        double half = SeekerRules.CAR_TRAVERSAL_WIDTH / 2.0;
        return new Box(position.x - half, position.y, position.z - half,
                position.x + half, position.y + SeekerRules.CAR_TRAVERSAL_HEIGHT, position.z + half);
    }

    /**
     * Velocity from raw input axes and yaw. {@code forward}/{@code sideways} follow vanilla input signs (+forward,
     * +left) and are clamped to [-1, 1]; strafing runs at {@link SeekerRules#CAR_STRAFE_FACTOR}; the horizontal speed
     * never exceeds {@link SeekerRules#CAR_SPEED}. There is no jump and no momentum: the horizontal part is exactly
     * the input. Vertical: on the ground a constant {@code -CAR_GRAVITY} keeps contact; in the air gravity then drag.
     * 由原始输入轴与朝向得到的速度。前后/左右沿用原版输入符号（正为前/左），截断到 [-1, 1]；平移按
     * {@link SeekerRules#CAR_STRAFE_FACTOR} 减速，水平速度永不超过 {@link SeekerRules#CAR_SPEED}。不能跳跃、没有惯性：
     * 水平分量完全由输入决定。垂直：着地时保持 {@code -CAR_GRAVITY} 以维持接触；空中先加重力再乘阻力。
     */
    public static Vec3d inputVelocity(float forward, float sideways, float yaw, Vec3d previousVelocity,
                                      boolean onGround) {
        double f = finiteClamp(forward);
        double s = finiteClamp(sideways) * SeekerRules.CAR_STRAFE_FACTOR;
        double lengthSquared = f * f + s * s;
        if (lengthSquared > 1.0) {
            double length = Math.sqrt(lengthSquared);
            f /= length;
            s /= length;
        }
        double x = 0.0;
        double z = 0.0;
        if (lengthSquared > 1.0E-7 && Float.isFinite(yaw)) {
            float radians = yaw * MathHelper.RADIANS_PER_DEGREE;
            double sin = MathHelper.sin(radians);
            double cos = MathHelper.cos(radians);
            x = (s * cos - f * sin) * SeekerRules.CAR_SPEED;
            z = (f * cos + s * sin) * SeekerRules.CAR_SPEED;
        }
        double previousY = previousVelocity == null || !Double.isFinite(previousVelocity.y) ? 0.0 : previousVelocity.y;
        double y = onGround
                ? -SeekerRules.CAR_GRAVITY
                : Math.max(-TERMINAL_FALL_SPEED, (Math.min(previousY, 0.0) - SeekerRules.CAR_GRAVITY)
                * SeekerRules.CAR_DRAG);
        return new Vec3d(x, y, z);
    }

    public static Result move(World world, Vec3d position, Vec3d velocity) {
        return move(worldCollider(world), position, velocity);
    }

    /**
     * One physics step of the traversal box. Vertical collision is resolved first (vanilla order); when the step hits
     * the ground and a wall, the pre-1.20.5 vanilla step-up with {@link SeekerRules#CAR_STEP_HEIGHT} is tried and kept
     * only if it goes further horizontally. Velocity components blocked by a collision become zero.
     * 通行箱的一步物理。先解算垂直碰撞（原版顺序）；同时撞到地面与墙时，尝试 1.20.5 之前原版式的
     * {@link SeekerRules#CAR_STEP_HEIGHT} 台阶，仅当水平走得更远时采用。被碰撞挡住的速度分量归零。
     */
    public static Result move(Collider collider, Vec3d position, Vec3d velocity) {
        if (!isFinite(position) || !isFinite(velocity)) {
            return new Result(position, Vec3d.ZERO, false, false);
        }
        Box box = traversalBox(position);
        Vec3d adjusted = velocity.lengthSquared() == 0.0 ? Vec3d.ZERO : collider.adjust(box, velocity);
        boolean verticalCollision = velocity.y != adjusted.y;
        boolean groundCollision = verticalCollision && velocity.y < 0.0;
        boolean blockedX = !approximately(velocity.x, adjusted.x);
        boolean blockedZ = !approximately(velocity.z, adjusted.z);
        if (SeekerRules.CAR_STEP_HEIGHT > 0.0 && groundCollision && (blockedX || blockedZ)) {
            Vec3d stepped = stepUp(collider, box, velocity);
            if (stepped != null && stepped.horizontalLengthSquared() > adjusted.horizontalLengthSquared()) {
                adjusted = stepped;
                blockedX = !approximately(velocity.x, adjusted.x);
                blockedZ = !approximately(velocity.z, adjusted.z);
            }
        }
        Vec3d newVelocity = new Vec3d(
                blockedX ? 0.0 : velocity.x,
                verticalCollision ? 0.0 : velocity.y,
                blockedZ ? 0.0 : velocity.z);
        return new Result(position.add(adjusted), newVelocity, groundCollision, blockedX || blockedZ);
    }

    /**
     * Server-computed support: a short downward probe from {@code position} is blocked.
     * 服务端自行计算的支撑：从 {@code position} 向下的短探测被挡住。
     */
    public static boolean isSupported(Collider collider, Vec3d position) {
        if (!isFinite(position)) {
            return false;
        }
        Vec3d probe = collider.adjust(traversalBox(position), new Vec3d(0.0, -SUPPORT_PROBE, 0.0));
        return probe.y > -SUPPORT_PROBE + EPSILON;
    }

    private static Vec3d stepUp(Collider collider, Box box, Vec3d velocity) {
        double step = SeekerRules.CAR_STEP_HEIGHT;
        Vec3d direct = collider.adjust(box, new Vec3d(velocity.x, step, velocity.z));
        Vec3d rise = collider.adjust(box.stretch(velocity.x, 0.0, velocity.z), new Vec3d(0.0, step, 0.0));
        if (rise.y < step) {
            Vec3d over = collider.adjust(box.offset(rise), new Vec3d(velocity.x, 0.0, velocity.z)).add(rise);
            if (over.horizontalLengthSquared() > direct.horizontalLengthSquared()) {
                direct = over;
            }
        }
        if (direct.horizontalLengthSquared() <= 0.0) {
            return null;
        }
        Vec3d settle = collider.adjust(box.offset(direct), new Vec3d(0.0, -direct.y + velocity.y, 0.0));
        return direct.add(settle);
    }

    private static double finiteClamp(float value) {
        return Float.isFinite(value) ? MathHelper.clamp(value, -1.0F, 1.0F) : 0.0;
    }

    private static boolean approximately(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    static boolean isFinite(Vec3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
