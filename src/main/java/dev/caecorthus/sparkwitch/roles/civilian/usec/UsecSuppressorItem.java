package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The rifle suppressor (shop stock 1). Attached through the attachment screen, it makes the shot quieter and sharper
 * with no other drawback (Q13).
 * 步枪消音器（商店限购 1）。通过配件界面安装后，枪声更小、更尖，没有其他副作用（Q13）。
 */
public class UsecSuppressorItem extends Item {
    static final String TOOLTIP_KEY = "item.sparkwitch.usec_suppressor.tooltip";

    public UsecSuppressorItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(TOOLTIP_KEY).formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
