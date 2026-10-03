package dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Side-neutral Shriek Gun beam geometry (a role-owned duplicate of the Taser's), shared by the authoritative server
 * hit and the client crosshair so both agree on range, block occlusion and the box margin. Who counts as a candidate is
 * the caller's predicate (server: full eligibility; client: public state only), and where a candidate can be hit is
 * the caller's volume provider (server: lag-compensated; client: current boxes), so this class reads no role, faction
 * or trait data. Closed doors stay solid through the shared {@code RaycastShapeScope} wrapper on every
 * {@link RaycastContext} block query.
 * 两端通用的啸音铳射线几何（电击枪几何的职业自有副本），由服务端权威命中与客户端准星共用，使双方在射程、方块遮挡与
 * 箱体余量上一致。候选资格由调用方的判定决定（服务端：完整资格；客户端：仅公开状态），候选者可被命中的位置由调用方的
 * 体积提供者决定（服务端：延迟补偿；客户端：当前箱体），因此本类不读取任何职业、阵营或词条数据。关闭的门通过共享的
 * {@code RaycastShapeScope} 包装（作用于每次 {@link RaycastContext} 方块查询）保持实心。
 */
public final class ShriekGunTargeting {
    /** Same player-box margin as the Taser and the Death Ray. / 与电击枪、死光相同的玩家箱体余量。 */
    public static final double BOX_EXPANSION = 0.2;

    private ShriekGunTargeting() {
    }

    /** A picked player and its squared entry distance from the beam start. / 选中的玩家及其距射线起点的平方进入距离。 */
    public record Hit<T>(T target, double distanceSquared) {
    }

    /**
     * End of {@code user}'s eye beam within {@code range}, cut at the first COLLIDER block (doors, panes and fences
     * stop it). On the server vanilla has already applied the use packet's yaw and pitch, so this is the aim.
     * {@code user} 视线射线在 {@code range} 内的终点，在第一个 COLLIDER 方块处截断（门、玻璃板与栅栏都会挡住）。
     * 服务端调用时原版已应用使用物品数据包中的朝向，因此这就是射手的瞄准方向。
     */
    public static Vec3d beamEnd(PlayerEntity user, double range) {
        Vec3d start = user.getEyePos();
        Vec3d end = start.add(user.getRotationVec(1.0F).multiply(range));
        BlockHitResult block = user.getWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }

    /**
     * Client crosshair form: the first eligible player on the block-cut beam against current boxes.
     * 客户端准星形式：按当前箱体判定，在被方块截断的射线上的第一个合格玩家。
     */
    public static <T extends PlayerEntity> @Nullable T findTarget(PlayerEntity user, Iterable<? extends T> candidates,
                                                                  Predicate<? super T> eligible) {
        Vec3d start = user.getEyePos();
        Hit<T> hit = firstHit(user, start, beamEnd(user, AbyssListenerRules.GUN_RANGE), candidates, eligible,
                ShriekGunTargeting::currentVolumes);
        return hit == null ? null : hit.target();
    }

    /**
     * First eligible player other than {@code user} on {@code start → end} (already cut at blocks), tested against
     * {@code volumesOf} (already grown by {@link #BOX_EXPANSION}, never more). Only the first player is hit (owner C1).
     * 在 {@code start → end}（已按方块截断）上除 {@code user} 外的第一个合格玩家，按 {@code volumesOf}
     * （已含 {@link #BOX_EXPANSION}，不再额外扩大）判定。只命中第一名玩家（所有者 C1）。
     */
    public static <T extends PlayerEntity> @Nullable Hit<T> firstHit(PlayerEntity user, Vec3d start, Vec3d end,
                                                                     Iterable<? extends T> candidates,
                                                                     Predicate<? super T> eligible,
                                                                     Function<? super T, List<Box>> volumesOf) {
        return nearest(start, end, candidates, candidate -> candidate != user && eligible.test(candidate), volumesOf);
    }

    /** The current box grown by the gun margin. / 当前箱体加枪械余量。 */
    public static List<Box> currentVolumes(PlayerEntity candidate) {
        return List.of(candidate.getBoundingBox().expand(BOX_EXPANSION));
    }

    /**
     * Pure pick over the segment {@code start..end}. Eligibility is decided before any geometry, so an ineligible
     * player is transparent and never shields the player behind it; the nearest entry wins.
     * 在线段 {@code start..end} 上的纯选择。资格判定先于任何几何计算，因此不合格的玩家是透明的，
     * 永远不会替身后的玩家挡下射线；最近的进入点获胜。
     */
    static <T> @Nullable Hit<T> nearest(Vec3d start, Vec3d end, Iterable<? extends T> candidates,
                                        Predicate<? super T> eligible, Function<? super T, List<Box>> volumesOf) {
        T selected = null;
        double closest = Double.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            if (!eligible.test(candidate)) {
                continue;
            }
            double distance = HitscanLagRules.entryDistanceSquared(start, end, volumesOf.apply(candidate));
            if (distance >= 0.0 && distance < closest) {
                closest = distance;
                selected = candidate;
            }
        }
        return selected == null ? null : new Hit<>(selected, closest);
    }
}
