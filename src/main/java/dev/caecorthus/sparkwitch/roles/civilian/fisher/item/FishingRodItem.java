package dev.caecorthus.sparkwitch.roles.civilian.fisher.item;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Angler's rod. It has no item behaviour of its own: {@code FisherFishingService} intercepts a main-hand rod on a
 * Wathe drink tray through Fabric's {@code UseBlockCallback}, because the tray's {@code onUse} answers SUCCESS on the
 * client before any item hook could run. Hidden from other living players through NoellesRoles' equipment filter.
 * 钓鱼佬的鱼竿。本身没有物品行为：{@code FisherFishingService} 通过 Fabric 的 {@code UseBlockCallback} 接管主手鱼竿
 * 对 Wathe 饮料托盘的使用，因为托盘的 {@code onUse} 在客户端会先返回 SUCCESS，物品钩子根本到不了。
 * 经由 NoellesRoles 装备过滤对其他存活玩家隐藏。
 */
public final class FishingRodItem extends Item {
    private static final int TOOLTIP_LINES = 2;

    public FishingRodItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        FisherItemTooltips.append(tooltip, FisherRules.FISHING_ROD_ID, TOOLTIP_LINES);
        super.appendTooltip(stack, context, tooltip, type);
    }
}
