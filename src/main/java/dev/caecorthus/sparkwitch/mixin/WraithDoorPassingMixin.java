package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.block.DoorPartBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
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
 * Makes Wathe train doors non-solid only for active Wraiths.
 * 仅对激活的冤魂移除 Wathe 列车门碰撞。
 */
@Mixin(DoorPartBlock.class)
public abstract class WraithDoorPassingMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$wraithPassesTrainDoors(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (context instanceof EntityShapeContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof PlayerEntity player && WraithStateService.isActive(player)) {
                cir.setReturnValue(VoxelShapes.empty());
            }
        }
    }
}
