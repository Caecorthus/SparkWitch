package dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile;

import dev.caecorthus.sparkwitch.compat.SparkStrengthM67Compat;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * External seam: SparkStrength's M67 skips entity collision and runs its own block-swept motion, so the deflection
 * seam never sees it. Each gate's server tick tests the M67's last movement (previous to current position) against its
 * volume; the nearest gate on that segment acts, teleporting the grenade in place to another gate's front or reflecting
 * it, with the shared pass cap. The type is resolved by registry id only ({@code sparkstrength:m67}) and the sweep is
 * inert when SparkStrength or that id is absent; no SparkStrength class is named. Whichever of gate and grenade ticks
 * first, every movement segment is tested exactly once, and a moved grenade's segment collapses (previous = current).
 * 外部接缝：SparkStrength 的 M67 不走实体碰撞、自行做方块扫掠运动，偏转接缝无法捕获它。每扇门的服务端 tick 用 M67 的上一段
 * 位移（上一位置到当前位置）检测自身体积；线段上最近的门负责处理，按共享的穿门上限把手雷原地传送到另一扇门正面或反弹。
 * 仅以注册 id（{@code sparkstrength:m67}）解析类型，缺少 SparkStrength 或该 id 时不生效；不引用任何 SparkStrength 类。
 * 无论门和手雷谁先 tick，每段位移都恰好检测一次；被移动的手雷其线段随即收缩（上一位置 = 当前位置）。
 * Accepted edge: SparkStrength interpolates the M67 on clients, so a long jump is drawn as a short slide.
 * 已接受的边角情况：SparkStrength 在客户端对 M67 做插值，长距离跳转会显示为一段短暂滑动。
 */
final class RiftGateM67Sweep {
    /** Covers one M67 movement segment past the gate volume (launch speed 2.25 plus gravity). / 覆盖越过门体积的一段 M67 位移。 */
    static final double SEARCH_RADIUS = 4.0;
    /** Gate lookup padding around a segment. / 线段周围的门搜索外扩量。 */
    private static final double GATE_SEARCH_PADDING = 1.0;

    private static boolean resolved;
    @Nullable
    private static EntityType<?> m67Type;

    private RiftGateM67Sweep() {
    }

    static void sweep(RiftGateEntity gate) {
        if (gate == null || gate.isRemoved() || !(gate.getWorld() instanceof ServerWorld world)) {
            return;
        }
        EntityType<?> type = m67Type();
        if (type == null) {
            return;
        }
        Box volume = RiftGateProjectileService.gateVolume(gate);
        List<ProjectileEntity> grenades = world.getEntitiesByClass(ProjectileEntity.class,
                volume.expand(SEARCH_RADIUS), entity -> entity.getType() == type && entity.isAlive());
        for (ProjectileEntity grenade : grenades) {
            if (grenade.isRemoved() || !RiftGateProjectileService.hasPassesLeft(grenade)) {
                continue;
            }
            Vec3d from = new Vec3d(grenade.prevX, grenade.prevY, grenade.prevZ);
            Vec3d to = grenade.getPos();
            double entry = RiftProjectileMath.entrySquared(volume, from, to);
            if (entry < 0.0 || nearerGateOnSegment(world, gate, from, to, entry)
                    || !RiftGateProjectileService.tryConsumePass(grenade)) {
                continue;
            }
            pass(world, gate, grenade, from);
        }
    }

    /**
     * The grenade already moved (or is about to move) on its own, with block collision, so it is placed exactly at
     * the exit (or back at the start of its last movement when reflected); no same-tick compensation applies.
     * 手雷自身已经（或即将）带方块碰撞地移动，因此直接放到出口（反弹时放回上一段位移的起点）；不需要同刻补偿。
     */
    private static void pass(ServerWorld world, RiftGateEntity gate, ProjectileEntity grenade, Vec3d from) {
        Vec3d velocity = grenade.getVelocity();
        Optional<RiftGateProjectileService.Exit> exit = RiftGateProjectileService.pickExit(world, gate, grenade,
                velocity, grenade.getRandom(), false);
        if (exit.isPresent()) {
            RiftGateProjectileService.place(grenade, exit.get().position(), exit.get().velocity());
            RiftGateProjectileService.passEffects(world, from, exit.get().position());
        } else {
            RiftGateProjectileService.place(grenade, from, RiftProjectileMath.reflect(velocity));
            RiftGateProjectileService.reflectEffects(world, from);
        }
    }

    /** Another live gate entered earlier on the same segment handles it. / 同一线段上更早进入的其他门负责处理。 */
    private static boolean nearerGateOnSegment(ServerWorld world, RiftGateEntity gate, Vec3d from, Vec3d to,
                                               double entrySquared) {
        for (RiftGateEntity other : world.getEntitiesByClass(RiftGateEntity.class,
                new Box(from, to).expand(GATE_SEARCH_PADDING), RiftGateProjectileService::isLiveTarget)) {
            if (other == gate) {
                continue;
            }
            double entry = RiftProjectileMath.entrySquared(RiftGateProjectileService.gateVolume(other), from, to);
            if (entry >= 0.0 && entry < entrySquared) {
                return true;
            }
        }
        return false;
    }

    /** Resolved once by id; null (inert) when SparkStrength or the id is absent. / 按 id 解析一次；缺失时为 null（不生效）。 */
    @Nullable
    private static EntityType<?> m67Type() {
        if (!resolved) {
            resolved = true;
            m67Type = FabricLoader.getInstance().isModLoaded(SparkStrengthM67Compat.MOD_ID)
                    ? Registries.ENTITY_TYPE.getOrEmpty(SparkStrengthM67Compat.M67_ENTITY_ID).orElse(null)
                    : null;
        }
        return m67Type;
    }
}
