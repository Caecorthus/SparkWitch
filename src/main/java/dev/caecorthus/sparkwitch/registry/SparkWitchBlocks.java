package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.block.SparkBedBlock;
import dev.caecorthus.sparkwitch.block.SparkBedBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Blocks added by this mod. There was no block registry before the two beds, so this class also owns the block entity
 * type: Fabric requires the type itself to be registered and to list every block it can back.
 * <p>
 * 本模组新增的方块。这两张床之前没有方块注册表，所以方块实体类型也放在这里：Fabric 要求类型自身注册，并列出它能承载的每个方块。
 */
public final class SparkWitchBlocks {
    public static final Identifier GREY_DIRTY_BED_ID = SparkWitch.id("grey_dirty_bed");
    public static final Identifier GREEN_STRANGE_BED_ID = SparkWitch.id("green_strange_bed");
    private static final Identifier SPARK_BED_ENTITY_ID = SparkWitch.id("spark_bed");

    private static Block greyDirtyBed;
    private static Block greenStrangeBed;

    /** Both beds share one block entity type, exactly like Wathe's two trimmed beds do. / 两张床共用一个方块实体类型，与 Wathe 的两张裁剪床一致。 */
    public static BlockEntityType<SparkBedBlockEntity> SPARK_BED_ENTITY;

    private SparkWitchBlocks() {
    }

    public static synchronized void register() {
        if (greyDirtyBed != null) {
            return;
        }
        AbstractBlock.Settings settings = AbstractBlock.Settings.create()
                .strength(0.2F)
                .sounds(BlockSoundGroup.WOOD)
                .nonOpaque();
        greyDirtyBed = Registry.register(
                Registries.BLOCK,
                GREY_DIRTY_BED_ID,
                new SparkBedBlock(SparkBedBlock.Variant.GREY_DIRTY, settings)
        );
        greenStrangeBed = Registry.register(
                Registries.BLOCK,
                GREEN_STRANGE_BED_ID,
                new SparkBedBlock(SparkBedBlock.Variant.GREEN_STRANGE, settings)
        );
        SPARK_BED_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                SPARK_BED_ENTITY_ID,
                BlockEntityType.Builder.create(SparkBedBlockEntity::create, greyDirtyBed, greenStrangeBed).build()
        );
        Registry.register(Registries.ITEM, GREY_DIRTY_BED_ID, new BlockItem(greyDirtyBed, new Item.Settings()));
        Registry.register(Registries.ITEM, GREEN_STRANGE_BED_ID, new BlockItem(greenStrangeBed, new Item.Settings()));
    }

    public static Block greyDirtyBed() {
        return greyDirtyBed;
    }

    public static Block greenStrangeBed() {
        return greenStrangeBed;
    }
}
