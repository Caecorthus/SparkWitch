package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * One Potion Gunner shell type. Shells are loaded into the launcher from the inventory cursor; they do nothing when
 * used on their own.
 * 一种药炮手炮弹。炮弹通过背包光标装入炮筒；单独使用没有效果。
 */
public class PotionShellItem extends Item {
    private final PotionShellType type;

    public PotionShellItem(PotionShellType type, Settings settings) {
        super(settings);
        this.type = type;
    }

    public static Settings createSettings() {
        return new Settings().maxCount(PotionGunnerRules.SHELL_MAX_STACK);
    }

    public PotionShellType shellType() {
        return type;
    }

    /**
     * Two gray lines per shell: {@code item.sparkwitch.<shell>.tooltip} (who and where) and {@code .tooltip.line2}
     * (the effect and its falloff), kept short because vanilla tooltips never wrap.
     * 每种炮弹两行灰色说明：{@code item.sparkwitch.<shell>.tooltip}（影响谁、范围多大）与 {@code .tooltip.line2}
     * （效果与衰减）；原版提示框不会自动换行，因此每行保持简短。
     */
    public static List<String> tooltipKeys(PotionShellType type) {
        String base = "item." + type.itemId().getNamespace() + "." + type.itemId().getPath() + ".tooltip";
        return List.of(base, base + ".line2");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (String key : tooltipKeys(this.type)) {
            tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
