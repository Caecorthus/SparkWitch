package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;

/**
 * Pure backblast geometry. The lane starts at the gunner's eye and runs straight backwards, opposite to the fire
 * direction (yaw and pitch), for {@link PotionGunnerRules#BACKBLAST_LENGTH} blocks or up to the first block, whichever
 * is shorter. It is a fat ray: player boxes are inflated by {@link PotionGunnerRules#BACKBLAST_HALF_WIDTH} on every
 * side, and only a player whose box centre lies behind the eye counts, so someone hugging the muzzle is never hit.
 * Only the nearest player with a clear line from the eye is hit.
 * 纯尾焰几何。判定通道从药炮手眼部出发，沿与发射方向（偏航与俯仰）相反的方向笔直向后，长度为
 * {@link PotionGunnerRules#BACKBLAST_LENGTH} 格或到第一个方块为止（取较短者）。它是“粗射线”：玩家碰撞箱向各个方向
 * 扩大 {@link PotionGunnerRules#BACKBLAST_HALF_WIDTH}，且只有碰撞箱中心位于眼部之后的玩家才计入，因此贴在炮口前的人
 * 永远不会被击中。只有从眼部视线畅通的最近一名玩家被击中。
 */
public final class PotionBackblastRules {
    /** Pulls the line-of-sight end point off the box surface. / 把视线终点从碰撞箱表面向内收。 */
    static final double LOS_INSET = 0.05;

    private PotionBackblastRules() {
    }

    /** Unit vector pointing out of the launcher's rear. / 指向炮筒尾部外侧的单位向量。 */
    public static Vec3d backwards(float fireYaw, float firePitch) {
        return Vec3d.fromPolar(firePitch, fireYaw).negate().normalize();
    }

    /**
     * Lane length after the first block along the axis; {@code blockDistance} is negative when the axis is clear.
     * 沿轴线遇到第一个方块后的通道长度；轴线畅通时 {@code blockDistance} 为负。
     */
    public static double laneLength(double blockDistance) {
        if (blockDistance < 0.0) {
            return PotionGunnerRules.BACKBLAST_LENGTH;
        }
        return Math.min(PotionGunnerRules.BACKBLAST_LENGTH, blockDistance);
    }

    /**
     * Distance along the lane at which it enters {@code targetBox} (inflated), 0 when the eye is already inside, or
     * -1 for a miss or a target whose centre is not behind the eye.
     * 通道进入（扩大后的）{@code targetBox} 处的距离；眼部已在其内为 0；未命中或目标中心不在眼部之后为 -1。
     */
    public static double hitDistance(Vec3d origin, Vec3d backwards, double laneLength, Box targetBox) {
        if (laneLength <= 0.0 || targetBox.getCenter().subtract(origin).dotProduct(backwards) <= 0.0) {
            return -1.0;
        }
        Vec3d end = origin.add(backwards.multiply(laneLength));
        double squared = HitscanLagRules.entryDistanceSquared(origin, end,
                List.of(targetBox.expand(PotionGunnerRules.BACKBLAST_HALF_WIDTH)));
        return squared < 0.0 ? -1.0 : Math.sqrt(squared);
    }

    /**
     * Where the line-of-sight check ends: the point of the target's real (slightly inset) box nearest to the lane
     * point at {@code distance}, so a player beside the lane behind a wall stays covered.
     * 视线检查的终点：目标真实碰撞箱（略微内收）上距离通道 {@code distance} 处的点最近的位置，因此站在通道旁、隔着墙的
     * 玩家仍然受到遮挡。
     */
    public static Vec3d sightPoint(Vec3d origin, Vec3d backwards, double distance, Box targetBox) {
        Vec3d lanePoint = origin.add(backwards.multiply(Math.max(0.0, distance)));
        Box inner = targetBox.contract(LOS_INSET);
        return new Vec3d(
                MathHelper.clamp(lanePoint.x, inner.minX, inner.maxX),
                MathHelper.clamp(lanePoint.y, inner.minY, inner.maxY),
                MathHelper.clamp(lanePoint.z, inner.minZ, inner.maxZ));
    }

    /** One candidate on the lane. / 通道上的一名候选者。 */
    public record Hit<T>(T target, double distance, boolean clear) {
    }

    /** The nearest candidate with a clear line; ties keep list order. / 视线畅通的最近候选者；距离相同保留列表顺序。 */
    public static <T> Optional<T> nearest(List<Hit<T>> hits) {
        return nearestHit(hits).map(Hit::target);
    }

    /** Like {@link #nearest} but keeps the lane distance. / 同 {@link #nearest}，但保留通道距离。 */
    public static <T> Optional<Hit<T>> nearestHit(List<Hit<T>> hits) {
        Hit<T> best = null;
        for (Hit<T> hit : hits) {
            if (hit.distance() < 0.0 || !hit.clear()) {
                continue;
            }
            if (best == null || hit.distance() < best.distance()) {
                best = hit;
            }
        }
        return Optional.ofNullable(best);
    }
}
