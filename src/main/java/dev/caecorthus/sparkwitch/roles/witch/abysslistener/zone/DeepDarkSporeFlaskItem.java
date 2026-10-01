package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

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
 * Abyss Listener Deep Dark Spore Flask: a consumable throwable bought from the role's own shop. The server throws a
 * role-owned {@link DeepDarkSporeFlaskEntity}; vanilla's use-item packet (which carries the aim) is the whole request.
 * 聆渊者的深暗孢瓶：从本职业商店购买的消耗型投掷物。服务端投出本职业自有的 {@link DeepDarkSporeFlaskEntity}；
 * 原版使用物品数据包（已携带瞄准方向）就是全部请求。
 */
public final class DeepDarkSporeFlaskItem extends Item {
    private static final int TOOLTIP_LINES = 3;

    public DeepDarkSporeFlaskItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(16);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // L3 implements: server-side throw of a DeepDarkSporeFlaskEntity.
        // L3 实现：服务端投出 DeepDarkSporeFlaskEntity。
        return super.use(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.deep_dark_spore_flask.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
