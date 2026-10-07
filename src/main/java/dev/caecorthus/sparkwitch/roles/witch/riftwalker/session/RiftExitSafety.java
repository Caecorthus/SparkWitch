package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

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

/**
 * Server-only exit landing check, a Riftwalker-owned copy of the Angler's {@code FisherSpiritExit.isSafe} rules
 * (research 03 §5.2) on the occupant's STANDING box: finite, inside the world height and above the Wathe play area's
 * fall line (the rest of the play area is ignored, owner 2026-10-05), inside the world border, every touched chunk already loaded (never loads one), no block collision probed with no
 * entity context (door exemptions cannot hide solids), reachable from the gate by a block-free sweep (B-2, ring cells
 * only), a 1/16-block floor under the whole footprint, no fluid, and no other collidable entity or living player in the
 * box (optionally relaxed for forced exits).
 * 仅服务端的出门落点检查，是钓鱼佬 {@code FisherSpiritExit.isSafe} 规则的隙行者自有副本（调研 03 §5.2），按门内玩家的
 * 站立碰撞箱判断：坐标有限、位于世界高度内且不低于 Wathe 游戏区域的坠落线（区域其余部分忽略，所有者 2026-10-05）、位于世界边界内、涉及的区块均已加载（从不加载区块）、
 * 无实体上下文探测方块碰撞（穿门豁免无法隐藏实体方块）、能从门口无碰撞地扫掠到达（B-2，仅限圈内格）、整个脚底下方 1/16 格有地面、没有流体、盒内没有其他可碰撞实体或
 * 存活玩家（强制出门时可放宽此条）。
 */
final class RiftExitSafety {
    private static final double SUPPORT_DEPTH = 0.0625;

    private RiftExitSafety() {
    }

    /**
     * {@code sweepFrom} (nullable): the gate position the body must be able to reach {@code feet} from without touching
     * a block ({@link RiftExitSearch#sweptBody}, B-2); null skips the sweep (the pre-entry position).
     * {@code sweepFrom}（可为空）：身体必须能从该门位置不碰方块地到达 {@code feet}（{@link RiftExitSearch#sweptBody}，B-2）；
     * 为 null 时不扫掠（进门前位置）。
     */
    static boolean isSafe(ServerPlayerEntity player, ServerWorld world, Vec3d feet, @Nullable Vec3d sweepFrom,
                          boolean allowEntityOverlap) {
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
        return allowEntityOverlap || world.getOtherEntities(player, body, other -> !other.isSpectator()
                && (other.isCollidable() || other instanceof PlayerEntity)).isEmpty();
    }

    /**
     * Pure bounds rule: finite, inside the world height, and feet not below the play area's floor (Wathe kills a
     * living player only below {@code playArea.minY}). The rest of the play area is ignored like gate placement
     * (owner 2026-10-05): a gate may stand where the server's play area does not reach, and a stricter rule would
     * leave its occupants with no safe exit, stuck inside.
     * 纯边界规则：坐标有限、在世界高度内、脚底不低于 play area 底面（Wathe 只在存活玩家低于 {@code playArea.minY} 时判死）。
     * 与放门一致忽略 play area 的其余部分（所有者 2026-10-05）：门可能立在服务器 play area 覆盖不到的地方，更严的规则会让
     * 门内的人没有安全出口而被困在门里。
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
