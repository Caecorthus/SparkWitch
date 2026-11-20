package dev.caecorthus.sparkwitch.block.props;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Wallpaper for the train's interiors: one plain sheet with a dark skirting board, one sheet without it, and eight
 * painted arrows that point the way through the carriages.
 * <p>
 * 车厢内饰用的墙纸：一张带深色踢脚线，一张不带，另有八个涂鸦箭头用于指引车厢方向。
 */
public final class SparkWitchWallpaperBlocks {
    public static final Identifier YELLOW_WALLPAPER_ID = SparkWitch.id("yellow_wallpaper");
    public static final Identifier YELLOW_WALLPAPER_PLAIN_ID = SparkWitch.id("yellow_wallpaper_plain");

    /** Painted arrow directions, in registration order. / 涂鸦箭头方向，按注册顺序。 */
    public static final String[] ARROW_DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private static Block yellowWallpaper;
    private static Block yellowWallpaperPlain;
    private static boolean registered;

    private SparkWitchWallpaperBlocks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        yellowWallpaper = block(YELLOW_WALLPAPER_ID, new Block(AbstractBlock.Settings.copy(Blocks.WHITE_WOOL)));
        yellowWallpaperPlain = block(YELLOW_WALLPAPER_PLAIN_ID, new Block(AbstractBlock.Settings.copy(Blocks.WHITE_WOOL)));
        for (String direction : ARROW_DIRECTIONS) {
            block(SparkWitch.id("yellowed_wallpaper_" + direction),
                    new Block(AbstractBlock.Settings.copy(Blocks.WHITE_WOOL)));
        }
    }

    private static Block block(Identifier id, Block block) {
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static Block yellowWallpaper() {
        return yellowWallpaper;
    }

    public static Block yellowWallpaperPlain() {
        return yellowWallpaperPlain;
    }
}
