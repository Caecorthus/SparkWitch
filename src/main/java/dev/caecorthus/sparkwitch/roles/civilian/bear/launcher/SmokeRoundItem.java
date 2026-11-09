package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The 40 mm smoke grenade ({@code sparkwitch:smoke_round_40mm}): a plain round loaded into the M32A1 from the cursor
 * ({@link M32CursorLoading}, the launcher's {@code onClicked}); it has no use action, so it cannot be thrown by hand
 * (D11), and the fired round does no damage (G1). Stacks to {@link BearRules#SMOKE_ROUND_MAX_STACK}.
 * 40 mm 烟雾榴弹（{@code sparkwitch:smoke_round_40mm}）：从光标装入 M32A1 的普通弹药（{@link M32CursorLoading}，即发射器的
 * {@code onClicked}）；它没有使用动作，因此不能用手投掷（D11），射出的榴弹没有伤害（G1）。最多堆叠
 * {@link BearRules#SMOKE_ROUND_MAX_STACK} 个。
 */
public class SmokeRoundItem extends Item {
    static final String TOOLTIP_KEY = "item.sparkwitch.smoke_round_40mm.tooltip.line1";

    public SmokeRoundItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(BearRules.SMOKE_ROUND_MAX_STACK);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(TOOLTIP_KEY).formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
