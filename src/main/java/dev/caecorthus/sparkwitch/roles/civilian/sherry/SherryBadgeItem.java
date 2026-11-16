package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 侦探徽章: a plain carried token; using it on another player is handled server-side by {@link SherryBadgeService}
 * (the item itself has no use action, so anyone else holding it can do nothing with it).
 * 侦探徽章：普通携带物；对其他玩家使用由服务端的 {@link SherryBadgeService} 处理（物品本身没有使用动作，
 * 因此其他人拿到它也无法使用）。
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
