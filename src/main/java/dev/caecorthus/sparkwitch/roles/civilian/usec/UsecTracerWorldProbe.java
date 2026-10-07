package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.doctor4t.wathe.index.WatheBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server block probe for {@link UsecTracer}. Every block query goes through a COLLIDER, fluid-less
 * {@link RaycastContext} cast by the shooter, so {@code util/RaycastShapeScope} keeps closed doors solid for the bullet
 * (door-passing exemptions stay movement-only) and other mods' entity-sensitive ray shapes are respected. It never
 * loads a chunk: {@link #isLoaded} asks the chunk manager first and the tracer stops at the first unloaded segment.
 * {@link UsecTracer} 的服务端方块探针。所有方块查询都经由射手发起的 COLLIDER、忽略流体的 {@link RaycastContext}，因此
 * {@code util/RaycastShapeScope} 让关闭的门对子弹保持实心（穿门豁免只作用于移动），其他模组按实体区分的射线形状也得到尊重。
 * 它从不加载区块：{@link #isLoaded} 先询问区块管理器，追踪器在第一个未加载线段处停止。
 */
final class UsecTracerWorldProbe implements UsecTracer.BlockProbe {
    private final ServerWorld world;
    private final Entity shooter;

    UsecTracerWorldProbe(ServerWorld world, Entity shooter) {
        this.world = world;
        this.shooter = shooter;
    }

    @Override
    public boolean isLoaded(Vec3d from, Vec3d to) {
        int minX = MathHelper.floor(Math.min(from.x, to.x)) >> 4;
        int maxX = MathHelper.floor(Math.max(from.x, to.x)) >> 4;
        int minZ = MathHelper.floor(Math.min(from.z, to.z)) >> 4;
        int maxZ = MathHelper.floor(Math.max(from.z, to.z)) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.getChunkManager().isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public @Nullable UsecTracer.BlockHit raycast(Vec3d from, Vec3d to) {
        BlockHitResult hit = world.raycast(context(from, to));
        return hit.getType() == HitResult.Type.BLOCK ? new UsecTracer.BlockHit(hit.getBlockPos(), hit.getPos()) : null;
    }

    @Override
    public boolean isImpenetrable(BlockPos pos) {
        return isMapWall(world.getBlockState(pos));
    }

    /**
     * Casts back from where the line leaves the cell towards the entry: the first shape face met is the last exit, so
     * any gaps inside a multi-part shape (stairs, panes) still count as one block.
     * 从直线离开方块格之处向入口反向投射：遇到的第一个形状面就是最后的出口，因此多部件形状（楼梯、玻璃板）内部的空隙也只算一个方块。
     */
    @Override
    public Vec3d exitPoint(BlockPos pos, Vec3d entry, Vec3d direction) {
        Vec3d far = UsecTracer.cellExit(pos, entry, direction);
        if (far.squaredDistanceTo(entry) < 1.0E-12) {
            return entry;
        }
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = context(far, entry).getBlockShape(state, world, pos);
        BlockHitResult back = shape.raycast(far, entry, pos);
        return back == null ? entry : back.getPos();
    }

    /**
     * Map walls stop every round (the vanilla barrier and Wathe's barrier panel). Wathe's light barrier has no collision,
     * so rays pass it without ever reporting it.
     * 地图墙挡住所有子弹（原版屏障与 Wathe 屏障板）。Wathe 光照屏障没有碰撞，射线直接穿过，从不报告它。
     */
    static boolean isMapWall(BlockState state) {
        return state.isOf(Blocks.BARRIER) || state.isOf(WatheBlocks.BARRIER_PANEL);
    }

    private RaycastContext context(Vec3d from, Vec3d to) {
        return new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                shooter);
    }
}
