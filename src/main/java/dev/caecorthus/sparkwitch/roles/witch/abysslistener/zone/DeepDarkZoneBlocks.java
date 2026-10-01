package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

/**
 * Read-only server adapter between the world and the pure zone rules: passability for the flood fill, the eligibility
 * facts and the fake look. It never writes a block.
 * 世界与纯领域规则之间的只读服务端适配：泛洪所需的可通行判定、资格事实与假外观。它从不写入任何方块。
 */
final class DeepDarkZoneBlocks {
    /**
     * Datapack block tag {@code sparkwitch:sculk_conversion_immune}: blocks a map maker never wants converted
     * (seeded with {@code minecraft:magma_block}). Stable resource contract.
     * 数据包方块标签 {@code sparkwitch:sculk_conversion_immune}：地图作者不希望被转换的方块（初始为岩浆块）。稳定资源契约。
     */
    static final TagKey<Block> CONVERSION_IMMUNE = TagKey.of(RegistryKeys.BLOCK,
            SparkWitch.id("sculk_conversion_immune"));
    /** Already deep dark: never converted again and never counted as zone blocks. / 已是深暗外观：不再转换。 */
    private static final Set<Block> DEEP_DARK_LOOKS = Set.of(
            Blocks.SCULK,
            Blocks.DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.CRACKED_DEEPSLATE_TILES,
            Blocks.CHISELED_DEEPSLATE,
            Blocks.REINFORCED_DEEPSLATE
    );

    private DeepDarkZoneBlocks() {
    }

    static boolean isLoaded(ServerWorld world, BlockPos pos) {
        return world.isInBuildLimit(pos) && world.isChunkLoaded(
                ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()));
    }

    /**
     * No collision shape for an entity-less context, so an open Wathe door lets the zone through and a closed one
     * stops it; door-passing exemptions keyed on an entity never apply. Unloaded cells block.
     * 在无实体的形状上下文中没有碰撞箱：打开的 Wathe 门放行、关着的门阻挡；按实体生效的穿门豁免永不适用。未加载的格子阻挡。
     */
    static boolean passable(ServerWorld world, BlockPos pos) {
        if (!isLoaded(world, pos)) {
            return false;
        }
        return world.getBlockState(pos).getCollisionShape(world, pos, ShapeContext.absent()).isEmpty();
    }

    static boolean convertible(ServerWorld world, BlockPos pos, Box playArea, Box resetTemplateArea) {
        if (!isLoaded(world, pos)) {
            return false;
        }
        return DeepDarkZoneEligibility.isConvertible(
                facts(world, pos, world.getBlockState(pos), playArea, resetTemplateArea));
    }

    static DeepDarkZoneEligibility.Facts facts(ServerWorld world, BlockPos pos, BlockState state, Box playArea,
                                               Box resetTemplateArea) {
        Vec3d center = Vec3d.ofCenter(pos);
        Block block = state.getBlock();
        return new DeepDarkZoneEligibility.Facts(
                playArea.contains(center),
                resetTemplateArea.contains(center),
                state.isAir(),
                !state.getFluidState().isEmpty(),
                state.hasBlockEntity(),
                state.getRenderType() == BlockRenderType.MODEL,
                state.isOpaqueFullCube(world, pos),
                state.isFullCube(world, pos),
                state.getLuminance(),
                state.emitsRedstonePower(),
                state.getProperties().size(),
                state.contains(Properties.AXIS),
                block instanceof FallingBlock,
                block.getSlipperiness(),
                block.getVelocityMultiplier(),
                block.getJumpVelocityMultiplier(),
                Registries.BLOCK.getId(block).getNamespace(),
                state.isIn(CONVERSION_IMMUNE),
                DEEP_DARK_LOOKS.contains(block)
        );
    }

    /** Deterministic fake look; a block with open space above is a floor. / 确定性的假外观；上方空旷的方块视为地板。 */
    static BlockState fakeState(ServerWorld world, BlockPos pos) {
        return switch (DeepDarkZoneEligibility.look(pos, passable(world, pos.up()))) {
            case SCULK -> Blocks.SCULK.getDefaultState();
            case DEEPSLATE_TILES -> Blocks.DEEPSLATE_TILES.getDefaultState();
            case DEEPSLATE_BRICKS -> Blocks.DEEPSLATE_BRICKS.getDefaultState();
            case COBBLED_DEEPSLATE -> Blocks.COBBLED_DEEPSLATE.getDefaultState();
        };
    }
}
