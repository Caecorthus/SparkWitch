package dev.caecorthus.sparkwitch.roles.civilian.savior;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The Savior's lightsaber. Attributes are copied verbatim from Wathe's bat: one item per stack and the bat's
 * {@code ToolMaterials.WOOD} damage/speed modifiers (no extra attack damage, -3 attack speed). The blade lights the
 * holder's surroundings through the bundled LambDynamicLights data file
 * {@code assets/sparkwitch/dynamiclights/item/savior_lightsaber.json} (torch-level 14).
 * 救世主光剑。属性逐项复制 Wathe 球棒：每堆一个，以及球棒使用的 {@code ToolMaterials.WOOD} 伤害/攻速修饰（无额外攻击伤害，
 * 攻速 -3）。光刃通过内置的 LambDynamicLights 数据文件
 * {@code assets/sparkwitch/dynamiclights/item/savior_lightsaber.json} 照亮持有者周围（火把级 14）。
 */
public final class SaviorLightsaberItem extends Item {
    public SaviorLightsaberItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings()
                .maxCount(1)
                .attributeModifiers(MiningToolItem.createAttributeModifiers(ToolMaterials.WOOD, 0.0F, -3.0F));
    }

    /** Two-line role hint, matching the other SparkWitch weapon tooltips. / 两行职业提示，与其他 SparkWitch 武器提示一致。 */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.savior_lightsaber.tooltip.line1")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.savior_lightsaber.tooltip.line2")
                .formatted(Formatting.GRAY));
    }
}
