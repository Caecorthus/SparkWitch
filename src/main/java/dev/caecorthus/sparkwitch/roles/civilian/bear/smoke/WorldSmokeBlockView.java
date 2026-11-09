package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import dev.doctor4t.wathe.block.BarrierPanelBlock;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Read-only server adapter from a world to {@link SmokeBlockView}. Collision comes from
 * {@code getCollisionShape(world, pos, ShapeContext.absent())}: an entity-less context, so the Wraith, Fisher and
 * Knight door-passing exemptions (keyed on a player entity) never apply, an open Wathe door (empty collision) lets
 * smoke through and a closed one (its 4/16-thick slab between cell centres) stops it. Wathe doors keep their closed
 * shape for rays ({@code getRaycastShape}); smoke deliberately follows collision instead, so it pours through open
 * doors. Fluids, unloaded chunks and the build limit block smoke; nothing here loads a chunk or writes a block.
 * Invisible barriers (vanilla {@code minecraft:barrier}, Wathe's light barrier and barrier panels) block smoke by
 * their collision like any other block (owner Q5, 2026-11-06: "挡"; {@link #BARRIERS_PASS_SMOKE} is false).
 * 世界到 {@link SmokeBlockView} 的只读服务端适配。碰撞取自 {@code getCollisionShape(world, pos, ShapeContext.absent())}：
 * 无实体上下文，因此冤魂、钓鱼佬与骑士按玩家实体生效的穿门豁免永不适用；打开的 Wathe 门（碰撞为空）让烟通过，关着的门
 * （夹在格中心之间的 4/16 厚板）挡住烟。Wathe 门对射线始终保持关闭形状（{@code getRaycastShape}）；烟刻意按碰撞走，
 * 所以会从开着的门涌出。流体、未加载区块与建筑高度之外都会挡住烟；这里从不加载区块，也从不写方块。不可见屏障（原版
 * {@code minecraft:barrier}、Wathe 的光屏障与屏障面板）与其他方块一样按碰撞挡烟（所有者 Q5，2026-11-06：“挡”；
 * {@link #BARRIERS_PASS_SMOKE} 为 false）。
 */
final class WorldSmokeBlockView implements SmokeBlockView {
    /**
     * Owner Q5 (2026-11-06, "挡"): invisible barriers block smoke by their collision. True would treat them as air
     * (the spike's default). / 所有者 Q5（2026-11-06，“挡”）：不可见屏障按碰撞挡烟。设为 true 则视为空气（原型的默认）。
     */
    static final boolean BARRIERS_PASS_SMOKE = false;

    private final ServerWorld world;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    WorldSmokeBlockView(ServerWorld world) {
        this.world = world;
    }

    @Override
    @Nullable
    public List<Box> collisionBoxes(int x, int y, int z) {
        pos.set(x, y, z);
        if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(
                ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return null;
        }
        if (BARRIERS_PASS_SMOKE && isInvisibleBarrier(state)) {
            return List.of();
        }
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.absent());
        return shape.isEmpty() ? List.of() : shape.getBoundingBoxes();
    }

    @Override
    public boolean seesSky(int x, int y, int z) {
        pos.set(x, y, z);
        return world.isSkyVisible(pos);
    }

    static boolean isInvisibleBarrier(BlockState state) {
        return state.getBlock() instanceof BarrierBlock || state.getBlock() instanceof BarrierPanelBlock;
    }
}
