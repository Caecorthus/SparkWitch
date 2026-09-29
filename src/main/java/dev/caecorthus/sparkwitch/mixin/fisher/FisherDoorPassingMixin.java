package dev.caecorthus.sparkwitch.mixin.fisher;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherDoorPassingRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherRaycastScope;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritComponent;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
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
 * The same synced capability gates client movement prediction and server collision validation. Absent entity
 * contexts retain ordinary shapes for safe-exit probes. RaycastContext queries explicitly bypass this exemption.
 * 同一同步能力控制客户端移动预测与服务端碰撞校验。无实体上下文保留安全退出探测形状；射线查询显式跳过穿门豁免。
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class FisherDoorPassingMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$fisherPassesDoors(BlockView world, BlockPos pos, ShapeContext context,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        if (!FisherRaycastScope.isRaycast() && context instanceof EntityShapeContext entityContext
                && entityContext.getEntity() instanceof PlayerEntity player
                && FisherDoorPassingRules.isDoor((BlockState) (Object) this)
                && FisherSpiritComponent.KEY.get(player).isActive()) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
