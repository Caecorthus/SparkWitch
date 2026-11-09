package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Pure, side-neutral flight rules of {@link SmokeRoundEntity} (G3): arming after {@link BearRules#ARMING_DISTANCE}
 * blocks of path, the damped bounce of an unarmed impact, coming to rest, the backup fuse and the burst point. The
 * ballistics themselves are {@link M32Ballistics}. Bounce numbers: the normal component keeps {@link #RESTITUTION}
 * (a dull aluminium round on steel or wood), the tangential one {@link #FRICTION}; a round slower than
 * {@link #REST_SPEED} after bouncing off an upward-facing surface lies still until the backup fuse bursts it.
 * {@link SmokeRoundEntity} 的纯计算、两端通用的飞行规则（G3）：飞行 {@link BearRules#ARMING_DISTANCE} 格路径后解除保险、未解除保险
 * 撞击时的阻尼反弹、静止、备用引信与爆开点。弹道本身见 {@link M32Ballistics}。反弹数值：法向分量保留 {@link #RESTITUTION}（铝制
 * 榴弹撞上钢或木头时的闷响反弹），切向分量保留 {@link #FRICTION}；从朝上的表面弹起后速度低于 {@link #REST_SPEED} 的榴弹静止不动，
 * 直到备用引信将其引爆。
 */
public final class SmokeRoundFlight {
    public static final double RESTITUTION = 0.3;
    public static final double FRICTION = 0.6;
    /** Blocks per tick. / 格/刻。 */
    public static final double REST_SPEED = 0.08;
    /** A surface whose normal's y is at least this counts as a floor. / 法线 y 不小于此值的表面视为地面。 */
    public static final double FLOOR_NORMAL_Y = 0.7;
    /** The bounce and burst points sit this far off the surface. / 反弹点与爆开点离开表面的距离。 */
    public static final double SURFACE_OFFSET = 0.05;

    private SmokeRoundFlight() {
    }

    /**
     * Whether an impact {@code alongTick} blocks into this tick's move, after {@code travelledBefore} blocks of path,
     * is armed (G3: the first impact at or beyond 4 blocks bursts the round; any earlier one bounces).
     * 本刻移动 {@code alongTick} 格处、此前已飞行 {@code travelledBefore} 格路径的撞击是否已解除保险（G3：4 格及以后的首次撞击引爆
     * 榴弹；更早的撞击只会反弹）。
     */
    public static boolean armed(double travelledBefore, double alongTick) {
        return travelledBefore + Math.max(0.0, alongTick) >= BearRules.ARMING_DISTANCE;
    }

    /** G3: the backup fuse bursts the round on its 60th tick wherever it is. / G3：备用引信在第 60 刻无论在何处都会引爆。 */
    public static boolean fuseDue(int age) {
        return age >= BearRules.BACKUP_FUSE_TICKS;
    }

    /**
     * The damped reflection of {@code velocity} off a surface with unit outward {@code normal}; a velocity already
     * leaving the surface is returned unchanged.
     * {@code velocity} 在单位外法线为 {@code normal} 的表面上的阻尼反射；已离开表面的速度原样返回。
     */
    public static Vec3d bounce(Vec3d velocity, Vec3d normal) {
        double into = velocity.dotProduct(normal);
        if (into >= 0.0) {
            return velocity;
        }
        Vec3d normalPart = normal.multiply(into);
        Vec3d tangential = velocity.subtract(normalPart);
        return tangential.multiply(FRICTION).subtract(normalPart.multiply(RESTITUTION));
    }

    /** Whether a bounced round lies still. / 反弹后的榴弹是否静止。 */
    public static boolean comesToRest(Vec3d bounced, Vec3d normal) {
        return normal.y >= FLOOR_NORMAL_Y && bounced.length() < REST_SPEED;
    }

    /**
     * Unit outward normal of the face of {@code box} nearest to {@code point} (the face a ray entered at that point).
     * {@code box} 中离 {@code point} 最近的面（射线在该点进入的面）的单位外法线。
     */
    public static Vec3d faceNormal(Box box, Vec3d point) {
        double[] distances = {
                Math.abs(point.x - box.minX), Math.abs(box.maxX - point.x),
                Math.abs(point.y - box.minY), Math.abs(box.maxY - point.y),
                Math.abs(point.z - box.minZ), Math.abs(box.maxZ - point.z)};
        int nearest = 0;
        for (int i = 1; i < distances.length; i++) {
            if (distances[i] < distances[nearest]) {
                nearest = i;
            }
        }
        return switch (nearest) {
            case 0 -> new Vec3d(-1.0, 0.0, 0.0);
            case 1 -> new Vec3d(1.0, 0.0, 0.0);
            case 2 -> new Vec3d(0.0, -1.0, 0.0);
            case 3 -> new Vec3d(0.0, 1.0, 0.0);
            case 4 -> new Vec3d(0.0, 0.0, -1.0);
            default -> new Vec3d(0.0, 0.0, 1.0);
        };
    }

    /**
     * The burst point: {@code impact} stepped {@link #SURFACE_OFFSET} back against the flight {@code direction}, so the
     * puff and the cloud seed start out of the surface ({@code SmokeCloudService.spawn} steps the seed further back
     * itself when that cell is still solid). A degenerate direction keeps {@code impact}.
     * 爆开点：把 {@code impact} 沿飞行方向 {@code direction} 反向后退 {@link #SURFACE_OFFSET}，使烟团粒子与烟雾起点离开表面
     * （若该格仍为实体方块，{@code SmokeCloudService.spawn} 会自行继续后退）。方向退化时保持 {@code impact}。
     */
    public static Vec3d burstPoint(Vec3d impact, Vec3d direction) {
        if (direction == null || !(direction.lengthSquared() > 1.0E-8)) {
            return impact;
        }
        return impact.subtract(direction.normalize().multiply(SURFACE_OFFSET));
    }

    /**
     * Whether a player stops the round: only a survival/adventure, Wathe-living round participant (spectators, creative
     * players, Wraiths and corpses are passed, as the anti-tank shell passes them).
     * 玩家是否挡下榴弹：只有生存/冒险模式且 Wathe 判定存活的对局参与者（旁观者、创造模式玩家、冤魂与尸体都会被穿过，与反坦克炮弹
     * 一致）。
     */
    public static boolean playerStopsRound(boolean aliveAndSurvival, boolean playingAndAlive) {
        return aliveAndSurvival && playingAndAlive;
    }
}
