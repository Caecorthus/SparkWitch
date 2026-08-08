package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.block.DoorPartBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
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
 * Makes Wathe and vanilla door-family blocks non-solid only for active Wraiths.
 * 仅对激活的冤魂移除门类方块碰撞。
 *
 * <p>这里故意只改碰撞形状，不改门的开关状态、交互、射线检测或渲染：
 * 冤魂可以直接穿过已经关闭的 Wathe 门、原版门、活板门和栅栏门，
 * 但其他玩家看到/使用这些门的行为仍保持原样。</p>
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class WraithDoorPassingMixin {
    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$wraithPassesDoorFamilyBlocks(
            BlockView world,
            BlockPos pos,
            ShapeContext context,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (context instanceof EntityShapeContext entityContext) {
            Entity entity = entityContext.getEntity();
            BlockState state = (BlockState) (Object) this;
            if (entity instanceof PlayerEntity player
                    && WraithStateService.isActive(player)
                    && isDoorFamilyBlock(state)) {
                // 只对正在查询碰撞的冤魂本人返回空形状；非冤魂仍使用目标方块原本的碰撞体积。
                cir.setReturnValue(VoxelShapes.empty());
            }
        }
    }

    private static boolean isDoorFamilyBlock(BlockState state) {
        return state.getBlock() instanceof DoorPartBlock
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapdoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }
}
