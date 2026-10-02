package dev.caecorthus.sparkwitch.roles.witch.riftwalker.swapper;

import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Server-only search for the cell in front of a gate where the crushed Swapper's body lands (research 06 §4.3 option
 * A). The safety probe is a copy of the Glimmerfish exit check ({@code FisherSpiritExit.isSafe}): inside the play area
 * and world border, chunks loaded, no block collision (no entity context, so no door exemption), a real floor under
 * the whole footprint and no solid entity or living player in the way. Never loads a chunk.
 * 仅服务端：寻找被夹死的交换者尸体落在门前的格子（调研 06 §4.3 方案 A）。安全检查照抄灵光鱼出口检查
 * （{@code FisherSpiritExit.isSafe}）：在游戏区域与世界边界内、区块已加载、无方块碰撞（无实体上下文，不享受穿门豁免）、
 * 整个脚底有真实地面、没有实体或活着的玩家挡路。从不加载区块。
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
            if (isSafe(swapper, world, feet)) {
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
                && world.getOtherEntities(swapper, body, other -> !other.isSpectator()
                && (other.isCollidable() || other instanceof PlayerEntity)).isEmpty();
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
