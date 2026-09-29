package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherItemTooltips;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.List;

/**
 * Knife-feel, single-use Swordfish (Vendetta knife pattern): inherits Wathe's hold/SPEAR use but never sends Wathe's
 * {@code KnifeStabPayload}; the client sends {@link SwordfishStabC2SPayload} and the server decides. Visible in hand.
 * 手感同刀的一次性剑鱼（复仇者之刃模式）：沿用 Wathe 的蓄力 / SPEAR 动作，但从不发送 Wathe 的刺杀包；客户端发送
 * {@link SwordfishStabC2SPayload}，由服务端决定。手持可见。WP3 completes.
 */
public final class SwordfishItem extends KnifeItem {
    private static final int TOOLTIP_LINES = 2;

    public SwordfishItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            SwordfishStabService.recordServerRelease(player, getMaxUseTime(stack, user) - remainingUseTicks);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        FisherItemTooltips.append(tooltip, FisherRules.SWORDFISH_ID, TOOLTIP_LINES);
    }
}
