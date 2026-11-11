package dev.caecorthus.sparkwitch.item.rope;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Rope ({@code sparkwitch:rope}): 二阶堂希罗's strangling tool, sold in her shop for {@link RopeRules#PRICE} coins.
 * SCAFFOLD: the item only exists (stack of 1, tooltip); using it does nothing yet. The strangle (target pick, channel,
 * interrupt, kill, cooldown, consumption) is server-authoritative and belongs to {@link RopeStrangleService}.
 * 绳索（{@code sparkwitch:rope}）：二阶堂希罗的勒杀道具，在她的商店以 {@link RopeRules#PRICE} 金币出售。骨架阶段：仅有物品本身
 * （不可堆叠、提示文本），使用暂无效果。勒杀（选取目标、引导、打断、击杀、冷却与消耗）由服务端权威判定，属于
 * {@link RopeStrangleService}。
 */
public final class RopeItem extends Item {
    private static final int TOOLTIP_LINES = 3;

    public RopeItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // TODO(绳索): start the strangle through RopeStrangleService. / TODO（绳索）：经 RopeStrangleService 开始勒杀。
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.rope.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
