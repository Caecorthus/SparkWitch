package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.tooltip.TooltipType;

import java.util.List;

/**
 * 侦探徽章 — handed to another player; its wearer glows orange for the detective and reports its killer on death.
 * 侦探徽章——赠予他人；佩戴者会为侦探透出橘黄色，死亡时把凶手报给侦探。
 */
public final class SherryBadgeItem extends Item {

    public SherryBadgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.sherry_badge.tooltip").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.sherry_badge.tooltip.hand").formatted(Formatting.DARK_GRAY));
    }
}
