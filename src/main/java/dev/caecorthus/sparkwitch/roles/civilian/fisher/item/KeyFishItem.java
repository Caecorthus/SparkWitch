package dev.caecorthus.sparkwitch.roles.civilian.fisher.item;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * One-shot lockpick shaped like a fish. Door handling lives in {@code FisherKeyFishDoors} on Wathe's
 * {@code DoorInteraction.EVENT}; visible in hand on purpose.
 * 鱼形的一次性开锁工具。开门逻辑在 {@code FisherKeyFishDoors}（Wathe {@code DoorInteraction.EVENT}）；刻意手持可见。
 */
public final class KeyFishItem extends Item {
    private static final int TOOLTIP_LINES = 2;

    public KeyFishItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(FisherRules.FISH_MAX_COUNT);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        FisherItemTooltips.append(tooltip, FisherRules.KEY_FISH_ID, TOOLTIP_LINES);
        super.appendTooltip(stack, context, tooltip, type);
    }
}
