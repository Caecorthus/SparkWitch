package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Pure shell geometry (no world): the launch direction, where a blast is centred for each kind of impact, and the
 * line-of-sight sample points on a target.
 * 纯炮弹几何（不依赖世界）：发射方向、各类命中时的爆心位置，以及目标身上的视线采样点。
 */
public final class PotionBlastGeometry {
    /** A block-face hit is centred this far out of the face, so sight rays never start inside the block. / 方块命中时爆心离开受击面的距离。 */
    public static final double FACE_OFFSET = 0.1;
    /** The feet sample sits this far above the feet. / 脚部采样点高于脚下的距离。 */
    public static final double FEET_SAMPLE_LIFT = 0.1;

    private PotionBlastGeometry() {
    }

    /**
     * Unit look vector of {@code yaw}/{@code pitch}, the same formula (and {@code MathHelper} tables) as vanilla
     * {@code ProjectileEntity#setVelocity(Entity, …)} with zero roll.
     * {@code yaw}/{@code pitch} 对应的单位视线向量，公式（及 {@code MathHelper} 查表）与原版
     * {@code ProjectileEntity#setVelocity(Entity, …)} 在横滚为 0 时一致。
     */
    public static Vec3d direction(float yaw, float pitch) {
        float radians = (float) (Math.PI / 180.0);
        float x = -MathHelper.sin(yaw * radians) * MathHelper.cos(pitch * radians);
        float y = -MathHelper.sin(pitch * radians);
        float z = MathHelper.cos(yaw * radians) * MathHelper.cos(pitch * radians);
        return new Vec3d(x, y, z);
    }

    /**
     * A player hit is centred on the target's real box: where this tick's segment enters it, else (the segment only
     * crossed the projectile margin) the segment point nearest the box centre clamped into the box. A direct hit is
     * therefore always in the centre ring.
     * 命中玩家时爆心位于目标真实碰撞箱上：本刻线段进入箱体处；若线段只穿过投射物余量，则取线段上离箱体中心最近的点并夹紧到箱内。
     * 因此直接命中总在中心环。
     */
    public static Vec3d entityImpact(Vec3d from, Vec3d to, Box box) {
        if (box.contains(from)) {
            return from;
        }
        return box.raycast(from, to).orElseGet(() -> clamp(closestOnSegment(from, to, box.getCenter()), box));
    }

    /**
     * The point {@code sqrt(distanceSquared)} along {@code from → to}, clamped to the segment; {@code from} for a
     * zero-length segment or a non-finite distance.
     * 沿 {@code from → to} 距起点 {@code sqrt(distanceSquared)} 的点，夹紧在线段内；线段长度为 0 或距离非有限时取 {@code from}。
     */
    public static Vec3d alongSegment(Vec3d from, Vec3d to, double distanceSquared) {
        Vec3d segment = to.subtract(from);
        double length = segment.length();
        if (!(length > 0.0) || !Double.isFinite(distanceSquared) || distanceSquared <= 0.0) {
            return from;
        }
        return from.add(segment.multiply(Math.min(1.0, Math.sqrt(distanceSquared) / length)));
    }

    /**
     * A block hit is centred {@link #FACE_OFFSET} out of the hit face (outward normal {@code nx, ny, nz}).
     * 方块命中时爆心沿受击面外法线（{@code nx, ny, nz}）离开受击面 {@link #FACE_OFFSET}。
     */
    public static Vec3d blockImpact(Vec3d hit, int nx, int ny, int nz) {
        return hit.add(nx * FACE_OFFSET, ny * FACE_OFFSET, nz * FACE_OFFSET);
    }

    /**
     * Line-of-sight samples on a target: feet + {@link #FEET_SAMPLE_LIFT}, body centre, eye.
     * 目标身上的视线采样点：脚下 + {@link #FEET_SAMPLE_LIFT}、身体中心、眼睛。
     */
    public static List<Vec3d> sightPoints(Vec3d feet, Box box, double eyeY) {
        Vec3d centre = box.getCenter();
        return List.of(
                new Vec3d(feet.x, feet.y + FEET_SAMPLE_LIFT, feet.z),
                new Vec3d(feet.x, centre.y, feet.z),
                new Vec3d(feet.x, eyeY, feet.z));
    }

    static Vec3d closestOnSegment(Vec3d from, Vec3d to, Vec3d point) {
        Vec3d segment = to.subtract(from);
        double lengthSquared = segment.lengthSquared();
        if (!(lengthSquared > 0.0)) {
            return from;
        }
        double t = MathHelper.clamp(point.subtract(from).dotProduct(segment) / lengthSquared, 0.0, 1.0);
        return from.add(segment.multiply(t));
    }

    static Vec3d clamp(Vec3d point, Box box) {
        return new Vec3d(
                MathHelper.clamp(point.x, box.minX, box.maxX),
                MathHelper.clamp(point.y, box.minY, box.maxY),
                MathHelper.clamp(point.z, box.minZ, box.maxZ));
    }
}
