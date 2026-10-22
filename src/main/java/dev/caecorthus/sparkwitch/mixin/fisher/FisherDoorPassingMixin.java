package dev.caecorthus.sparkwitch.mixin.fisher;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherDoorPassingRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritComponent;
import dev.caecorthus.sparkwitch.util.RaycastShapeScope;
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
 * contexts retain ordinary shapes for safe-exit probes. Queries inside {@code RaycastShapeScope} skip this exemption.
 * 同一同步能力控制客户端移动预测与服务端碰撞校验。无实体上下文保留安全退出探测形状；RaycastShapeScope 内的射线查询跳过穿门豁免。
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class FisherDoorPassingMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$fisherPassesDoors(BlockView world, BlockPos pos, ShapeContext context,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        // Cheapest side-effect-free test first (hot on both sides); the ray scope is read last, so the result is
        // unchanged. FisherSpiritComponent is attached to every player, so its lookup never throws.
        // 无副作用的判断按开销从低到高排列（双端热路径），射线作用域最后读取，结果不变。每名玩家都挂有
        // FisherSpiritComponent，查找从不抛出异常。
        if (FisherDoorPassingRules.isDoor((BlockState) (Object) this)
                && context instanceof EntityShapeContext entityContext
                && entityContext.getEntity() instanceof PlayerEntity player
                && FisherSpiritComponent.KEY.get(player).isActive()
                && !RaycastShapeScope.isRaycast()) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
