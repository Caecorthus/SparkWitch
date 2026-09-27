package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Side-neutral Clock ray geometry, shared by the authoritative server use and the client crosshair so both agree.
 * Who counts as a candidate is the caller's predicate (the server passes the full targeting veto, the client only
 * public state), so this class reads no role, trait or faction data.
 * 两端通用的时钟射线几何，由服务端权威使用与客户端准星共用，使双方判定一致。候选资格由调用方的判定决定
 * （服务端传入完整的目标否决，客户端只传入公开状态），因此本类不读取任何职业、词条或阵营数据。
 */
public final class ClockGeometry {
    /**
     * Line-of-sight sample points sit this far inside the real hitbox, so a point never lies on a face shared with a
     * floor, wall or door (vanilla extends a raycast by 1e-7 past its end and would report that surface as a hit).
     * 视线采样点位于真实命中盒内侧的该距离处，使采样点永远不落在与地板、墙或门共用的面上
     * （原版射线会越过终点 1e-7，会把该表面判为命中）。
     */
    static final double SAMPLE_INSET = 0.1D;
    /** Tolerance for "the block hit is at (or past) the sample point". / “方块命中点位于采样点处（或之后）”的容差。 */
    private static final double LOS_EPSILON = 1.0E-4D;

    private ClockGeometry() {
    }

    /**
     * Frozen contract (mirrors {@code ControlExpertTaserTargeting.findTarget}): the nearest eligible player along
     * {@code user}'s eye ray of length {@code range}, truncated at the first collider block, where eligibility is
     * decided before any geometry so ineligible players are transparent, and the pick uses hitboxes expanded by
     * {@link TimeStealerRules#CLOCK_BOX_EXPANSION} for aim tolerance only. Two hard checks apply to each ray-hit
     * candidate before the nearest pick: the eye-to-real-(unexpanded)-hitbox distance must be at most {@code range},
     * and the line of sight from the eye to the unexpanded hitbox must be unobstructed by collider blocks (a door is
     * thinner than the expansion), so an out-of-range or hidden player never shields a reachable one behind it.
     * Callers pass {@link TimeStealerRules#CLOCK_RANGE}. Returns {@code null} when nothing qualifies.
     * 冻结契约（对应 {@code ControlExpertTaserTargeting.findTarget}）：沿 {@code user} 视线、长度为 {@code range}
     * 且在第一个碰撞方块处截断的射线上最近的合格玩家；资格判定先于任何几何计算，因此不合格者是透明的；选取时命中盒
     * 按 {@link TimeStealerRules#CLOCK_BOX_EXPANSION} 扩展，仅用于瞄准容差。选取最近者之前，对每个被射线触及的候选者
     * 施加两道硬校验：眼睛到真实（未扩展）命中盒的距离不超过 {@code range}，且眼睛到未扩展命中盒的视线不被碰撞方块
     * 遮挡（门的厚度小于扩展量），因此超出距离或被遮住的玩家绝不会挡住其后可命中的玩家。
     * 调用方传入 {@link TimeStealerRules#CLOCK_RANGE}。无人合格时返回 {@code null}。
     */
    public static <T extends PlayerEntity> @Nullable T findTarget(PlayerEntity user, double range,
                                                                  Iterable<? extends T> candidates,
                                                                  Predicate<? super T> eligible) {
        if (user == null || candidates == null || eligible == null || !(range > 0.0D)) {
            return null;
        }
        World world = user.getWorld();
        // On the server vanilla has already applied the use packet's yaw and pitch, so this is the user's real aim.
        // 服务端调用时原版已应用使用物品数据包中的朝向，因此这就是使用者真实的瞄准方向。
        Vec3d start = user.getEyePos();
        Vec3d end = start.add(user.getRotationVec(1.0F).multiply(range));
        BlockHitResult block = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        return pick(start, end, range, candidates, candidate -> candidate != user && eligible.test(candidate),
                Entity::getBoundingBox, (from, to) -> segmentClear(world, from, to, user));
    }

