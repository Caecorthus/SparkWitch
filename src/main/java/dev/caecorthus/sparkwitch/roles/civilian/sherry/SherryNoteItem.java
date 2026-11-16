package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 信息纸条: what 橘雪莉 receives when a badge holder dies. It names the dead holder only (owner rule 2026-11-11: never
 * the killer's name or role); the name is read from the stack's custom data, so it shows in the tooltip on any client.
 * 信息纸条：徽章持有者死亡时橘雪莉收到的纸条。只写死去的持有者名字（所有者规则 2026-11-11：不含凶手的名字与职业）；
 * 名字读取自物品的自定义数据，因此任何客户端的提示框都能显示。
 */
public final class SherryNoteItem extends Item {

    public SherryNoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        String victim = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt()
                .getString(SherryRules.NOTE_VICTIM_KEY);
        if (victim.isEmpty()) {
            tooltip.add(Text.translatable("item.sparkwitch.sherry_note.tooltip").formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.translatable("item.sparkwitch.sherry_note.tooltip.victim", victim)
                    .formatted(Formatting.GOLD));
        }
    }
}
