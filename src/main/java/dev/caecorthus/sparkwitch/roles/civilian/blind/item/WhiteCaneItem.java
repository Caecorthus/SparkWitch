package dev.caecorthus.sparkwitch.roles.civilian.blind.item;

import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindCaneService;
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
 * The Blind's bound White Cane ({@code sparkwitch:white_cane}). Every gate, the tap sound and the CANE pulse are
 * server-authoritative in {@link BlindCaneService}; vanilla's use-item packet is the whole request, so no custom packet
 * exists. The client returns CONSUME and never SUCCESS, so no arm swing reveals the cane, which is hidden in hand from
 * other living players (D9).
 * 盲人的绑定盲杖（{@code sparkwitch:white_cane}）。所有判定、敲击声与 CANE 脉冲都由服务端 {@link BlindCaneService}
 * 负责；原版使用物品数据包就是全部请求，因此不存在自定义数据包。客户端返回 CONSUME 而绝不返回 SUCCESS，因此不会有
 * 挥手动作暴露盲杖；盲杖在手中对其他存活玩家隐藏（D9）。
 */
public final class WhiteCaneItem extends Item {
    private static final int TOOLTIP_LINES = 2;

    public WhiteCaneItem(Settings settings) {
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
        if (!(user instanceof ServerPlayerEntity blind)) {
            return TypedActionResult.fail(stack);
        }
        return BlindCaneService.use(blind, stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.white_cane.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