    /**
     * Pure core over the block-truncated segment {@code start..end}: eligibility first, then the expanded-box ray hit,
     * then the two hard checks as a per-candidate filter, and the nearest survivor (by expanded-box entry) wins. The
     * hard checks run only for a candidate nearer than the current best, so sight rays stay rare. A candidate failing
     * a hard check is skipped exactly like an ineligible one: it can neither be stolen from nor shield anyone.
     * {@code segmentClear} answers whether no collider block lies between two points.
     * 在已按方块截断的线段 {@code start..end} 上的纯核心：先判定资格，再做扩展盒射线命中，随后把两道硬校验作为逐个
     * 候选者的过滤条件，最终由（按扩展盒进入距离）最近的幸存者胜出。只对比当前最佳者更近的候选者执行硬校验，使视线射线
     * 保持稀少。未通过硬校验的候选者与不合格者一样被跳过：既不会被窃取，也不会挡住其他人。
     * {@code segmentClear} 回答两点之间是否没有碰撞方块。
     */
    static <T> @Nullable T pick(Vec3d start, Vec3d end, double range, Iterable<? extends T> candidates,
                                Predicate<? super T> eligible, Function<? super T, Box> boxOf,
                                BiPredicate<Vec3d, Vec3d> segmentClear) {
        T selected = null;
        double closest = Double.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            if (!eligible.test(candidate)) {
                continue;
            }
            Box box = boxOf.apply(candidate);
            Box aimBox = box.expand(TimeStealerRules.CLOCK_BOX_EXPANSION);
            Vec3d hit = aimBox.contains(start) ? start : aimBox.raycast(start, end).orElse(null);
            if (hit == null) {
                continue;
            }
            double distance = start.squaredDistanceTo(hit);
            if (distance >= closest
                    || squaredDistanceToBox(start, box) > range * range
                    || !hasLineOfSight(start, end, box, segmentClear)) {
                continue;
            }
            closest = distance;
            selected = candidate;
        }
        return selected;
    }

    /** Squared distance from {@code point} to the closest point of {@code box} (0 inside). / 点到盒最近点距离的平方（在盒内为 0）。 */
    static double squaredDistanceToBox(Vec3d point, Box box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0D), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0D), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0D), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether any part of the real {@code box} is visible from {@code start}. The aim segment already stops at the
     * first collider block, so an aim ray that reaches the real box proves sight on its own; otherwise at least one
     * inset sample point must be reachable through collider shapes. A player flush behind a closed door is picked only
     * through the 0.2 expansion, and every sample point lies behind the door, so it is rejected.
     * 真实的 {@code box} 是否有任一部分能从 {@code start} 看到。瞄准线段已在第一个碰撞方块处截止，因此瞄准射线本身
     * 触及真实命中盒即足以证明可见；否则至少要有一个内缩采样点能穿过碰撞形状到达。紧贴关着的门背后的玩家只会经 0.2
     * 扩展被选中，而其所有采样点都在门后，因此会被拒绝。
     */
    static boolean hasLineOfSight(Vec3d start, Vec3d end, Box box, BiPredicate<Vec3d, Vec3d> segmentClear) {
        if (box.contains(start) || box.raycast(start, end).isPresent()) {
            return true;
        }
        for (Vec3d point : samplePoints(box)) {
            if (segmentClear.test(start, point)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Centre column and the four inset corners, each at the upper-body and mid-body height of the real box.
     * 真实命中盒的中轴与四个内缩角，各取上身与中部两个高度。
     */
    static List<Vec3d> samplePoints(Box box) {
        double inset = Math.min(SAMPLE_INSET,
                Math.min(box.getLengthX(), Math.min(box.getLengthY(), box.getLengthZ())) / 2.0D);
        double minX = box.minX + inset;
        double maxX = box.maxX - inset;
        double minZ = box.minZ + inset;
        double maxZ = box.maxZ - inset;
        Vec3d center = box.getCenter();
        double top = box.maxY - inset;
        double mid = center.y;
        return List.of(
                new Vec3d(center.x, top, center.z),
                new Vec3d(minX, top, minZ),
                new Vec3d(maxX, top, minZ),
                new Vec3d(minX, top, maxZ),
                new Vec3d(maxX, top, maxZ),
                center,
                new Vec3d(minX, mid, minZ),
                new Vec3d(maxX, mid, minZ),
                new Vec3d(minX, mid, maxZ),
                new Vec3d(maxX, mid, maxZ));
    }

    /** No collider block between {@code from} and {@code to}. / 两点之间没有碰撞方块。 */
    private static boolean segmentClear(World world, Vec3d from, Vec3d to, Entity context) {
        double length = from.squaredDistanceTo(to);
        if (length < LOS_EPSILON * LOS_EPSILON) {
            return true;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(
                from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, context));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        double blocked = Math.sqrt(from.squaredDistanceTo(hit.getPos()));
        return blocked >= Math.sqrt(length) - LOS_EPSILON;
    }
}
