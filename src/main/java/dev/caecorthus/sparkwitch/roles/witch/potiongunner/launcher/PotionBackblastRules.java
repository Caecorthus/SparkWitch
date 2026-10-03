package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;

/**
 * Pure backblast geometry (coordinator decision D-R2, owner: "身后四格"). The lane follows the shot's yaw only: it
 * starts at the gunner's eye and runs horizontally straight behind the gunner, whatever the pitch, for
 * {@link PotionGunnerRules#BACKBLAST_LENGTH} blocks or up to the first block, whichever is shorter. Membership is a fat
 * ray: player boxes are inflated by {@link PotionGunnerRules#BACKBLAST_HALF_WIDTH} on every side, and only a player
 * whose box centre has a positive horizontal offset along the backwards vector counts, so nobody beside or in front of
 * the gunner is hit. The reported distance is measured on the player's real box (see {@link #hitDistance}), the same
 * measure Seeker devices use, and only the nearest player with a clear line from the eye is hit.
 * 纯尾焰几何（协调者决定 D-R2，所有者：“身后四格”）。判定通道只跟随发射的偏航角：从药炮手眼部出发，无论俯仰如何都水平地
 * 沿药炮手正后方延伸 {@link PotionGunnerRules#BACKBLAST_LENGTH} 格或到第一个方块为止（取较短者）。是否在通道内按“粗射线”
 * 判定：玩家碰撞箱向各个方向扩大 {@link PotionGunnerRules#BACKBLAST_HALF_WIDTH}，且只有碰撞箱中心沿向后方向的水平偏移为正
 * 的玩家才计入，因此站在药炮手身旁或身前的人不会被击中。返回的距离按玩家真实碰撞箱量取（见 {@link #hitDistance}），
 * 与搜寻者设备的量法相同；只有从眼部视线畅通的最近一名玩家被击中。
 */
public final class PotionBackblastRules {
    /** Pulls the line-of-sight end point off the box surface. / 把视线终点从碰撞箱表面向内收。 */
    static final double LOS_INSET = 0.05;

    private PotionBackblastRules() {
    }

    /**
     * Horizontal unit vector pointing straight behind a shot fired at {@code fireYaw}; the pitch never tilts it.
     * 以 {@code fireYaw} 发射时指向正后方的水平单位向量；俯仰角从不使其倾斜。
     */
    public static Vec3d backwards(float fireYaw) {
        // Exact double trig (not the MathHelper sine table), so a player exactly beside the gunner is never "behind".
        // 使用精确的双精度三角函数（而非 MathHelper 正弦表），使正好站在身旁的玩家绝不会被算作“身后”。
        double radians = Math.toRadians(fireYaw);
        return new Vec3d(Math.sin(radians), 0.0, -Math.cos(radians));
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
     * Lane distance to a player, or -1 when the fat lane misses the inflated box or the box centre is not behind the
     * eye. The distance is taken on the REAL box so it compares fairly with a Seeker device's ray entry: where the lane
     * axis enters the real box (0 when the eye is inside it), or, for a player touched only by the lane's half-width,
     * how far along the lane the real box begins; never beyond {@code laneLength}.
     * 到玩家的通道距离；粗射线未进入扩大后的碰撞箱、或碰撞箱中心不在眼部之后时返回 -1。距离按真实碰撞箱量取，以便与搜寻者
     * 设备的射线入射距离公平比较：通道轴线进入真实碰撞箱处（眼部已在其内为 0）；若玩家只被通道半宽擦到，则取真实碰撞箱
     * 沿通道开始的位置；不超过 {@code laneLength}。
     */
    public static double hitDistance(Vec3d origin, Vec3d backwards, double laneLength, Box targetBox) {
        if (laneLength <= 0.0 || !isBehind(origin, backwards, targetBox)) {
            return -1.0;
        }
        Vec3d end = origin.add(backwards.multiply(laneLength));
        double fat = HitscanLagRules.entryDistanceSquared(origin, end,
                List.of(targetBox.expand(PotionGunnerRules.BACKBLAST_HALF_WIDTH)));
        if (fat < 0.0) {
            return -1.0;
        }
        double axis = HitscanLagRules.entryDistanceSquared(origin, end, List.of(targetBox));
        if (axis >= 0.0) {
            return Math.sqrt(axis);
        }
        return Math.min(laneLength, Math.max(Math.sqrt(fat), nearSide(origin, backwards, targetBox)));
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

    /**
     * The nearest candidate with a clear line, keeping its lane distance; ties keep list order.
     * 视线畅通的最近候选者，保留其通道距离；距离相同保留列表顺序。
     */
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

    /** The box centre's horizontal offset has a positive dot with the backwards vector. / 碰撞箱中心的水平偏移与向后向量点积为正。 */
    private static boolean isBehind(Vec3d origin, Vec3d backwards, Box box) {
        Vec3d center = box.getCenter();
        return (center.x - origin.x) * backwards.x + (center.z - origin.z) * backwards.z > 0.0;
    }

    /** Smallest lane coordinate of any point of the box. / 碰撞箱上任一点在通道上的最小坐标。 */
    private static double nearSide(Vec3d origin, Vec3d backwards, Box box) {
        double x = (backwards.x >= 0.0 ? box.minX : box.maxX) - origin.x;
        double z = (backwards.z >= 0.0 ? box.minZ : box.maxZ) - origin.z;
        return Math.max(0.0, x * backwards.x + z * backwards.z);
    }
}
