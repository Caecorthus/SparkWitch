package dev.caecorthus.sparkwitch.client.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;

/** Ceremonial Sword tooltip only: Witch Factor uses the shared primary dispatch and the Grand Witch owns no
 * secondary-key handler.
 * 仅负责仪礼剑提示：魔女因子沿用共用主技能分发，大魔女没有二技能键处理器。 */
public final class GrandWitchClientModule {
    private static boolean registered;

    private GrandWitchClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isOf(SparkWitchItems.ceremonialSword())) {
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.unlock"));
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.attack"));
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.dash"));
                lines.add(Text.translatable("item.sparkwitch.ceremonial_sword.tooltip.protection"));
            }
        });
        registered = true;
    }
}
