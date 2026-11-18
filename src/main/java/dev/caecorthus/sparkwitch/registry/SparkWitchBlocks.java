package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.block.SparkBedBlock;
import dev.caecorthus.sparkwitch.block.service.SparkBedService;
import dev.doctor4t.wathe.index.WatheBlockEntities;
import dev.doctor4t.wathe.index.WatheBlocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Blocks added by SparkWitch: two map-maker beds (PR #239). They copy Wathe's trimmed bed settings and are added to
 * Wathe's own {@code wathe:trimmed_bed} block entity type through Fabric's {@code addSupportedBlock}, so the block
 * entity they create is Wathe's {@code TrimmedBedBlockEntity} with Wathe's type: scorpion poison, its client
 * particles and every add-on hook that checks that type or class see them as ordinary trimmed beds. Registered from
 * {@code SparkWitchItems.register()}, after every existing item, so earlier raw ids keep their order.
 * <p>
 * SparkWitch 新增的方块：两张供地图作者使用的床（PR #239）。沿用 Wathe 裁剪床的方块设置，并通过 Fabric 的
 * {@code addSupportedBlock} 加入 Wathe 自己的 {@code wathe:trimmed_bed} 方块实体类型，因此它们创建的方块实体就是
 * 带 Wathe 类型的 {@code TrimmedBedBlockEntity}：蝎子毒、其客户端粒子以及所有按该类型或类判断的附属钩子都把它们视为
 * 普通裁剪床。由 {@code SparkWitchItems.register()} 在所有既有物品之后注册，保持既有注册顺序。
 */
public final class SparkWitchBlocks {
    public static final Identifier GREY_DIRTY_BED_ID = SparkWitch.id("grey_dirty_bed");
    public static final Identifier GREEN_STRANGE_BED_ID = SparkWitch.id("green_strange_bed");

    private static Block greyDirtyBed;
    private static Block greenStrangeBed;

    private SparkWitchBlocks() {
    }

    public static synchronized void register() {
        if (greyDirtyBed != null) {
            return;
        }
        greyDirtyBed = registerBed(GREY_DIRTY_BED_ID, SparkBedBlock.Variant.GREY_DIRTY);
        greenStrangeBed = registerBed(GREEN_STRANGE_BED_ID, SparkBedBlock.Variant.GREEN_STRANGE);
        // The beds own their one listener, so SparkWitchEvents' pinned registration order stays untouched.
        // 床自带唯一的监听器，因此 SparkWitchEvents 中被固定的注册顺序保持不变。
        SparkBedService.register();
    }

    private static Block registerBed(Identifier id, SparkBedBlock.Variant variant) {
        Block bed = Registry.register(
                Registries.BLOCK,
                id,
                new SparkBedBlock(variant, AbstractBlock.Settings.copy(WatheBlocks.WHITE_TRIMMED_BED))
        );
        WatheBlockEntities.TRIMMED_BED.addSupportedBlock(bed);
        Registry.register(Registries.ITEM, id, new BlockItem(bed, new Item.Settings()));
        return bed;
    }

    public static Block greyDirtyBed() {
        return greyDirtyBed;
    }

    public static Block greenStrangeBed() {
        return greenStrangeBed;
    }
}
