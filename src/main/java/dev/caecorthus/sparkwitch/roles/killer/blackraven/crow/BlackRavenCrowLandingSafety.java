package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftExitSearch;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-only landing-spot search at the Homing Feather (plan §2.8), a Raven-owned copy of the Riftwalker's
 * {@code RiftExitSafety.isSafe} (package-private there) driven by the public {@link RiftExitSearch} ring: the feather's
 * own spot first, then the Rift ring around it in the player's facing frame, each reachable from the feather by a
 * block-free sweep. A spot is safe on the player's STANDING box: finite, inside the world height and not below the Wathe
 * play area's fall line, inside the world border, every touched chunk already loaded (never loads one), no block
 * collision (probed with no entity context, so door exemptions cannot hide solids), a 1/16-block floor under the whole
 * footprint, no fluid, and no other collidable entity or living player in the box. No safe spot means the player stays
 * at the body.
 * 仅服务端的归羽落点搜索（计划 §2.8），是隙行者 {@code RiftExitSafety.isSafe}（在原处为包内可见）的黑羽鸦自有副本，由公开的
 * {@link RiftExitSearch} 环形候选驱动：先是归羽本身的位置，再按玩家朝向坐标系取其周围的 Rift 环形格，且每格都必须能从归羽处无碰撞扫掠到达。
 * 按玩家站立碰撞箱判断安全：坐标有限、位于世界高度内且不低于 Wathe 游戏区域的坠落线、位于世界边界内、涉及的区块均已加载（从不加载区块）、
 * 无方块碰撞（无实体上下文探测，门的豁免无法隐藏实体方块）、整个脚底下方 1/16 格有地面、没有流体、盒内没有其他可碰撞实体或存活玩家。
 * 找不到安全点时玩家留在本体处。
 */
final class BlackRavenCrowLandingSafety {
    private static final double SUPPORT_DEPTH = 0.0625;

    private BlackRavenCrowLandingSafety() {
    }

    /** First safe feet position at the feather, or null. / 归羽处第一个安全的脚底位置，没有时为 null。 */
    static @Nullable Vec3d findSpot(ServerPlayerEntity player, ServerWorld world, Vec3d feather) {
        if (!BlackRavenCrowUseRules.isFinite(feather)) {
            return null;
        }
        List<Vec3d> candidates = new ArrayList<>();
        candidates.add(feather);
        candidates.addAll(RiftExitSearch.candidates(feather, player.getHorizontalFacing(), null));
        return RiftExitSearch.select(candidates,
                candidate -> isSafe(player, world, candidate, candidate == feather ? null : feather));
    }

    static boolean isSafe(ServerPlayerEntity player, ServerWorld world, Vec3d feet, @Nullable Vec3d sweepFrom) {
        EntityDimensions standing = player.getBaseDimensions(EntityPose.STANDING);
        Box body = standing.getBoxAt(feet);
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        if (!insideBounds(body, world.getBottomY(), world.getTopY(), playArea)
                || !world.getWorldBorder().contains(body) || !chunksLoaded(world, body.expand(1))) {
            return false;
        }
        if (world.getBlockCollisions(null, body).iterator().hasNext() || world.containsFluid(body)) {
            return false;
        }
        if (sweepFrom != null) {
            // Unloaded chunks read as empty to collision queries, so the swept volume must be loaded too.
            // 未加载区块在碰撞查询中视为空，因此扫掠空间也必须已加载。
            Box swept = RiftExitSearch.sweptBody(standing.getBoxAt(sweepFrom), sweepFrom, feet);
            if (!chunksLoaded(world, swept) || world.getBlockCollisions(null, swept).iterator().hasNext()) {
                return false;
            }
        }
        Box support = new Box(body.minX, body.minY - SUPPORT_DEPTH, body.minZ, body.maxX, body.minY, body.maxZ);
        if (!world.getBlockCollisions(null, support).iterator().hasNext()) {
            return false;
        }
        return world.getOtherEntities(player, body, other -> !other.isSpectator()
                && (other.isCollidable() || other instanceof PlayerEntity)).isEmpty();
    }

    /**
     * Pure bounds rule: finite, inside the world height, feet not below the play area's floor (Wathe kills a living
     * player below {@code playArea.minY}). / 纯边界规则：坐标有限、在世界高度内、脚底不低于游戏区域底面。
     */
    static boolean insideBounds(Box body, int bottom, int top, @Nullable Box playArea) {
        return Double.isFinite(body.minX) && Double.isFinite(body.maxX)
                && Double.isFinite(body.minY) && Double.isFinite(body.maxY)
                && Double.isFinite(body.minZ) && Double.isFinite(body.maxZ)
                && body.minY >= bottom && body.maxY <= top
                && (playArea == null || body.minY >= playArea.minY);
    }

    private static boolean chunksLoaded(ServerWorld world, Box box) {
        for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX) >> 4; x++) {
            for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ) >> 4; z++) {
                if (!world.getChunkManager().isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
