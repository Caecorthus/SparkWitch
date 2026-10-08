package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A single loose .338 round (stacks to 64). Bought rounds are never auto-loaded: the player chooses the load order,
 * which is the firing order (D17).
 * 单发散装 .338 子弹（可堆叠到 64）。买到的子弹不会自动装填：装弹顺序就是发射顺序，由玩家决定（D17）。
 */
public class UsecAmmoItem extends Item {
    private final UsecAmmoType ammoType;

    public UsecAmmoItem(UsecAmmoType ammoType, Settings settings) {
        super(settings);
        this.ammoType = ammoType;
    }

    public static Settings createSettings() {
        return new Settings();
    }

    public UsecAmmoType ammoType() {
        return ammoType;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.usec_338_" + ammoType.id() + ".tooltip")
                .formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
