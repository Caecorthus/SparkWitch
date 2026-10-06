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
 * Bound Gift Watch of a Conscience Time Stealer (owner decision 2026-10-05): the Clock in reverse. A separate class
 * from {@link TimeStealerClockItem}, so every "exactly one Clock" rule keeps meaning the curse watch. Like the Clock,
 * the server decides everything in {@link TimeGiftWatchService} from vanilla's use-item packet, and the client returns
 * CONSUME, never SUCCESS, so no arm swing reveals it.
 * 善良窃时者的绑定赠时怀表（所有者决定 2026-10-05）：反向的时钟。与 {@link TimeStealerClockItem} 是不同的类，
 * 因此所有“恰好一个时钟”的规则仍只指诅咒怀表。与时钟一样，服务端依据原版使用物品数据包在 {@link TimeGiftWatchService}
 * 中裁定一切，客户端返回 CONSUME 而绝不返回 SUCCESS，因此不会有挥手动作暴露它。
 */
public final class TimeGiftWatchItem extends Item {
    private static final int TOOLTIP_LINES = 3;

    public TimeGiftWatchItem(Settings settings) {
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
        return TimeGiftWatchService.use(serverUser, stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.time_stealer_gift_watch.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
