package dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Pure geometry for projectiles passing Rift Gates (no world access, unit-tested). Positions are entity positions
 * (bottom centre of the box); a gate's position is the bottom centre of its slab and its front normal is its facing.
 * The exit velocity is a proper rotation about Y that maps the direction of travel through the source slab onto the
 * destination's front normal, so speed and the vertical component are kept exactly and the projectile always leaves the
 * destination's front, whichever face it entered.
 * 投掷物穿越裂隙门的纯几何（不访问世界，可单元测试）。位置均为实体位置（碰撞箱底面中心）；门的位置是门板底面中心，
 * 正面法线即其朝向。出口速度是绕 Y 轴的真旋转：把穿过源门板的行进方向映射到目标门的正面法线，因此速率与竖直分量
 * 完全保持，且无论从哪一面进入，投掷物总从目标门正面飞出。
 */
public final class RiftProjectileMath {
    /** Gap between the gate slab and the projectile box at the exit. / 出口处门板与投掷物碰撞箱之间的间隙。 */
    public static final double EXIT_CLEARANCE = 0.1;
    /**
     * Vanilla 1.21.1 {@code ProjectileUtil} entity margin ({@code 0.3F} in {@code getCollision}/{@code getEntityCollision});
     * the throwing-axe and M67 sweeps use it so every projectile meets the same gate volume as the deflection seam.
     * 原版 1.21.1 {@code ProjectileUtil} 的实体外扩量；飞斧与 M67 扫描沿用它，使所有投掷物遇到的门体积与偏转接缝一致。
     */
    public static final double GATE_TARGET_MARGIN = 0.3;
    /**
     * How far past the entry face a capped projectile ends its tick: a segment that starts inside a box never hits it,
     * so the next tick's vanilla collision ignores the gate and covers whatever lies behind it.
     * 达到穿门上限的投掷物在入射面之后多远结束本刻：起点位于箱体内的线段不会命中该箱体，因此下一刻的原版碰撞会忽略此门，
     * 并照常检测门后的一切。
     */
    public static final double PASS_THROUGH_NUDGE = 0.01;

    private RiftProjectileMath() {
    }

    /** Outward front normal of a horizontal facing (anything else counts as NORTH, like the gate). / 水平朝向的外法线。 */
    public static Vec3d frontNormal(Direction facing) {
        Direction horizontal = horizontal(facing);
        return new Vec3d(horizontal.getOffsetX(), 0.0, horizontal.getOffsetZ());
    }

    /** Centre of the gate slab for a gate at {@code gatePos}. / 位于 {@code gatePos} 的门板中心。 */
    public static Vec3d gateCentre(Vec3d gatePos) {
        return gatePos.add(0.0, RiftwalkerRules.GATE_HEIGHT / 2.0, 0.0);
    }

    /** Distance from the slab's centre plane to the exiting projectile's box centre. / 门板中心面到出口投掷物箱体中心的距离。 */
    public static double exitDistance(double projectileWidth) {
        return RiftwalkerRules.GATE_DEPTH / 2.0 + Math.max(0.0, projectileWidth) / 2.0 + EXIT_CLEARANCE;
    }

    /**
     * Entity position of a projectile of the given size whose box is centred just in front of the destination gate.
     * 给定尺寸的投掷物在目标门正前方居中时的实体位置。
     */
    public static Vec3d exitPosition(Vec3d destinationPos, Direction destinationFacing, double projectileWidth,
                                     double projectileHeight) {
        Vec3d centre = gateCentre(destinationPos)
                .add(frontNormal(destinationFacing).multiply(exitDistance(projectileWidth)));
        return centre.subtract(0.0, Math.max(0.0, projectileHeight) / 2.0, 0.0);
    }

    /**
     * Box of a projectile of the given size at an entity position, as vanilla {@code EntityDimensions.getBoxAt} builds it.
     * 给定尺寸的投掷物位于某实体位置时的碰撞箱，与原版 {@code EntityDimensions.getBoxAt} 一致。
     */
    public static Box boxAt(Vec3d position, double width, double height) {
        double half = Math.max(0.0, width) / 2.0;
        return new Box(position.x - half, position.y, position.z - half,
                position.x + half, position.y + Math.max(0.0, height), position.z + half);
    }

    /**
     * Direction of travel through the source slab: the facing when moving out of the front side, otherwise its
     * opposite (front entries and moves parallel to the slab).
     * 穿过源门板的行进方向：从正面一侧向外运动时为朝向本身，否则（正面进入或与门板平行）为其反方向。
     */
    public static Direction travelDirection(Vec3d velocity, Direction sourceFacing) {
        Direction source = horizontal(sourceFacing);
        double along = velocity.x * source.getOffsetX() + velocity.z * source.getOffsetZ();
        return along > 0.0 ? source : source.getOpposite();
    }

