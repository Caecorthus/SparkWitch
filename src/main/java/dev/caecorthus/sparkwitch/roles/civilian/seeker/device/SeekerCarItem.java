package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import net.minecraft.item.Item;

/**
 * The Search Car item: the "garage". It stays in its slot while deployed and carries every car cooldown.
 * TODO(WP-03): implement deploy / console fallback use and tooltip. / 待 WP-03 实现部署、控制台兜底使用与提示。
 * 搜寻小车物品即“车库”：部署后仍留在槽位中，并承载所有小车冷却。
 */
public class SeekerCarItem extends Item {
    /** Tooltip lines {@code item.sparkwitch.seeker_car.tooltip.line1..N}. / 物品提示行数。 */
    public static final int TOOLTIP_LINES = 4;

    public SeekerCarItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }
}
