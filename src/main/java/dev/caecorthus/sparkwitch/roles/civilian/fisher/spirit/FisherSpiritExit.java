package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-only exit: probe with no entity context so Fisher, Wraith and provider door exemptions cannot hide solids.
 * The window remains active until teleport/velocity correction has been sent. Never load a chunk to find an exit.
 * 仅服务端退出：无实体上下文探测，灵光、冤魂与提供方的穿门豁免均不能隐藏实体碰撞。发送传送/速度修正后
 * 才能清除窗口；不会为了寻找出口加载区块。
 */
final class FisherSpiritExit {
    private static final double SUPPORT_DEPTH = 0.0625;

    private FisherSpiritExit() {
    }

    static void rememberSafePosition(ServerPlayerEntity player, FisherSpiritComponent component) {
        if (isSafe(player, player.getPos())) {
            component.lastSafePosition = player.getPos();
        }
    }

    static void moveOutOfDoor(ServerPlayerEntity player, FisherSpiritComponent component) {
        List<Box> doors = intersectingDoors(player);
        if (doors.isEmpty()) {
            return;
        }
        Vec3d destination = FisherExitSearch.select(
                FisherExitSearch.candidates(player.getPos(), player.getBoundingBox(), doors),
                component.startedWorld == player.getServerWorld() ? component.lastSafePosition : null,
                candidate -> isSafe(player, candidate));
        if (destination == null) {
            // A changed map can invalidate every safe location. Do not prolong phasing or teleport into unchecked blocks.
            // 地图变化可能让所有安全点失效；不延长穿门，也不传送到未通过检查的方块中。
            SparkWitch.LOGGER.warn("No safe Glimmerfish door exit for {} in {}", player.getUuid(),
                    player.getWorld().getRegistryKey().getValue());
            return;
        }
        player.requestTeleportAndDismount(destination.x, destination.y, destination.z);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0;
    }

    private static List<Box> intersectingDoors(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Box body = player.getBoundingBox();
        List<Box> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(body.minX, body.minY, body.minZ),
                BlockPos.ofFloored(body.maxX, body.maxY, body.maxZ))) {
            if (!world.isChunkLoaded(pos)) {
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (FisherDoorPassingRules.isDoor(state)) {
                for (Box shape : state.getCollisionShape(world, pos, ShapeContext.absent()).getBoundingBoxes()) {
                    Box door = shape.offset(pos);
                    if (body.intersects(door)) {
                        result.add(door);
                    }
                }
            }
        }
        return result;
    }

    private static boolean isSafe(ServerPlayerEntity player, Vec3d feet) {
        ServerWorld world = player.getServerWorld();
        Box body = player.getBoundingBox().offset(feet.subtract(player.getPos()));
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        if (!FisherExitSearch.insideBounds(body, world.getBottomY(), world.getTopY(), playArea)
                || !world.getWorldBorder().contains(body) || !chunksLoaded(world, body.expand(1))) {
            return false;
        }
        if (world.getBlockCollisions(null, body).iterator().hasNext()) {
            return false;
        }
        // Probe the actual floor beneath the whole footprint, including slabs; a block below the centre is insufficient.
        // 探测整个脚底下的实际地面（含台阶），仅检查中心下方方块并不足够。
        Box support = new Box(body.minX, body.minY - SUPPORT_DEPTH, body.minZ, body.maxX, body.minY, body.maxZ);
        return world.getBlockCollisions(null, support).iterator().hasNext()
                && world.getOtherEntities(player, body, other -> !other.isSpectator()
                && (other.isCollidable() || other instanceof PlayerEntity)).isEmpty();
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