    /** Clockwise quarter turns (seen from above) taking {@code from} to {@code to}. / 由上往下看，从 from 到 to 的顺时针四分之一圈数。 */
    public static int clockwiseQuarterTurns(Direction from, Direction to) {
        Direction current = horizontal(from);
        Direction target = horizontal(to);
        int turns = 0;
        while (current != target && turns < 4) {
            current = current.rotateYClockwise();
            turns++;
        }
        return turns % 4;
    }

    /**
     * Rotates around Y by quarter turns, matching {@link Direction#rotateYClockwise()} (N → E → S → W); exact.
     * 绕 Y 轴按四分之一圈旋转，与 {@link Direction#rotateYClockwise()} 一致（北 → 东 → 南 → 西）；无精度损失。
     */
    public static Vec3d rotateClockwise(Vec3d vector, int quarterTurns) {
        double x = vector.x;
        double z = vector.z;
        for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) {
            double rotatedX = -z;
            z = x;
            x = rotatedX;
        }
        return new Vec3d(x, vector.y, z);
    }

    /**
     * Velocity after passing from the source gate to the destination: rotated so the direction of travel through the
     * source becomes the destination's front normal; speed and vertical component unchanged. A result that would still
     * point into the destination has its horizontal part flipped (defensive; a proper rotation never does).
     * 从源门穿到目标门后的速度：旋转使穿过源门的行进方向变为目标门的正面法线；速率与竖直分量不变。若结果仍指向目标门内，
     * 则翻转其水平分量（防御性处理；真旋转不会出现这种情况）。
     */
    public static Vec3d exitVelocity(Vec3d velocity, Direction sourceFacing, Direction destinationFacing) {
        Direction travel = travelDirection(velocity, sourceFacing);
        Vec3d rotated = rotateClockwise(velocity, clockwiseQuarterTurns(travel, destinationFacing));
        Vec3d normal = frontNormal(destinationFacing);
        if (rotated.x * normal.x + rotated.z * normal.z < 0.0) {
            return new Vec3d(-rotated.x, rotated.y, -rotated.z);
        }
        return rotated;
    }

    /** Reflection with no other gate: straight back at full speed (D8). / 没有其他门时的反弹：原速原路返回（D8）。 */
    public static Vec3d reflect(Vec3d velocity) {
        return velocity.negate();
    }

    /**
     * Thrown, persistent and explosive projectiles re-read their position and velocity after {@code hitOrDeflect} and
     * move by it in the same tick without any collision test. Starting the move here makes the tick end exactly on
     * {@code end}, so the next tick's vanilla collision sees everything from {@code end} on (nothing in front of the exit
     * is skipped, no wall behind it is tunnelled).
     * 投掷类、持久类与爆炸类投掷物在 {@code hitOrDeflect} 之后重新读取位置和速度，并在同一刻内不做碰撞检测地移动。
     * 从这里起步可让本刻恰好结束在 {@code end}，下一刻的原版碰撞便会检测从 {@code end} 起的一切（不会跳过出口前的目标，
     * 也不会穿过墙壁）。
     */
    public static Vec3d sameTickMoveStart(Vec3d end, Vec3d velocity) {
        return end.subtract(velocity);
    }

    /** Projectile yaw for a velocity, as {@code ProjectileEntity.updateRotation} computes it. / 与原版一致的偏航角。 */
    public static float yawOf(Vec3d velocity) {
        return (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
    }

    /** Projectile pitch for a velocity, as {@code ProjectileEntity.updateRotation} computes it. / 与原版一致的俯仰角。 */
    public static float pitchOf(Vec3d velocity) {
        return (float) Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalLength()));
    }

    /**
     * Squared distance from {@code from} to where the segment enters {@code box}; -1 on a miss or when the segment
     * starts inside (vanilla {@code Box.raycast} only reports entering faces).
     * 线段进入 {@code box} 处到 {@code from} 的平方距离；未命中或起点在箱体内时为 -1（原版 {@code Box.raycast} 只报告进入面）。
     */
    public static double entrySquared(Box box, Vec3d from, Vec3d to) {
        if (box == null || from == null || to == null || box.contains(from)) {
            return -1.0;
        }
        return box.raycast(from, to).map(from::squaredDistanceTo).orElse(-1.0);
    }

    /**
     * Where a capped projectile ends its tick: just past the entry face of the gate volume; null when the segment does
     * not enter it (then the projectile is left alone).
     * 达到上限的投掷物本刻的结束点：刚越过门体积的入射面；线段未进入时为 null（此时不改动投掷物）。
     */
    @Nullable
    public static Vec3d passThroughEnd(Box gateVolume, Vec3d from, Vec3d to) {
        if (gateVolume == null || from == null || to == null || gateVolume.contains(from)) {
            return null;
        }
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length <= 1.0E-7) {
            return null;
        }
        return gateVolume.raycast(from, to)
                .map(entry -> entry.add(delta.multiply(Math.min(PASS_THROUGH_NUDGE, length) / length)))
                .orElse(null);
    }

    private static Direction horizontal(Direction facing) {
        return facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
    }
}
