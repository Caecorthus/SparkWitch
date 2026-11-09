package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Gray three-line tooltips for the Ghost Trigger and the Peachwood Sword, like the other killer kit items (Bear Trap,
 * Knockout Drug). Client presentation only; the numbers mirror the server rules in {@code SpecterTriggerService} and
 * {@code SpecterPeachwoodService}, so a rule change there must update the lang lines too.
 * 鬼魂触发器与桃木剑的三行灰色提示，与其他杀手道具（捕兽夹、迷药）一致。仅客户端展示；数值对应
 * {@code SpecterTriggerService} 与 {@code SpecterPeachwoodService} 中的服务端规则，规则变更时须同步修改语言文本。
 */
final class SpecterItemTooltips {
    private static final int LINES = 3;

    private SpecterItemTooltips() {
    }

    static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> append(stack, lines));
    }

    private static void append(ItemStack stack, List<Text> lines) {
        String prefix;
        if (stack.isOf(SparkWitchItems.ghostTrigger())) {
            prefix = "item.sparkwitch.ghost_trigger.tooltip.line";
        } else if (stack.isOf(SparkWitchItems.peachwoodSword())) {
            prefix = "item.sparkwitch.peachwood_sword.tooltip.line";
        } else {
            return;
        }
        for (int line = 1; line <= LINES; line++) {
            lines.add(Text.translatable(prefix + line).formatted(Formatting.GRAY));
        }
    }
}
