package dev.caecorthus.sparkwitch.roles.civilian.fisher.item;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/** Consumed one per cast; bought in the Angler's shop. Hidden while held. / 每次抛竿消耗一个；在钓鱼佬商店购买。手持隐藏。 */
public final class FishBaitItem extends Item {
    private static final int TOOLTIP_LINES = 1;

    public FishBaitItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(FisherRules.BAIT_MAX_COUNT);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        FisherItemTooltips.append(tooltip, FisherRules.BAIT_ID, TOOLTIP_LINES);
        super.appendTooltip(stack, context, tooltip, type);
    }
}
