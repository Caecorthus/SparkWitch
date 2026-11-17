package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 「拳头子弹」 ({@code sparkwitch:sphinx_hao_fist_ammo}), stacks of 16, sold for hao in the Sphinx Hao shop. It has no
 * use of its own: using an empty 「“手”枪」 loads one ({@link SphinxHaoGunService}). Bound to its holder like the gun and
 * deleted on death ({@link SphinxHaoGunInventory}).
 * 「拳头子弹」（{@code sparkwitch:sphinx_hao_fist_ammo}），16 个一组，在狮身豪商店以豪度值出售。自身没有使用动作：使用空膛的
 * 「“手”枪」时装填一发（{@link SphinxHaoGunService}）。与手枪一样绑定持有者，死亡时删除（{@link SphinxHaoGunInventory}）。
 */
public final class SphinxHaoFistAmmoItem extends Item {
    static final String TOOLTIP_KEY = "item.sparkwitch.sphinx_hao_fist_ammo.tooltip";
    public static final int MAX_COUNT = 16;

    public SphinxHaoFistAmmoItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(MAX_COUNT);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(TOOLTIP_KEY).formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
