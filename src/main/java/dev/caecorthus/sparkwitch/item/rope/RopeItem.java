package dev.caecorthus.sparkwitch.item.rope;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Rope ({@code sparkwitch:rope}): 二阶堂希罗's strangling tool, sold in her shop for {@link RopeRules#PRICE} coins
 * and usable by anyone who holds it. The item is only the request: vanilla's use-item packet (which carries the aim)
 * reaches {@link RopeStrangleService#tryStart} on the server, which owns the target pick, the hold, the interrupt, the
 * kill, the cooldown and the consumption. The client predicts nothing.
 * 绳索（{@code sparkwitch:rope}）：二阶堂希罗的勒杀道具，在她的商店以 {@link RopeRules#PRICE} 金币出售，任何持有者都可使用。物品本身
 * 只是请求：原版使用物品数据包（已携带瞄准方向）到达服务端的 {@link RopeStrangleService#tryStart}，由其负责选取目标、定身、打断、击杀、
 * 冷却与消耗。客户端不做任何预测。
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
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            // Consume, not pass: the other hand's item must not fire from the same click. / 返回 consume 而非 pass：同一次点击不得再触发另一只手的物品。
            return TypedActionResult.consume(stack);
        }
        // Success makes vanilla swing the arm for everyone when the grab starts. / success 会让原版在出手时向所有人播放挥手动作。
        return user instanceof ServerPlayerEntity player && RopeStrangleService.tryStart(player, hand)
                ? TypedActionResult.success(stack)
                : TypedActionResult.fail(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.rope.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
