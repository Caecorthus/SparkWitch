package dev.caecorthus.sparkwitch.roles.killer.timestealer;

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
 * Bound Time Stealer Clock. Every gate, the target ray and the theft are server-authoritative in
 * {@link TimeStealerClockService}; vanilla's use-item packet (which carries the aim) is the whole request, so no custom
 * packet exists. The client returns CONSUME and never SUCCESS, so no arm swing reveals the hidden Clock.
 * 窃时者的绑定时钟。所有判定、目标射线与窃取都由服务端 {@link TimeStealerClockService} 负责；原版使用物品数据包
 * （已携带瞄准方向）就是全部请求，因此不存在自定义数据包。客户端返回 CONSUME 而绝不返回 SUCCESS，
 * 因此不会有挥手动作暴露隐藏的时钟。
 */
public final class TimeStealerClockItem extends Item {
    private static final int TOOLTIP_LINES = 3;

    public TimeStealerClockItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.consume(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverUser)) {
            return TypedActionResult.fail(stack);
        }
        return TimeStealerClockService.use(serverUser, stack, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.time_stealer_pocket_watch.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
