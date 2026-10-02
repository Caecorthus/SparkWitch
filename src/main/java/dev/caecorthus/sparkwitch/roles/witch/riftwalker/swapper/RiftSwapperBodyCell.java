package dev.caecorthus.sparkwitch.roles.witch.riftwalker.swapper;

import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server-only search for the cell in front of a gate where the crushed Swapper's body lands (research 06 §4.3 option
 * A). The safety probe is a copy of the Glimmerfish exit check ({@code FisherSpiritExit.isSafe}): inside the play area
 * and world border, chunks loaded, no block collision (no entity context, so no door exemption), a real floor under
 * the whole footprint, no fluid at the waist or eyes and no solid entity or living player in the way; plus the cell
 * must be reachable from the gate opening (a clear collider ray from the gate's centre), so the body and its drops
 * never land in the next compartment behind a one-block wall (M-4, C8). Never loads a chunk.
 * 仅服务端：寻找被夹死的交换者尸体落在门前的格子（调研 06 §4.3 方案 A）。安全检查照抄灵光鱼出口检查
 * （{@code FisherSpiritExit.isSafe}）：在游戏区域与世界边界内、区块已加载、无方块碰撞（无实体上下文，不享受穿门豁免）、
 * 整个脚底有真实地面、腰部与眼部没有流体、没有实体或活着的玩家挡路；此外该格必须能从门口到达（从门中心出发的碰撞射线
 * 畅通），尸体与掉落物绝不会落到一格墙后的隔壁车厢（M-4、C8）。从不加载区块。
 */
final class RiftSwapperBodyCell {
    private static final double SUPPORT_DEPTH = 0.0625;

    private RiftSwapperBodyCell() {
    }

    /** First safe feet position in front of the gate, or null. / 门前第一个安全的脚底位置；没有则为 null。 */
    static @Nullable Vec3d find(ServerPlayerEntity swapper, ServerWorld world, Vec3d gatePos, Direction facing) {
        Direction front = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        int facingX = front.getOffsetX();
        int facingZ = front.getOffsetZ();
        for (RiftSwapperCrushRules.BodyOffset offset : RiftSwapperCrushRules.bodyOffsets()) {
            Vec3d feet = gatePos.add(
                    RiftSwapperCrushRules.worldDeltaX(offset, facingX, facingZ),
                    offset.up(),
                    RiftSwapperCrushRules.worldDeltaZ(offset, facingX, facingZ));
            if (isSafe(swapper, world, feet) && reachableFromGate(world, gatePos, feet)) {
                return feet;
            }
        }
        return null;
    }

    private static boolean isSafe(ServerPlayerEntity swapper, ServerWorld world, Vec3d feet) {
        // Standing box: the Swapper may be crouching now, but the body and the dead spectator start standing.
        // 使用站立碰撞箱：交换者此刻可能在潜行，但尸体与死亡旁观者都以站立姿态出现。
        Box body = PlayerEntity.STANDING_DIMENSIONS.getBoxAt(feet);
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        if (!insideBounds(body, world.getBottomY(), world.getTopY(), playArea)
                || !world.getWorldBorder().contains(body) || !chunksLoaded(world, body.expand(1))) {
            return false;
        }
        if (world.getBlockCollisions(null, body).iterator().hasNext()) {
            return false;
        }
        Box support = new Box(body.minX, body.minY - SUPPORT_DEPTH, body.minZ, body.maxX, body.minY, body.maxZ);
        return world.getBlockCollisions(null, support).iterator().hasNext()
                && !inFluid(world, feet)
                && world.getOtherEntities(swapper, body, other -> !other.isSpectator()
                && (other.isCollidable() || other instanceof PlayerEntity)).isEmpty();
    }

    /**
     * Fluid at the waist or the standing eye height; a waterlogged floor slab does not count.
     * 腰部或站立视线高度处有流体；含水的地面台阶不计。
     */
    private static boolean inFluid(ServerWorld world, Vec3d feet) {
        double eye = PlayerEntity.STANDING_DIMENSIONS.eyeHeight();
        return !world.getFluidState(BlockPos.ofFloored(feet.x, feet.y + RiftSwapperCrushRules.WAIST_HEIGHT, feet.z))
                .isEmpty()
                || !world.getFluidState(BlockPos.ofFloored(feet.x, feet.y + eye, feet.z)).isEmpty();
    }

    /**
     * A collider ray (absent shape context: walls, panes and closed doors block it) from the gate's centre to the
     * middle of the candidate body hits no block, i.e. the cell is on the gate-opening side of any wall.
     * 从门中心到候选身体中部的碰撞射线（空形状上下文：墙、玻璃板与关着的门都会挡住）不碰到任何方块，即该格与门口位于
     * 同一侧，没有隔着墙。
     */
    private static boolean reachableFromGate(ServerWorld world, Vec3d gatePos, Vec3d feet) {
        Vec3d from = gatePos.add(0.0, RiftSwapperCrushRules.GATE_RAY_HEIGHT, 0.0);
        Vec3d to = feet.add(0.0, RiftSwapperCrushRules.BODY_RAY_HEIGHT, 0.0);
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent())).getType() == HitResult.Type.MISS;
    }

    private static boolean insideBounds(Box body, int bottom, int top, @Nullable Box playArea) {
        return Double.isFinite(body.minX) && Double.isFinite(body.maxX)
                && Double.isFinite(body.minY) && Double.isFinite(body.maxY)
                && Double.isFinite(body.minZ) && Double.isFinite(body.maxZ)
                && body.minY >= bottom && body.maxY <= top
                && (playArea == null || body.minX >= playArea.minX && body.maxX <= playArea.maxX
                && body.minY >= playArea.minY && body.maxY <= playArea.maxY
                && body.minZ >= playArea.minZ && body.maxZ <= playArea.maxZ);
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
