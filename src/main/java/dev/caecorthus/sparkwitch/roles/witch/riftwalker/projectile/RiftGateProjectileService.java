package dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import net.minecraft.entity.ProjectileDeflection;

/**
 * Projectiles through Rift Gates (plan §9, research 02 §4.3–4.4), server only. The gate exposes the vanilla 1.21.1
 * deflection seam: {@link RiftGateEntity#canBeHitByProjectile} asks {@link #isProjectileTarget} and
 * {@link RiftGateEntity#getProjectileDeflection} returns {@link #DEFLECTION}, which runs inside
 * {@code ProjectileEntity.deflect} before any explosion or hit (owner kept). It teleports the projectile to the front of
 * a random other gate (rotated velocity, in place, never re-spawned, max {@code PROJECTILE_MAX_GATE_PASSES} passes) or
 * reflects it at full speed when no other gate qualifies (D8: ender pearls included, every faction). Hitscan weapons
 * ignore gates. Owned by P4 (including the NoellesRoles throwing-axe mixin and the M67 sweep).
 * 投掷物穿越裂隙门（plan §9，调研 02 §4.3–4.4），仅服务端。门使用原版 1.21.1 偏转接缝：
 * {@link RiftGateEntity#canBeHitByProjectile} 询问 {@link #isProjectileTarget}，{@link RiftGateEntity#getProjectileDeflection}
 * 返回 {@link #DEFLECTION}，它在 {@code ProjectileEntity.deflect} 中、任何爆炸或命中之前执行（保留原主人）。
 * 投掷物被原地传送到另一扇随机门的正面（速度随朝向旋转、不重新生成、最多穿门 {@code PROJECTILE_MAX_GATE_PASSES} 次），
 * 没有可用的门时原速反弹（D8：含末影珍珠，所有阵营）。射线武器不受门影响。归属 P4（含 NoellesRoles 飞斧 mixin 与 M67 扫描）。
 */
public final class RiftGateProjectileService {
    /**
     * Frozen deflection instance returned by every gate. G0 stub: does nothing (and gates are not projectile targets
     * yet, see {@link #isProjectileTarget}). Arguments: projectile, the gate (as hit entity), server random.
     * 每扇门返回的冻结偏转实例。G0 存根：不做任何事（且门暂不是投掷物目标，见 {@link #isProjectileTarget}）。
     * 参数：投掷物、门（命中实体）、服务端随机数。
     */
    public static final ProjectileDeflection DEFLECTION = (projectile, hitEntity, random) -> {
        // TODO(P4): teleport to a random other gate's front, or reflect at full speed. / TODO(P4)：传送或原速反弹。
    };
    private static boolean registered;

    private RiftGateProjectileService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // TODO(P4): per-projectile pass ledger cleanup, if any. / TODO(P4)：每个投掷物穿门次数记录的清理（如需要）。
    }

    /**
     * Whether projectiles may hit (and so be deflected by) this gate. G0 stub: false, so projectiles pass through and
     * nothing explodes at a gate until P4 lands.
     * 投掷物是否可以命中（从而被偏转）此门。G0 存根：false，P4 落地前投掷物直接穿过，门处不会发生爆炸。
     */
    public static boolean isProjectileTarget(RiftGateEntity gate) {
        // TODO(P4): return true. / TODO(P4)：返回 true。
        return false;
    }

    /**
     * Server tick of each gate: catch projectiles that skip entity collision (SparkStrength M67) on their last
     * segment. G0 stub: no-op.
     * 每扇门的服务端 tick：在最后一段轨迹上捕获不走实体碰撞的投掷物（SparkStrength M67）。G0 存根：空操作。
     */
    public static void sweepUncollidable(RiftGateEntity gate) {
        // TODO(P4)
    }
}
