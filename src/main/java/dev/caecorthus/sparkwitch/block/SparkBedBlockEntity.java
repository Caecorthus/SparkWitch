package dev.caecorthus.sparkwitch.block;

import dev.caecorthus.sparkwitch.registry.SparkWitchBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

/**
 * Plain block entity for the two custom beds. The rendered model comes from the blockstate, so nothing is stored here
 * yet; it exists because {@link SparkBedBlock} follows Wathe's layout and keeps the same "one entity, two blocks" shape.
 * <p>
 * 两张自定义床的空方块实体。渲染模型由 blockstate 提供，目前这里不存任何数据；它存在是因为
 * {@link SparkBedBlock} 沿用 Wathe 的布局，保持同样的「一个实体、两格方块」形状。
 */
public class SparkBedBlockEntity extends BlockEntity {
    public SparkBedBlockEntity(BlockPos pos, BlockState state) {
        super(SparkWitchBlocks.SPARK_BED_ENTITY, pos, state);
    }

    public static SparkBedBlockEntity create(BlockPos pos, BlockState state) {
        return new SparkBedBlockEntity(pos, state);
    }
}
