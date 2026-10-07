package dev.caecorthus.sparkwitch.roles.killer.bellringer;

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
 * Bound Bell Ringer bell. Every gate and the toll itself are server-authoritative in
 * {@link BellTollService}; the glint only mirrors the owner's server-computed target flag.
 * 敲钟人的绑定之钟。所有判定与敲钟结算都由服务端 {@link BellTollService} 负责；
 * 光效只反映服务端为拥有者计算的目标标记。
 */
public final class TollBellItem extends Item {
    private static final int TOOLTIP_LINES = 3;

    public TollBellItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // Client only predicts the swing (same as the Feather Blade) and never mutates the stack, so the
        // server's verdict and the vanilla cooldown packet cannot desync it.
        // 客户端只预测挥手动作（与羽刃相同）且从不修改物品堆，因此不会与服务端判定及原版冷却数据包失步。
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity serverUser)) {
            return TypedActionResult.fail(stack);
        }
        return BellTollService.use(serverUser, stack, hand);
    }

    /** Client-local glint; see {@link TollBellGlint}. / 客户端本地光效，见 {@link TollBellGlint}。 */
    @Override
    public boolean hasGlint(ItemStack stack) {
        return TollBellGlint.isLit();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.toll_bell.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
