package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.caecorthus.sparkwitch.util.RaycastShapeScope;
import dev.doctor4t.wathe.block.DoorPartBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Both sides: duelists walk through closed door-family blocks, the same collision-shape exemption used for Wraiths and
 * Glimmerfish. Only movement changes; the door's own state, interaction and rendering are untouched, and
 * {@link RaycastShapeScope} keeps sight, aiming and weapon rays stopped by the door.
 * 双端：决斗者穿过关闭的门类方块，与冤魂、幽光鱼相同的碰撞形状豁免。只改移动；门的开关状态、交互与渲染不变，
 * {@link RaycastShapeScope} 内的视线、瞄准与武器射线仍被门挡住。
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class KnightDuelDoorPassingMixin {
    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$duelistsPassDoors(BlockView world, BlockPos pos, ShapeContext context,
                                              CallbackInfoReturnable<VoxelShape> cir) {
        // Hot path (every collision-shape query on both sides): cheapest side-effect-free tests first.
        // 热路径（双端每次碰撞形状查询）：无副作用的判断按开销从低到高排列。
        if (isDoorFamily((BlockState) (Object) this)
                && context instanceof EntityShapeContext entityContext
                && entityContext.getEntity() instanceof PlayerEntity player
                && isDuelist(player)
                && !RaycastShapeScope.isRaycast()) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }

    private static boolean isDoorFamily(BlockState state) {
        return state.getBlock() instanceof DoorPartBlock
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapdoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    private static boolean isDuelist(PlayerEntity player) {
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.getNullable(player.getWorld());
        return duel != null && duel.isDuelist(player.getUuid());
    }
}
