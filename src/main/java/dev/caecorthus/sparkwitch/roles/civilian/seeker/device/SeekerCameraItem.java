package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.doctor4t.wathe.util.AdventureUsable;
import net.minecraft.item.Item;

/**
 * The Security Camera item; usable in adventure mode and consumed on placement.
 * TODO(WP-03): implement placement and tooltip. / 待 WP-03 实现放置与提示。
 * 摄像头物品；冒险模式下可用，放置后消耗。
 */
public class SeekerCameraItem extends Item implements AdventureUsable {
    /** Tooltip lines {@code item.sparkwitch.seeker_camera.tooltip.line1..N}. / 物品提示行数。 */
    public static final int TOOLTIP_LINES = 3;

    public SeekerCameraItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }
}
