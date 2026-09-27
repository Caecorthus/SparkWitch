package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Frozen contract: nearest-wins device ray helpers (owner decision Q4). The nearest thing on the ray wins: a device
 * closer than the player target absorbs the hit and shields the player; a closer player is hit exactly as before.
 * Device boxes grow only by their small targeting margin ({@link SeekerDeviceEntity#targetingMargin()}, car ~0.15,
 * camera ~0.05); rays stop at the first COLLIDER block. Side-neutral: the client wrappers use it to pick a device
 * target (the server then validates that id), the server hooks use it to decide nearest-wins themselves. A shooter's
 * own devices are always transparent to them (server: owner UUID; client: the owner-synced device ids).
 * 冻结契约：“最近者命中”的设备射线辅助方法（所有者决定 Q4）。射线上最近的物体命中：比玩家目标更近的设备吸收这次命中
 * 并挡住玩家；更近的玩家则与原来完全一样被命中。设备箱体只按很小的瞄准余量扩大（小车约 0.15、摄像头约 0.05）；
 * 射线在第一个 COLLIDER 方块处停止。两端通用：客户端包装用它选出设备目标（随后由服务端校验该 id），服务端钩子自行用它
 * 判定最近者。射手自己的设备对其始终透明（服务端看拥有者 UUID；客户端看仅同步给拥有者的设备 id）。
 */
public final class SeekerDeviceRaycast {
    /** Search-box padding around a ray segment. / 射线段搜索箱的外扩量。 */
    private static final double SEARCH_PADDING = 1.0;

    private SeekerDeviceRaycast() {
    }

    /** A device hit on a segment: entry point and squared distance from the segment start. / 线段上的设备命中。 */
    public record DeviceHit(SeekerDeviceEntity device, Vec3d point, double distanceSquared) {
    }

    /**
     * Client target wrappers (revolver, derringer, knife, Demon Hunter): returns {@code original} unless a Seeker
     * device lies nearer on the look ray within {@code range}, then that device's EntityHitResult. Side-neutral.
     * The ray uses Wathe's own aim ({@code getRotationVec(0)}, the vanilla collision helper's tick delta) and its
     * length is the original result's distance, so extended ranges (SparkTraits Marksman) are kept automatically;
     * {@code range} is only the fallback when the original carries no usable position.
     * 客户端目标包装（左轮、德林加、刀、猎魔枪）：除非视线上 {@code range} 内有更近的搜寻者设备，
     * 否则原样返回 {@code original}；有则返回该设备的 EntityHitResult。射线使用与 Wathe 相同的朝向，长度取原结果的距离，
     * 因此自动保留延长后的射程（SparkTraits 神射手）；{@code range} 仅在原结果无可用位置时作为兜底。
     */
    public static HitResult preferNearerDevice(Entity shooter, HitResult original, double range) {
        if (shooter == null || original == null) {
            return original;
        }
        Vec3d start = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(0.0F);
        double limit = referenceDistance(start, look, original, range);
        if (!(limit > 0.0)) {
            return original;
        }
        Vec3d end = clipToBlocks(shooter.getWorld(), start, start.add(look.multiply(limit)), shooter);
        // Strictly nearer than the original hit: on a tie the player (or block) keeps the shot.
        // 必须严格近于原命中点：距离相同时仍由玩家（或方块）承受这一枪。
        DeviceHit hit = nearestDevice(shooter.getWorld(), start, end, limit * limit,
                device -> !isOwnDevice(shooter, device));
        if (hit == null) {
            return original;
        }
        return new EntityHitResult(hit.device(), hit.point());
    }

    /**
     * Ray check (side-neutral; server hooks and the optional CE taser crosshair hint): the nearest live device on
     * {@code start → end} that is nearer than {@code playerTarget} (or any device when the target is null); null when
     * the player (or nothing) is nearer. Ignores the shooter's own devices and does not judge breakability.
     * 射线检查（两端通用；服务端钩子与可选的控场专家电击枪准星提示）：在 {@code start → end} 上比
     * {@code playerTarget} 更近（目标为空时任意）的最近存活设备；玩家更近或没有设备时返回 null。
     * 忽略射手自己的设备，不判断能否打坏。
     */
    @Nullable
    public static SeekerDeviceEntity blockingDevice(Entity shooter, Vec3d start, Vec3d end,
                                                    @Nullable Entity playerTarget) {
        DeviceHit hit = blockingHit(shooter, start, end, playerTarget, device -> !isOwnDevice(shooter, device));
        return hit == null ? null : hit.device();
    }

    /**
     * Projectile sweep between two tick positions: the nearest live device on {@code from → to} before the first
     * COLLIDER block. Players and breakability are the caller's concern.
     * 投射物在两个刻位置之间的扫掠检测：{@code from → to} 上、第一个 COLLIDER 方块之前最近的存活设备。
     * 玩家遮挡与能否打坏由调用方判断。
     */
    @Nullable
    public static SeekerDeviceEntity projectileSweep(Entity projectile, Vec3d from, Vec3d to) {
        if (projectile == null) {
            return null;
        }
        Vec3d end = clipToBlocks(projectile.getWorld(), from, to, projectile);
        DeviceHit hit = nearestDevice(projectile.getWorld(), from, end, Double.POSITIVE_INFINITY, device -> true);
        return hit == null ? null : hit.device();
    }

    /** Pure nearest-wins comparison on squared distances. / 基于平方距离的纯“最近者命中”比较。 */
    public static boolean deviceWins(double deviceDistanceSquared, double targetDistanceSquared) {
        return deviceDistanceSquared < targetDistanceSquared;
    }

    // ---- Shared helpers (side-neutral) ----

    /**
     * The nearest eligible device on {@code start → end} (the caller clips to blocks) that beats {@code playerTarget}.
     * {@code start → end} 上击败 {@code playerTarget} 的最近合格设备（调用方负责按方块截断）。
     */
    @Nullable
    static DeviceHit blockingHit(Entity shooter, Vec3d start, Vec3d end, @Nullable Entity playerTarget,
                                 Predicate<SeekerDeviceEntity> eligible) {
        if (shooter == null) {
            return null;
        }
        Vec3d clipped = clipToBlocks(shooter.getWorld(), start, end, shooter);
        double targetDistance = playerTarget == null
                ? Double.POSITIVE_INFINITY
                : targetDistanceSquared(start, clipped, playerTarget.getBoundingBox());
        return nearestDevice(shooter.getWorld(), start, clipped, targetDistance, eligible);
    }

    /**
     * Nearest live eligible device whose margin-grown box meets {@code start → end} strictly nearer than
     * {@code beatDistanceSquared}. / 扩大余量后的箱体与线段相交、且严格近于给定距离的最近存活合格设备。
     */
    @Nullable
    static DeviceHit nearestDevice(World world, Vec3d start, Vec3d end, double beatDistanceSquared,
                                   Predicate<SeekerDeviceEntity> eligible) {
        if (world == null) {
            return null;
        }
        Box search = new Box(start, end).expand(SEARCH_PADDING);
        var devices = world.getEntitiesByClass(SeekerDeviceEntity.class, search,
                device -> device.isAlive() && !device.isRemoved() && eligible.test(device));
        SeekerDeviceEntity device = nearest(start, end, devices, SeekerDeviceRaycast::targetBox, beatDistanceSquared);
        if (device == null) {
            return null;
        }
        Vec3d point = entryPoint(start, end, targetBox(device)).orElse(start);
        return new DeviceHit(device, point, start.squaredDistanceTo(point));
    }

    /**
     * Pure pick: the candidate whose box entry along {@code start → end} is nearest and strictly nearer than
     * {@code beatDistanceSquared}; a box containing {@code start} is at distance 0.
     * 纯选择：沿线段进入箱体最早、且严格近于 {@code beatDistanceSquared} 的候选；包含起点的箱体距离为 0。
     */
    @Nullable
    static <T> T nearest(Vec3d start, Vec3d end, Iterable<? extends T> candidates, Function<? super T, Box> boxOf,
                         double beatDistanceSquared) {
        T selected = null;
        double closest = beatDistanceSquared;
        for (T candidate : candidates) {
            double distance = entryDistanceSquared(start, end, boxOf.apply(candidate));
            if (distance >= 0.0 && deviceWins(distance, closest)) {
                closest = distance;
                selected = candidate;
            }
        }
        return selected;
    }

    /** Squared distance from {@code start} to where the segment enters {@code box}; -1 on a miss. / 未命中返回 -1。 */
    static double entryDistanceSquared(Vec3d start, Vec3d end, Box box) {
        return entryPoint(start, end, box).map(start::squaredDistanceTo).orElse(-1.0);
    }

    static Optional<Vec3d> entryPoint(Vec3d start, Vec3d end, Box box) {
        if (box.contains(start)) {
            return Optional.of(start);
        }
        return box.raycast(start, end);
    }

    /**
     * Distance to a player target: where the ray enters its box, or (a lenient server pick such as the taser's grown
     * box) its closest point. / 玩家目标的距离：射线进入其箱体处；若未相交（如电击枪扩大箱体的宽松选择）则取最近点。
     */
    static double targetDistanceSquared(Vec3d start, Vec3d end, Box targetBox) {
        double entry = entryDistanceSquared(start, end, targetBox);
        return entry >= 0.0 ? entry : SeekerDamageRules.squaredDistanceToBox(start, targetBox);
    }

    /** The device box grown by its own small targeting margin. / 按设备自身小余量扩大的箱体。 */
    static Box targetBox(SeekerDeviceEntity device) {
        return device.getBoundingBox().expand(Math.max(0.0, device.targetingMargin()));
    }

    /** Cuts {@code end} at the first COLLIDER block. / 在第一个 COLLIDER 方块处截断终点。 */
    static Vec3d clipToBlocks(World world, Vec3d start, Vec3d end, Entity context) {
        HitResult block = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, context));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }

    /**
     * Side-neutral ownership: the server knows the owner UUID; the owner's client knows its own device ids from the
     * owner-only {@code sparkwitch:seeker_status} sync (other clients never learn ownership).
     * 两端通用的归属判断：服务端知道拥有者 UUID；拥有者客户端通过仅同步给本人的组件得知自己的设备 id（其他客户端无从得知）。
     */
    public static boolean isOwnDevice(@Nullable Entity shooter, @Nullable SeekerDeviceEntity device) {
        if (shooter == null || device == null) {
            return false;
        }
        UUID owner = device.ownerUuid();
        if (owner != null) {
            return owner.equals(shooter.getUuid());
        }
        if (!(shooter instanceof PlayerEntity player)) {
            return false;
        }
        int id = device.getId();
        return SeekerStatusComponent.KEY.maybeGet(player)
                .map(component -> id == component.carEntityId() || id == component.cameraEntityId())
                .orElse(false);
    }

    /**
     * Reference distance of the original target: the player box entry for an entity hit (Wathe's EntityHitResult
     * carries the feet position), otherwise the hit/miss position; {@code range} when neither is usable.
     * 原目标的参考距离：命中实体时取其箱体入射点（Wathe 的 EntityHitResult 位置为脚下），否则取命中/未命中位置；
     * 两者都不可用时取 {@code range}。
     */
    private static double referenceDistance(Vec3d start, Vec3d look, HitResult original, double range) {
        if (original instanceof EntityHitResult entityHit) {
            Box box = entityHit.getEntity().getBoundingBox();
            double reach = Math.max(range, Math.sqrt(SeekerDamageRules.squaredDistanceToBox(start, box)) + 2.0);
            double entry = entryDistanceSquared(start, start.add(look.multiply(reach)), box);
            if (entry >= 0.0) {
                return Math.sqrt(entry);
            }
            return Math.sqrt(SeekerDamageRules.squaredDistanceToBox(start, box));
        }
        Vec3d position = original.getPos();
        if (position != null) {
            double distance = start.distanceTo(position);
            if (Double.isFinite(distance) && distance > 0.0) {
                return distance;
            }
        }
        return range;
    }
}
