package dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRecord;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * Frozen deflection instance returned by every gate. Arguments: projectile, the gate (as hit entity), server random.
     * Vanilla calls it only on the server ({@code deflect} returns early on clients), then re-sets the same owner and
     * remembers the gate as {@code lastDeflectedEntity}; {@code hitOrDeflect} never calls {@code onCollision} for a
     * non-NONE deflection, so nothing explodes or hits at the gate, and a capped projectile simply passes through.
     * 每扇门返回的冻结偏转实例。参数：投掷物、门（命中实体）、服务端随机数。原版只在服务端调用它（客户端的 {@code deflect}
     * 直接返回），随后重设同一主人并把门记为 {@code lastDeflectedEntity}；对非 NONE 偏转，{@code hitOrDeflect} 从不调用
     * {@code onCollision}，因此门处不会爆炸或命中，达到上限的投掷物直接穿过。
     */
    public static final ProjectileDeflection DEFLECTION = RiftGateProjectileService::deflect;

    /** Teleports and reflections per projectile (weak keys). / 每个投掷物的传送与反弹次数（弱键）。 */
    private static final RiftGatePassLedger PASSES = new RiftGatePassLedger(RiftwalkerRules.PROJECTILE_MAX_GATE_PASSES);
    /** Gate lookup padding around a throwing-axe segment (gate volumes are at most ~1.3 wide). / 飞斧线段的门搜索外扩量。 */
    private static final double AXE_SEARCH_PADDING = 1.0;
    private static boolean registered;

    private RiftGateProjectileService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Weak keys already release discarded projectiles; a stopped server drops the rest. / 弱键已释放移除的投掷物；服务器停止时清空其余记录。
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PASSES.clear());
    }

    /**
     * Whether projectiles may hit (and so be deflected by) this gate: always, on both sides, so the client simulation
     * also skips its local hit at a gate. The pass cap is decided inside {@link #DEFLECTION} on the server.
     * 投掷物是否可以命中（从而被偏转）此门：双端恒为是，客户端模拟也因此跳过门处的本地命中。穿门上限由服务端的
     * {@link #DEFLECTION} 判定。
     */
    public static boolean isProjectileTarget(RiftGateEntity gate) {
        return gate != null;
    }

    /**
     * Server tick of each gate: catch projectiles that skip entity collision (SparkStrength M67) on their last
     * segment. Fail-closed when SparkStrength or its M67 id is absent.
     * 每扇门的服务端 tick：在最后一段轨迹上捕获不走实体碰撞的投掷物（SparkStrength M67）。缺少 SparkStrength 或其 M67 id 时不生效。
     */
    public static void sweepUncollidable(RiftGateEntity gate) {
        RiftGateM67Sweep.sweep(gate);
    }

    // ---- Vanilla deflection seam / 原版偏转接缝 ----

    private static void deflect(ProjectileEntity projectile, @Nullable Entity hitEntity, Random random) {
        if (projectile == null || projectile.isRemoved() || !(hitEntity instanceof RiftGateEntity gate)
                || !(projectile.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d from = projectile.getPos();
        Vec3d velocity = projectile.getVelocity();
        if (!PASSES.tryConsume(projectile)) {
            // Capped: end the tick just inside the gate volume so the next tick ignores the gate and collides with
            // whatever is behind it. / 已达上限：本刻结束在门体积内侧，下一刻忽略此门并照常碰撞门后的物体。
            Vec3d end = RiftProjectileMath.passThroughEnd(gateVolume(gate), from, from.add(velocity));
            if (end != null) {
                place(projectile, sameTickStart(world, end, velocity), velocity, projectile.getYaw(),
                        projectile.getPitch());
            }
            return;
        }
        Optional<RiftGateEntity> destination = pickDestination(world, gate, projectile, random);
        if (destination.isPresent()) {
            Exit exit = exitThrough(projectile, gate, destination.get(), velocity);
            place(projectile, sameTickStart(world, exit.position(), exit.velocity()), exit.velocity());
            passEffects(world, from, exit.position());
        } else {
            Vec3d reflected = RiftProjectileMath.reflect(velocity);
            place(projectile, sameTickStart(world, from, reflected), reflected);
            reflectEffects(world, from);
        }
    }

    /**
     * Start of the vanilla same-tick move that ends on {@code end}; falls back to {@code end} itself when that start
     * lies in a chunk that is not entity-ticking (a far gate at a chunk border), so the projectile never hops through
     * a non-ticking section.
     * 原版同刻移动的起点，使其恰好结束在 {@code end}；若起点所在区块不处于实体 tick 范围（远处、位于区块边界的门），
     * 则退回 {@code end} 本身，避免投掷物途经不 tick 的区段。
     */
    private static Vec3d sameTickStart(ServerWorld world, Vec3d end, Vec3d velocity) {
        Vec3d start = RiftProjectileMath.sameTickMoveStart(end, velocity);
        return world.shouldTickEntity(BlockPos.ofFloored(start)) ? start : end;
    }

    // ---- NoellesRoles throwing axe (MX.RiftThrowingAxeMixin) / NoellesRoles 飞斧 ----

    /** Nearest gate the axe meets this tick and the squared distance to it. / 飞斧本刻遇到的最近门及其平方距离。 */
    public record ThrowingAxeGateHit(RiftGateEntity gate, double entrySquared) {
    }

    /**
     * Server only, before the axe's pierce loop: the nearest gate its segment enters before any block, or null. Gates
     * are transparent once the axe has used its passes.
     * 仅服务端，飞斧贯穿循环之前：线段在碰到方块之前进入的最近门，没有则为 null。飞斧用完穿门次数后门对它透明。
     */
    @Nullable
    public static ThrowingAxeGateHit findThrowingAxeGate(ProjectileEntity axe, Vec3d from, Vec3d to) {
        if (axe == null || from == null || to == null || !(axe.getWorld() instanceof ServerWorld world)
                || !PASSES.hasPassesLeft(axe)) {
            return null;
        }
        List<RiftGateEntity> gates = world.getEntitiesByClass(RiftGateEntity.class,
                new Box(from, to).expand(AXE_SEARCH_PADDING), RiftGateProjectileService::isLiveTarget);
        if (gates.isEmpty()) {
            return null;
        }
        Vec3d end = clipToBlocks(world, axe, from, to);
        RiftGateEntity nearest = null;
        double nearestSquared = Double.POSITIVE_INFINITY;
        for (RiftGateEntity gate : gates) {
            double entry = RiftProjectileMath.entrySquared(gateVolume(gate), from, end);
            if (entry >= 0.0 && entry < nearestSquared) {
                nearest = gate;
                nearestSquared = entry;
            }
        }
        return nearest == null ? null : new ThrowingAxeGateHit(nearest, nearestSquared);
    }

    /**
     * Server only, after the pierce loop (players before the gate were hit as usual): teleport or reflect the axe so
     * it ends this tick at the exit (or where it was) and returns true, meaning vanilla movement must be skipped this
     * tick; false leaves the axe untouched.
     * 仅服务端，贯穿循环之后（门之前的玩家已照常被命中）：传送或反弹飞斧，使其本刻结束在出口（或原位）并返回 true，
     * 表示本刻必须跳过原版移动；返回 false 时不改动飞斧。
     */
    public static boolean passThrowingAxe(ProjectileEntity axe, RiftGateEntity gate) {
        if (axe == null || gate == null || axe.isRemoved() || gate.isRemoved()
                || !(axe.getWorld() instanceof ServerWorld world) || !PASSES.tryConsume(axe)) {
            return false;
        }
        Vec3d from = axe.getPos();
        Vec3d velocity = axe.getVelocity();
        Optional<RiftGateEntity> destination = pickDestination(world, gate, axe, axe.getRandom());
        if (destination.isPresent()) {
            Exit exit = exitThrough(axe, gate, destination.get(), velocity);
            place(axe, exit.position(), exit.velocity());
            passEffects(world, from, exit.position());
        } else {
            Vec3d reflected = RiftProjectileMath.reflect(velocity);
            place(axe, from, reflected);
            reflectEffects(world, from);
        }
        return true;
    }

    // ---- Shared server steps (also used by RiftGateM67Sweep) / 共享的服务端步骤 ----

    /** Exit position and velocity at a destination gate. / 目标门处的出口位置与速度。 */
    record Exit(Vec3d position, Vec3d velocity) {
    }

    static boolean tryConsumePass(ProjectileEntity projectile) {
        return PASSES.tryConsume(projectile);
    }

    static boolean hasPassesLeft(ProjectileEntity projectile) {
        return PASSES.hasPassesLeft(projectile);
    }

    /** The gate volume every projectile meets (box plus the vanilla margin). / 所有投掷物遇到的门体积（碰撞箱加原版外扩）。 */
    static Box gateVolume(RiftGateEntity gate) {
        return gate.getBoundingBox().expand(RiftProjectileMath.GATE_TARGET_MARGIN);
    }

    static boolean isLiveTarget(RiftGateEntity gate) {
        return gate != null && gate.isAlive() && !gate.isRemoved() && gate.canBeHitByProjectile();
    }

    /**
     * Uniform pick among the OTHER registered gates of this world whose entity is loaded and whose gate and exit point
     * are entity-ticking (moving a projectile into a non-ticking section would freeze it); empty means reflect.
     * 在本世界其他已登记、实体已加载、门与出口点都处于实体 tick 范围内的门中均匀随机选择（把投掷物移入不 tick 的区段会使其
     * 冻结）；为空表示反弹。
     */
    static Optional<RiftGateEntity> pickDestination(ServerWorld world, RiftGateEntity source,
                                                    ProjectileEntity projectile, Random random) {
        List<RiftGateEntity> candidates = new ArrayList<>();
        int sourceNumber = source.gateNumber();
        for (RiftGateRecord record : RiftGateRegistry.gates(world)) {
            if (record.number() == sourceNumber) {
                continue;
            }
            RiftGateRegistry.entity(world, record.number())
                    .filter(gate -> gate != source && world.shouldTickEntity(gate.getBlockPos())
                            && world.shouldTickEntity(BlockPos.ofFloored(exitPosition(projectile, gate))))
                    .ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    static Exit exitThrough(ProjectileEntity projectile, RiftGateEntity source, RiftGateEntity destination,
                            Vec3d velocity) {
        return new Exit(exitPosition(projectile, destination),
                RiftProjectileMath.exitVelocity(velocity, source.facing(), destination.facing()));
    }

    private static Vec3d exitPosition(ProjectileEntity projectile, RiftGateEntity destination) {
        return RiftProjectileMath.exitPosition(destination.getPos(), destination.facing(), projectile.getWidth(),
                projectile.getHeight());
    }

    static void place(ProjectileEntity projectile, Vec3d position, Vec3d velocity) {
        place(projectile, position, velocity, RiftProjectileMath.yawOf(velocity), RiftProjectileMath.pitchOf(velocity));
    }

    /**
     * Moves the projectile in place (never re-spawned, so spawn-time boosts such as SparkTraits Herculean are not
     * applied twice) and forces the tracker to resend position and velocity this tick.
     * 原地移动投掷物（从不重新生成，因此 SparkTraits 大力士等生成时加成不会重复生效），并强制追踪器在本刻重发位置与速度。
     */
    private static void place(ProjectileEntity projectile, Vec3d position, Vec3d velocity, float yaw, float pitch) {
        projectile.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        projectile.setVelocity(velocity);
        projectile.velocityDirty = true;
        projectile.velocityModified = true;
    }

    static void passEffects(ServerWorld world, Vec3d entry, Vec3d exit) {
        burst(world, entry);
        burst(world, exit);
    }

    static void reflectEffects(ServerWorld world, Vec3d at) {
        burst(world, at);
    }

    private static void burst(ServerWorld world, Vec3d at) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.05);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL,
                0.4F, 1.6F);
    }

    /** End of the segment clipped at the first block, like vanilla projectile collision. / 在首个方块处截断的线段终点。 */
    private static Vec3d clipToBlocks(ServerWorld world, Entity projectile, Vec3d from, Vec3d to) {
        HitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, projectile));
        return block.getType() == HitResult.Type.MISS ? to : block.getPos();
    }
}
