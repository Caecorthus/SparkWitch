package dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import net.minecraft.util.math.Vec3d;

import java.util.function.BooleanSupplier;

/**
 * Pure Shriek Gun decisions: the fire gate, the ally/enemy hit plan, the knockback vector and the beam particle
 * spacing. Every tuning number comes from {@link AbyssListenerRules}; only vanilla protocol facts live here.
 * 啸音铳的纯判定：开火门槛、队友/敌人命中方案、击退向量与射线粒子间距。所有调参数值都来自
 * {@link AbyssListenerRules}；这里只放原版协议事实。
 */
public final class ShriekGunRules {
    /**
     * Vanilla {@code EntityVelocityUpdateS2CPacket} clamps each axis to ±3.9 blocks/tick and replaces the client
     * velocity, so anything larger would be invisible to the pushed player. / 原版速度同步包每轴限幅 ±3.9。
     */
    public static final double VELOCITY_SYNC_CAP = 3.9;
    /** One Warden-style sonic boom particle per block along the beam. / 沿射线每格一个监守者式音爆粒子。 */
    public static final double PARTICLE_STEP = 1.0;
    private static final double DEGENERATE_SQUARED = 1.0E-6;

    private ShriekGunRules() {
    }

    /**
     * What one accepted hit does. Allies get the farther, flatter push and Speed; enemies get the strong push, and the
     * whole suppression bundle (sanity, Slowness, Blindness, forced cooldowns) only when their sanity is real.
     * 一次命中的效果。队友获得更远更平的推力与速度；敌人获得强力击退，且只有拥有真实理智时才会受到整套压制
     * （理智、缓慢、失明、强制冷却）。
     */
    public record HitPlan(boolean ally, double horizontalSpeed, double lift, boolean suppress, boolean allySpeed) {
    }

    /**
     * Pure fire gate; later seams are consulted only after every earlier one passed. A refusal costs nothing.
     * 纯开火门槛；前序条件全部通过后才查询后续接缝。被拒绝时不产生任何代价。
     */
    public static boolean canFire(boolean running, boolean abyssListener, BooleanSupplier aliveParticipant,
                                  BooleanSupplier coolingDown, BooleanSupplier weaponActionBlocked) {
        return running
                && abyssListener
                && aliveParticipant.getAsBoolean()
                && !coolingDown.getAsBoolean()
                && !weaponActionBlocked.getAsBoolean();
    }

    /**
     * Vendetta isolation, as for every single-target hitscan (Taser, Clock, shotgun): an active Vendetta endpoint is
     * reachable only inside its exact pair, so it is transparent to the beam. / 复仇者隔离：激活的一端只在精确配对内可被影响。
     */
    public static boolean vendettaAllows(boolean shooterVendetta, boolean targetVendetta, boolean exactPair) {
        return !(shooterVendetta || targetVendetta) || exactPair;
    }

    /** Hit plan for a target that already passed eligibility. / 已通过资格判定的目标的命中方案。 */
    public static HitPlan planFor(boolean ally, boolean realSanity) {
        if (ally) {
            return new HitPlan(true, AbyssListenerRules.GUN_ALLY_KNOCKBACK, AbyssListenerRules.GUN_ALLY_LIFT,
                    false, true);
        }
        return new HitPlan(false, AbyssListenerRules.GUN_ENEMY_KNOCKBACK, AbyssListenerRules.GUN_ENEMY_LIFT,
                realSanity, false);
    }

    /**
     * Absolute push velocity: the horizontal unit of {@code beamDirection} (else of {@code fallbackDirection}, else no
     * horizontal part) times {@code horizontalSpeed}, and {@code max(currentVy, lift)} upwards, every axis clamped to
     * the ±{@link #VELOCITY_SYNC_CAP} sync cap. Written with {@code setVelocity} because a server player's stored
     * velocity is stale, so adding to it would make the lift unpredictable.
     * 绝对推力速度：取 {@code beamDirection} 的水平单位向量（退化时取 {@code fallbackDirection}，再退化则无水平分量）乘以
     * {@code horizontalSpeed}，竖直方向为 {@code max(currentVy, lift)}，各轴限制在 ±{@link #VELOCITY_SYNC_CAP}。
     * 使用 {@code setVelocity} 写入，因为服务端玩家保存的速度是过时的，叠加会让抬升不可预测。
     */
    public static Vec3d knockback(Vec3d beamDirection, Vec3d fallbackDirection, double currentVy,
                                  double horizontalSpeed, double lift) {
        Vec3d horizontal = horizontalUnit(beamDirection);
        if (horizontal == null) {
            horizontal = horizontalUnit(fallbackDirection);
        }
        double x = horizontal == null ? 0.0 : horizontal.x * horizontalSpeed;
        double z = horizontal == null ? 0.0 : horizontal.z * horizontalSpeed;
        double y = Math.max(currentVy, lift);
        return new Vec3d(capAxis(x), capAxis(y), capAxis(z));
    }

    /**
     * Distances from the eye at which a particle is spawned: every {@link #PARTICLE_STEP} up to the cut point, or one
     * particle at the cut point when the beam is shorter than a step. / 粒子生成位置（距眼睛的距离）。
     */
    public static double[] particleDistances(double cutDistance) {
        if (!(cutDistance > 0.0)) {
            return new double[0];
        }
        int steps = (int) Math.floor(cutDistance / PARTICLE_STEP + 1.0E-9);
        if (steps == 0) {
            return new double[]{cutDistance};
        }
        double[] distances = new double[steps];
        for (int step = 1; step <= steps; step++) {
            distances[step - 1] = step * PARTICLE_STEP;
        }
        return distances;
    }

    static double capAxis(double value) {
        return Math.max(-VELOCITY_SYNC_CAP, Math.min(VELOCITY_SYNC_CAP, value));
    }

    private static Vec3d horizontalUnit(Vec3d direction) {
        if (direction == null) {
            return null;
        }
        Vec3d horizontal = new Vec3d(direction.x, 0.0, direction.z);
        return horizontal.lengthSquared() < DEGENERATE_SQUARED ? null : horizontal.normalize();
    }
}
