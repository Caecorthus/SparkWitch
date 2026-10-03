package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
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
 * block collision, fluid (Wathe drowning), door cells and the open-door drop column, Rift Gate boxes, placed Hunter
 * traps (their trigger reach) and collidable entities or living players; on a real floor; and reachable by a clear
 * collider ray from the caster that crosses no door cell (no pulling through a wall or a doorway into the next cabin,
 * or onto the roof). Control Expert shock devices need no check: they only stun where they land and then vanish.
 * 魔女集会的服务端落点校验（research/04 §2、04b §2–§6），是钓鱼佬出口检查的职业自有副本，外加传送特有的规则。所有检查都
 * 使用目标的站立碰撞箱（蹲下的施放者比站立的队友矮），并使用 {@code null} 实体 / 空形状上下文，因此任何穿门豁免
 * （钓鱼佬、冤魂、提供方）都无法隐藏实体方块。落点必须位于世界高度、Wathe 游戏区域（低于其 {@code minY} 会被 Wathe 判死）
 * 与世界边界之内；处于已加载区块（绝不为找落点加载区块）；没有方块碰撞、流体（Wathe 溺亡）、门格及开门掉落列、裂隙门碰撞箱、
 * 已放置的猎人捕兽夹（含其触发范围）、可碰撞实体或活着的玩家；脚下有真实地面；并且从施放者出发的碰撞射线畅通且不穿过门格
 * （不会穿墙或穿门拉进隔壁车厢，也不会拉上车顶）。控场专家的电击装置无需检查：它只在落地处电击一次随即消失。
 */
final class WitchesSabbathLanding {
    private static final double EPSILON = 1.0E-4;
    /** Body point checked for fluid besides the eyes. / 除眼睛外检查流体的身体高度。 */
    private static final double WAIST_HEIGHT = 0.5;

    private WitchesSabbathLanding() {
    }

    /** One spot per target around the caster, in target order. / 在施放者周围为每个目标分配一个落点，按目标顺序。 */
    static List<WitchesSabbathLandingPlan.Landing<ServerPlayerEntity>> plan(ServerWorld world, ServerPlayerEntity caster,
                                                                        List<ServerPlayerEntity> targets) {
        Vec3d origin = groundedOrigin(world, caster);
        @Nullable Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        return WitchesSabbathLandingPlan.plan(
                targets,
                WitchesSabbathLandingPlan.candidates(origin),
                (target, candidate) -> find(world, origin, playArea, target, candidate),
                WitchesSabbathLanding::standingBoxAt);
    }

    /**
     * The caster's feet, or — while airborne (falling, mid-step) — the floor up to
     * {@link WitchesSabbathRules#AIRBORNE_FLOOR_DEPTH} below them, so the rings and the sight ray start on the ground.
     * 施放者脚底；在空中（下落、跨步途中）时改用其下方 {@link WitchesSabbathRules#AIRBORNE_FLOOR_DEPTH} 内的地面，
     * 使圆环与视线射线都从地面出发。
     */
    private static Vec3d groundedOrigin(ServerWorld world, ServerPlayerEntity caster) {
        Vec3d feet = caster.getPos();
        if (caster.isOnGround()) {
            return feet;
        }
        Box box = caster.getBoundingBox();
        double depth = WitchesSabbathRules.AIRBORNE_FLOOR_DEPTH;
        Box column = new Box(box.minX, feet.y - depth - EPSILON, box.minZ, box.maxX, feet.y, box.maxZ);
        OptionalDouble floor = WitchesSabbathLandingPlan.floorBelow(feet.y, collisionTops(world, column), depth);
        return floor.isPresent() ? new Vec3d(feet.x, floor.getAsDouble(), feet.z) : feet;
    }

    /**
     * Whether the caster's sight rays start inside a door cell (Wathe door part or vanilla door), which makes every
     * candidate fail the door-crossing test; lets the handler say "step out of the doorway" instead of "no room".
     * 施放者的视线射线是否从门格（Wathe 门部件或原版门）内出发——此时所有候选都会因穿门检查失败；处理器据此提示
     * 「离开门口」，而不是「没有空位」。
     */
    static boolean castsFromDoorway(ServerWorld world, ServerPlayerEntity caster) {
        Vec3d from = groundedOrigin(world, caster).add(0.0, WitchesSabbathRules.SIGHT_RAY_HEIGHT, 0.0);
        return isDoor(world.getBlockState(BlockPos.ofFloored(from)).getBlock());
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
        // Collision lookups count strict overlaps only: reach a hair below so a floor exactly one step down is found.
        // 碰撞查询只计严格重叠：向下多探一点，使恰好低一个台阶的地面也能被找到。
        Box column = new Box(footprint.minX, feetY - step - EPSILON, footprint.minZ,
                footprint.maxX, feetY + step, footprint.maxZ);
        return WitchesSabbathLandingPlan.snapFloor(feetY, collisionTops(world, column), step);
    }

