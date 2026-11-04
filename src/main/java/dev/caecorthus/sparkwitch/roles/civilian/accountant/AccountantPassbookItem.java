package dev.caecorthus.sparkwitch.roles.civilian.accountant;

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

import java.util.List;

/**
 * 存折 item: right-click deposits (Accountant only), sneak + right-click drops it; left-click withdrawal arrives as
 * {@code AccountantWithdrawC2SPacket} from {@code AccountantAirSwingMixin}. All rules live in
 * {@link AccountantPassbookService}; registered with {@code maxCount(1)}.
 * 存折物品：右键存入（仅会计），潜行 + 右键丢出；左键取出由 {@code AccountantAirSwingMixin} 发送
 * {@code AccountantWithdrawC2SPacket}。规则全部在 {@link AccountantPassbookService}；以 {@code maxCount(1)} 注册。
 */
public final class AccountantPassbookItem extends Item {

    public AccountantPassbookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user instanceof ServerPlayerEntity player) {
            if (player.isSneaking()) {
                AccountantPassbookService.drop(player, hand, stack);
                return TypedActionResult.success(player.getStackInHand(hand));
            }
            AccountantPassbookService.deposit(player, stack);
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.accountant_passbook.tooltip.stored",
                AccountantPassbookService.storedCoins(stack)).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.sparkwitch.accountant_passbook.tooltip.usage")
                .formatted(Formatting.GRAY));
    }
}
