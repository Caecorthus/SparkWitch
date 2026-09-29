package dev.caecorthus.sparkwitch.mixin.fisher;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherRaycastScope;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;

/** COLLIDER rays reuse entity collision contexts; keep Glimmerfish's movement exemption out of ray queries.
 * COLLIDER 射线复用实体碰撞上下文；射线查询不得使用灵光鱼的移动穿门豁免。 */
@Mixin(RaycastContext.class)
public abstract class FisherRaycastContextMixin {
    @WrapMethod(method = "getBlockShape")
    private VoxelShape sparkwitch$fisherRaycastShape(BlockState state, BlockView world, BlockPos pos,
                                                    Operation<VoxelShape> original) {
        return FisherRaycastScope.query(() -> original.call(state, world, pos));
    }
}