    /**
     * Tops of the collision boxes that overlap {@code column} (per box, so a stair's high step outside the footprint
     * does not hide its low step), null entity context.
     * 与 {@code column} 重叠的各碰撞箱顶面（逐箱计算，楼梯在脚印外的高阶不会掩盖其低阶），使用 null 实体上下文。
     */
    private static double[] collisionTops(ServerWorld world, Box column) {
        DoubleStream.Builder tops = DoubleStream.builder();
        for (VoxelShape shape : world.getBlockCollisions(null, column)) {
            for (Box part : shape.getBoundingBoxes()) {
                if (part.intersects(column)) {
                    tops.add(part.maxY);
                }
            }
        }
        return tops.build().toArray();
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
        if (inFluid(world, target, feet) || touchesDoor(world, body)) {
            return false;
        }
        if (!world.getEntitiesByClass(RiftGateEntity.class, body.expand(WitchesSabbathRules.GATE_MARGIN),
                gate -> !gate.isRemoved()).isEmpty()) {
            return false;
        }
        // Never onto a Hunter trap (armed or arming): it would root and injure the teammate on arrival.
        // 绝不落在猎人捕兽夹上（已布设或布设中）：到达时会被定身并受伤。
        if (!world.getEntitiesByClass(HunterTrapEntity.class, body.expand(WitchesSabbathRules.HUNTER_TRAP_MARGIN_XZ,
                WitchesSabbathRules.HUNTER_TRAP_MARGIN_Y, WitchesSabbathRules.HUNTER_TRAP_MARGIN_XZ),
                trap -> !trap.isRemoved()).isEmpty()) {
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
     * Fluid at the waist or the standing eye height (Wathe drowns submerged players); waterlogged floor slabs and
     * decorations elsewhere do not count.
     * 腰部或站立视线高度处有流体（Wathe 会让淹没的玩家溺亡）；含水的地面台阶及其他位置的装饰不计。
     */
    private static boolean inFluid(ServerWorld world, PlayerEntity target, Vec3d feet) {
        double eye = target.getDimensions(EntityPose.STANDING).eyeHeight();
        return !world.getFluidState(BlockPos.ofFloored(feet.x, feet.y + WAIST_HEIGHT, feet.z)).isEmpty()
                || !world.getFluidState(BlockPos.ofFloored(feet.x, feet.y + eye, feet.z)).isEmpty();
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
            if (isDoor(world.getBlockState(pos).getBlock())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A collider ray between both feet raised by {@link WitchesSabbathRules#SIGHT_RAY_HEIGHT}, with an absent shape
     * context (walls and closed doors block it), that also never crosses a door cell: an open door has no collision,
     * and a teammate pulled through it into a keyed cabin is locked in once the door closes itself.
     * 两端脚底抬高 {@link WitchesSabbathRules#SIGHT_RAY_HEIGHT} 之间的碰撞射线，使用空形状上下文（墙与关着的门会挡住），
     * 且从不穿过门格：开着的门没有碰撞，被拉过门进入上锁车厢的队友会在门自动关闭后被锁在里面。
     */
    private static boolean hasClearPath(ServerWorld world, Vec3d origin, Vec3d feet) {
        Vec3d from = origin.add(0.0, WitchesSabbathRules.SIGHT_RAY_HEIGHT, 0.0);
        Vec3d to = feet.add(0.0, WitchesSabbathRules.SIGHT_RAY_HEIGHT, 0.0);
        if (world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent())).getType() != HitResult.Type.MISS) {
            return false;
        }
        Boolean crossesDoor = BlockView.raycast(from, to, world,
                (view, pos) -> isDoor(view.getBlockState(pos).getBlock()) ? Boolean.TRUE : null,
                view -> Boolean.FALSE);
        return !Boolean.TRUE.equals(crossesDoor);
    }

    private static boolean isDoor(Block block) {
        return block instanceof DoorPartBlock || block instanceof DoorBlock;
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
