package dev.caecorthus.sparkwitch.roles.killer.timestealer;

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
 * The Time Stealer's physical currency ({@code sparkwitch:time_stamp}, owner decision Q3). It has no use action: it
 * never swings or succeeds, so holding it stays invisible to other living players. The balance is whatever stamps sit
 * in the holder's own inventory plus cursor; binding, grants and spending are server-side in the role's services.
 * 窃时者的实体货币（{@code sparkwitch:time_stamp}，所有者决定 Q3）。它没有使用动作：从不挥手、从不返回成功，
 * 因此手持时其他存活玩家看不到。余额即持有者自身背包加光标中的邮票数；绑定、发放与花费都在职业服务的服务端完成。
 */
public final class TimeStampItem extends Item {
    public TimeStampItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(TimeStealerRules.STAMP_MAX_STACK);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.time_stamp.tooltip").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
