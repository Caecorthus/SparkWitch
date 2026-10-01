package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

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
 * Side-neutral Taser ray geometry, shared by the authoritative server hit and the client crosshair so both agree on
 * range, block occlusion and the Taser's box margin. Who counts as a candidate is the caller's predicate: the server
 * passes the full targeting veto, the client only public state, so this class reads no role or trait data. Where a
 * candidate can be hit is the caller's volume provider: the client uses current boxes, the server lag-compensated ones.
 * 两端通用的电击枪射线几何，由服务端权威命中与客户端准星共用，使双方在射程、方块遮挡与电击枪箱体余量上一致。
 * 候选资格由调用方的判定决定：服务端传入完整的目标否决，客户端只传入公开状态，因此本类不读取任何职业或词条数据。
 * 候选者可被命中的位置由调用方的体积提供者决定：客户端用当前箱体，服务端用延迟补偿后的箱体。
 */
public final class ControlExpertTaserTargeting {
    private ControlExpertTaserTargeting() {
    }

    /**
     * Nearest eligible player against its current box grown by {@link ControlExpertRules#TASER_BOX_EXPANSION}; the
     * client crosshair already sees the delayed positions. / 按当前箱体（加电击枪余量）选取最近的合格玩家；
     * 客户端准星看到的本就是延迟后的位置。
     */
    public static <T extends PlayerEntity> @Nullable T findTarget(PlayerEntity user, double range,
                                                                  Iterable<? extends T> candidates,
                                                                  Predicate<? super T> eligible) {
        return findTarget(user, range, candidates, eligible, ControlExpertTaserTargeting::currentVolumes);
    }

    /**
     * Nearest eligible player along {@code user}'s eye ray within {@code range}, truncated at the first collider
     * block, tested against {@code hitVolumes} (already grown by the Taser margin, never more). On the server, vanilla
     * has already applied the use packet's yaw and pitch, so this is the shooter's aim.
     * 沿 {@code user} 视线射线在 {@code range} 内、且在第一个碰撞方块前的最近合格玩家，按 {@code hitVolumes}
     * （已含电击枪余量，不再额外扩大）判定。服务端调用时原版已应用使用物品数据包中的朝向，因此这就是射手的瞄准方向。
     */
    public static <T extends PlayerEntity> @Nullable T findTarget(PlayerEntity user, double range,
                                                                  Iterable<? extends T> candidates,
                                                                  Predicate<? super T> eligible,
                                                                  Function<PlayerEntity, List<Box>> hitVolumes) {
        Vec3d start = user.getEyePos();
        Vec3d end = start.add(user.getRotationVec(1.0F).multiply(range));
        BlockHitResult block = user.getWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getPos();
        }
        return nearest(start, end, candidates, candidate -> candidate != user && eligible.test(candidate), hitVolumes);
    }

    /** The current box grown by the Taser margin. / 当前箱体加电击枪余量。 */
    public static List<Box> currentVolumes(PlayerEntity candidate) {
        return List.of(candidate.getBoundingBox().expand(ControlExpertRules.TASER_BOX_EXPANSION));
    }

    /**
     * Pure pick over the segment {@code start..end}. Eligibility is decided before any geometry, so an ineligible
     * player is transparent and never shields the player behind it.
     * 在线段 {@code start..end} 上的纯选择。资格判定先于任何几何计算，因此不合格的玩家是透明的，
     * 永远不会替身后的玩家挡下射线。
     */
    static <T> @Nullable T nearest(Vec3d start, Vec3d end, Iterable<? extends T> candidates,
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
        return selected;
    }
}
