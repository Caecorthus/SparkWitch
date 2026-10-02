package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

/**
 * Server-side landing validation for the Witches' Sabbath (research/04 §2, 04b §2–§6), a role-owned copy of the
 * Angler exit checks plus the teleport-specific rules. Every test uses the TARGET's standing box (a crouched caster
 * is shorter than a standing teammate) and a {@code null} entity / absent shape context, so no door-passing exemption
 * (Angler, Wraith, providers) can hide a solid. A spot must be inside the world height, Wathe's play area (below its
 * {@code minY} Wathe kills) and the world border; in loaded chunks (a chunk is never loaded to find a spot); free of
 * block collision, fluid (Wathe drowning), door cells and the open-door drop column, Rift Gate boxes and collidable
 * entities or living players; on a real floor; and reachable by a clear collider ray from the caster (no pulling
 * through a wall into the next cabin or onto the roof).
 * 魔女集会的服务端落点校验（research/04 §2、04b §2–§6），是钓鱼佬出口检查的职业自有副本，外加传送特有的规则。所有检查都
 * 使用目标的站立碰撞箱（蹲下的施放者比站立的队友矮），并使用 {@code null} 实体 / 空形状上下文，因此任何穿门豁免
 * （钓鱼佬、冤魂、提供方）都无法隐藏实体方块。落点必须位于世界高度、Wathe 游戏区域（低于其 {@code minY} 会被 Wathe 判死）
 * 与世界边界之内；处于已加载区块（绝不为找落点加载区块）；没有方块碰撞、流体（Wathe 溺亡）、门格及开门掉落列、裂隙门碰撞箱、
 * 可碰撞实体或活着的玩家；脚下有真实地面；并且从施放者出发的碰撞射线畅通（不会穿墙拉进隔壁车厢或拉上车顶）。
 */
final class WitchesSabbathLanding {
    private WitchesSabbathLanding() {
    }

    /** One spot per target around the caster, in target order. / 在施放者周围为每个目标分配一个落点，按目标顺序。 */
    static List<WitchesSabbathLandingPlan.Landing<ServerPlayerEntity>> plan(ServerWorld world, ServerPlayerEntity caster,
                                                                        List<ServerPlayerEntity> targets) {
        Vec3d origin = caster.getPos();
        @Nullable Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        return WitchesSabbathLandingPlan.plan(
                targets,
                WitchesSabbathLandingPlan.candidates(origin),
                (target, candidate) -> find(world, origin, playArea, target, candidate),
                WitchesSabbathLanding::standingBoxAt);
    }

    static Box standingBoxAt(PlayerEntity target, Vec3d feet) {
        return target.getDimensions(EntityPose.STANDING).getBoxAt(feet);
    }

    /** Snaps the candidate onto the floor near the caster's height, then validates it. / 吸附到施放者高度附近的地面后校验。 */
    private static @Nullable Vec3d find(ServerWorld world, Vec3d origin, @Nullable Box playArea,
                                        ServerPlayerEntity target, Vec3d candidate) {
        Box footprint = standingBoxAt(target, candidate);
        if (!chunksLoaded(world, footprint.expand(1.0))) {
            return null;
        }
        OptionalDouble floor = floorTop(world, footprint, candidate.y);
        if (floor.isEmpty()) {
            return null;
        }
        Vec3d feet = new Vec3d(candidate.x, floor.getAsDouble(), candidate.z);
        return isSafe(world, origin, playArea, target, feet, standingBoxAt(target, feet)) ? feet : null;
    }

    private static OptionalDouble floorTop(ServerWorld world, Box footprint, double feetY) {
        double step = WitchesSabbathRules.FLOOR_STEP;
        Box column = new Box(footprint.minX, feetY - step, footprint.minZ, footprint.maxX, feetY + step, footprint.maxZ);
        DoubleStream.Builder tops = DoubleStream.builder();
        for (VoxelShape shape : world.getBlockCollisions(null, column)) {
            if (!shape.isEmpty()) {
                tops.add(shape.getMax(Direction.Axis.Y));
            }
        }
        return WitchesSabbathLandingPlan.snapFloor(feetY, tops.build().toArray(), step);
    }

    private static boolean isSafe(ServerWorld world, Vec3d origin, @Nullable Box playArea, ServerPlayerEntity target,
                                  Vec3d feet, Box body) {
        if (!WitchesSabbathLandingPlan.insideBounds(body, world.getBottomY(), world.getTopY(), playArea)
                || !world.getWorldBorder().contains(body)
                || !chunksLoaded(world, body.expand(1.0))) {
            return false;
        }
        // Null entity context: door-passing exemptions can never hide a solid. / null 实体上下文：穿门豁免无法隐藏实体方块。
        if (world.getBlockCollisions(null, body).iterator().hasNext()) {
            return false;
        }
        // The floor under the whole footprint, including slabs. / 整个脚底下的地面，含台阶。
        Box support = new Box(body.minX, body.minY - WitchesSabbathRules.SUPPORT_DEPTH, body.minZ,
                body.maxX, body.minY, body.maxZ);
        if (!world.getBlockCollisions(null, support).iterator().hasNext()) {
            return false;
        }
        if (world.containsFluid(body) || touchesDoor(world, body)) {
            return false;
        }
        if (!world.getEntitiesByClass(RiftGateEntity.class, body.expand(WitchesSabbathRules.GATE_MARGIN),
                gate -> !gate.isRemoved()).isEmpty()) {
            return false;
        }
        // Players are solid in Wathe; spectators (dead, in-gate bodies) are not obstacles. / Wathe 中玩家是实体障碍；旁观者不是。
        if (!world.getOtherEntities(target, body, other -> !other.isSpectator()
                && (other.isCollidable() || other instanceof PlayerEntity)).isEmpty()) {
            return false;
        }
        return hasClearPath(world, origin, feet);
    }

    /**
     * Any Wathe door part or vanilla door in the body's cells or up to {@link WitchesSabbathRules#DOOR_COLUMN_DROP}
     * below: open doors leave an empty collision column a player can drop into.
     * 身体所在格或其下方 {@link WitchesSabbathRules#DOOR_COLUMN_DROP} 内存在 Wathe 门部件或原版门：开着的门会留下可掉落的空碰撞列。
     */
    private static boolean touchesDoor(ServerWorld world, Box body) {
        BlockPos min = BlockPos.ofFloored(body.minX, body.minY - WitchesSabbathRules.DOOR_COLUMN_DROP, body.minZ);
        BlockPos max = BlockPos.ofFloored(body.maxX, body.maxY, body.maxZ);
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            Block block = world.getBlockState(pos).getBlock();
            if (block instanceof DoorPartBlock || block instanceof DoorBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * A collider ray between both feet raised by {@link WitchesSabbathRules#SIGHT_RAY_HEIGHT}, with an absent shape
     * context (closed doors and walls block it; open doors do not).
     * 两端脚底抬高 {@link WitchesSabbathRules#SIGHT_RAY_HEIGHT} 之间的碰撞射线，使用空形状上下文（关着的门与墙会挡住，开着的门不会）。
     */
    private static boolean hasClearPath(ServerWorld world, Vec3d origin, Vec3d feet) {
        Vec3d from = origin.add(0.0, WitchesSabbathRules.SIGHT_RAY_HEIGHT, 0.0);
        Vec3d to = feet.add(0.0, WitchesSabbathRules.SIGHT_RAY_HEIGHT, 0.0);
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent())).getType() == HitResult.Type.MISS;
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
