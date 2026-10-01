package dev.caecorthus.sparkwitch.roles.witch.abysslistener.gun;

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
 * Abyss Listener Shriek Gun (Taser template): a bound, non-lethal, server-authoritative hitscan. It never joins
 * Wathe's gun tag or gun packet, so no forged client packet can turn it into a lethal shot; vanilla's use-item packet
 * (which carries the aim) is the whole request.
 * 聆渊者的啸音铳（以电击枪为模板）：绑定、非致命、由服务端权威判定的直射道具。它永不加入 Wathe 枪械标签或枪械数据包，
 * 因此伪造的客户端数据包无法把它变成致命射击；原版使用物品数据包（已携带瞄准方向）就是全部请求。
 */
public final class ShriekGunItem extends Item {
    private static final int TOOLTIP_LINES = 3;

    public ShriekGunItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // L2 implements: server ray, knockback, debuffs and the vanilla item cooldown.
        // L2 实现：服务端射线、击退、减益与原版物品冷却。
        return super.use(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.shriek_gun.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
