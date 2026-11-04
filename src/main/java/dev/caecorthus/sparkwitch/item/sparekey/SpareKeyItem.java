package dev.caecorthus.sparkwitch.item.sparekey;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 备用钥匙 Spare Key. Its points are vanilla durability (the bar shows what is left); door handling lives in
 * {@link SpareKeyDoors} and the Attendant's round-start key in {@link SpareKeyAttendantKit}. No death drop is
 * registered, so it never drops on death.
 * 备用钥匙。点数即原版耐久（耐久条显示剩余）；开门逻辑在 {@link SpareKeyDoors}，乘务员的开局钥匙在
 * {@link SpareKeyAttendantKit}。未注册死亡掉落，因此死亡时不会掉落。
 */
public final class SpareKeyItem extends Item {

    public SpareKeyItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings()
                .maxCount(1)
                .maxDamage(SpareKeyRules.DURABILITY);
    }

    /** Remaining points on this stack. / 这把钥匙的剩余点数。 */
    public static int remaining(ItemStack stack) {
        return SpareKeyRules.remaining(stack.getMaxDamage(), stack.getDamage());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Stack data only: the creative search index builds tooltips off-thread with no player.
        // 只读物品堆数据：创造模式搜索索引会在后台线程、无玩家时构建提示文本。
        tooltip.add(Text.translatable(
                "item.sparkwitch.spare_key.tooltip.remaining",
                remaining(stack),
                stack.getMaxDamage()
        ).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(
                "item.sparkwitch.spare_key.tooltip.cost",
                SpareKeyRules.TRAIN_DOOR_COST,
                SpareKeyRules.ROOM_DOOR_COST
        ).formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable(
                "item.sparkwitch.spare_key.tooltip.cooldown",
                SpareKeyRules.COOLDOWN_TICKS / 20
        ).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
