package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Wither Gohei (凋灵御币), granted with the Mini Wither unlock and bound to its Witch Maiden. Its left-click asks the
 * summoned Wither for a crimson skull; the click path is client input plus {@code fire_crimson_skull}, never vanilla
 * melee or mining.
 * 凋灵御币，随小凋灵解锁发放并绑定于巫女。左键令已召唤的小凋灵射出赤色凋灵之首；点击经客户端输入与
 * {@code fire_crimson_skull} 处理，从不走原版近战或挖掘。
 */
public class WitherGoheiItem extends Item {
    static final String TOOLTIP_FIRE_KEY = "item.sparkwitch.wither_gohei.tooltip.fire";
    static final String TOOLTIP_WITHER_KEY = "item.sparkwitch.wither_gohei.tooltip.wither";

    public WitherGoheiItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    /** Left-click fires; the held Gohei never breaks blocks. / 左键用于射击；手持御币永不破坏方块。 */
    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(TOOLTIP_FIRE_KEY, MiniWitherRules.CRIMSON_MANA_COST,
                MiniWitherRules.CRIMSON_COOLDOWN_TICKS / 20).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(TOOLTIP_WITHER_KEY).formatted(Formatting.DARK_GRAY));
    }
}
