package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;

/**
 * Pure tuning and decisions of weapon hits on the Raven Form crow (plan v2 §2.5: every weapon that can directly kill a
 * player hits the crow, and a hit that would kill a player kills the Black Raven). The crow is a 0.5 block box flying
 * at up to 24 blocks/s, so every server check that confirms a shooter's aim tests its lag-compensated volumes
 * ({@link BlackRavenCrowHitboxHistory}); projectiles keep vanilla collision on its current box.
 * 乌鸦（化鸦）被武器命中的纯数值与判定（计划 v2 §2.5：所有能直接命中玩家的武器都能命中乌鸦，足以杀死玩家的命中杀死黑羽鸦）。
 * 乌鸦是 0.5 格的箱体，最高 24 格/秒，因此所有确认射手瞄准的服务端检查都测试其延迟补偿体积（{@link BlackRavenCrowHitboxHistory}）；
 * 投射物保持原版的当前箱体碰撞。
 */
public final class BlackRavenCrowHitRules {
    /** Wathe's server cap for a gun target ({@code GunShootPayload$Receiver}). / Wathe 服务端枪械目标距离上限。 */
    public static final double GUN_MAX_DISTANCE = 65.0;
    /**
     * Growth of the rewound crow volumes when the server confirms a client-picked hitscan shot (revolver, derringer,
     * add-on guns): the Hunter shotgun claim's confirm growth, so a crow the shooter's screen showed on the ray is
     * accepted. / 服务端确认客户端选中的即时射击（左轮、德林加、附属枪械）时，回溯乌鸦体积的扩大量：与猎枪声明确认相同。
     */
    public static final double GUN_CONFIRM_GROWTH = 0.5;
    /**
     * Growth of the crow box in the client pick, the margin the Hunter and USEC picks give a player box.
     * 客户端选靶时乌鸦箱体的扩大量，与猎枪、USEC 选取玩家时的余量相同。
     */
    public static final double CLIENT_AIM_GROWTH = 0.1;
    /** Wathe knife stab reach plus the latency tolerance of every client-trusted stab. / Wathe 刀刺距离加延迟容差。 */
    public static final double KNIFE_REACH = 3.5;
    /** Vanilla left-click reach 3 plus its 1-block attack allowance. / 原版左键距离 3 加 1 格攻击余量。 */
    public static final double MELEE_REACH = 4.0;
    /**
     * Wathe grenade: a player dies when its feet lie in the grenade box grown by 3 ({@code GrenadeEntity#onCollision}).
     * Wathe 手雷：玩家脚下位于手雷箱体扩大 3 格的范围内即死亡。
     */
    public static final double GRENADE_REACH = 3.0;
    /** Vanilla projectile entity margin ({@code ProjectileUtil.getEntityCollision}, 0.3F). / 原版投射物实体余量。 */
    public static final double PROJECTILE_MARGIN = 0.3;
    /**
     * SparkStrength M67 kill radius ({@code M67Rules.BLAST_RADIUS}), the sphere the Magician puppet blast uses too.
     * SparkStrength M67 击杀半径，与魔术师皮套爆炸使用的球体相同。
     */
    public static final double M67_RADIUS = 5.0;

    private BlackRavenCrowHitRules() {
    }

    /**
     * Nearest wins: the crow takes the hit only when the ray enters it ({@code crowSquared >= 0}) strictly nearer than
     * everything else it competes with; a tie goes to the player, puppet or device.
     * 最近者命中：只有射线进入乌鸦（{@code crowSquared >= 0}）且严格近于所有竞争对象时乌鸦才承受命中；距离相同时归玩家、皮套或设备。
     */
    public static boolean crowWins(double crowSquared, double beatSquared) {
        return crowSquared >= 0.0 && crowSquared < beatSquared;
    }

    /** The smallest of the squared competitor distances (+inf when none). / 竞争对象平方距离中的最小值（没有时为正无穷）。 */
    public static double nearest(double... squaredDistances) {
        double nearest = Double.POSITIVE_INFINITY;
        for (double distance : squaredDistances) {
            if (distance >= 0.0 && distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    /**
     * Squared distance from {@code start} to the earliest entry into any volume along {@code start -> end}; 0 inside
     * one, -1 on a miss ({@code HitscanLagRules.entryDistanceSquared}, restated so the rule stays pure here).
     * 沿线段最早进入任一体积处的平方距离；起点在内为 0，未命中为 -1。
     */
    public static double entrySquared(Vec3d start, Vec3d end, List<Box> volumes) {
        double nearest = -1.0;
        for (Box volume : volumes) {
            double distance;
            if (volume.contains(start)) {
                distance = 0.0;
            } else {
                Optional<Vec3d> entry = volume.raycast(start, end);
                if (entry.isEmpty()) {
                    continue;
                }
                distance = start.squaredDistanceTo(entry.get());
            }
            if (nearest < 0.0 || distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    /** Squared distance from {@code point} to the nearest volume (0 inside, +inf when none). / 点到最近体积的平方距离。 */
    public static double closestSquared(Vec3d point, List<Box> volumes) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Box volume : volumes) {
            nearest = Math.min(nearest, volume.squaredMagnitude(point));
        }
        return nearest;
    }

    /** Melee and stab reach on the rewound volumes (inclusive). / 按回溯体积判定的近战与刺击距离（含边界）。 */
    public static boolean withinReach(double closestSquared, double reach) {
        return closestSquared <= reach * reach;
    }

    /**
     * Wathe grenade rule applied to the crow's feet position (the player rule, {@code contains(getPos())}).
     * 以乌鸦脚下位置套用 Wathe 手雷规则（玩家规则 {@code contains(getPos())}）。
     */
    public static boolean inGrenadeReach(Box grenadeBox, Vec3d crowPos) {
        return grenadeBox.expand(GRENADE_REACH).contains(crowPos);
    }

    /** Closed blast sphere on the crow's box centre. / 以乌鸦箱体中心计算的闭球判定。 */
    public static boolean inBlastSphere(Vec3d center, Vec3d crowCenter, double radius) {
        return radius >= 0.0 && center.squaredDistanceTo(crowCenter) <= radius * radius;
    }

    /**
     * Path distance where a polyline first enters the volumes, walking its segments in flight order up to
     * {@code limit}; -1 when it never does. Used for the USEC bullet path (AP pierces blocks and the round sinks).
     * 按飞行顺序逐段行进、在 {@code limit} 之内折线首次进入体积处的路径距离；从未进入时为 -1。用于 USEC 子弹路径
     * （AP 穿透方块且子弹下坠）。
     */
    public static double pathEntry(List<Vec3d> path, List<Box> volumes, double limit) {
        if (path == null || path.size() < 2 || !(limit > 0.0)) {
            return -1.0;
        }
        double travelled = 0.0;
        for (int i = 0; i + 1 < path.size() && travelled < limit; i++) {
            Vec3d from = path.get(i);
            Vec3d to = path.get(i + 1);
            double length = from.distanceTo(to);
            if (!(length > 0.0)) {
                continue;
            }
            double cut = Math.min(length, limit - travelled);
            Vec3d end = cut < length ? from.add(to.subtract(from).multiply(cut / length)) : to;
            double entry = entrySquared(from, end, volumes);
            if (entry >= 0.0) {
                return travelled + Math.sqrt(entry);
            }
            travelled += length;
        }
        return -1.0;
    }
}
