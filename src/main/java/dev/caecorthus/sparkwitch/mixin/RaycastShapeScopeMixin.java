package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.util.RaycastShapeScope;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The single SparkWitch wrapper around ray block-shape queries, on both logical sides. It keeps the ray's own entity
 * shape context, so other mods' entity-sensitive ray geometry is unchanged; door-passing mixins read the scope instead.
 * SparkWitch 唯一的射线方块形状查询包装，双端生效。保留射线自身的实体形状上下文，其他模组按实体区分的射线几何不变；
 * 穿门 mixin 改为读取该作用域。
 */
@Mixin(RaycastContext.class)
public abstract class RaycastShapeScopeMixin {
    @WrapMethod(method = "getBlockShape")
    private VoxelShape sparkwitch$scopeRayBlockShape(BlockState state, BlockView world, BlockPos pos,
                                                     Operation<VoxelShape> original) {
        return RaycastShapeScope.query(() -> original.call(state, world, pos));
    }
}
