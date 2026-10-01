package dev.caecorthus.sparkwitch.roles.civilian.fisher.item;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/** Gray {@code item.sparkwitch.<path>.tooltip.lineN} lines shared by the Angler's items. / 钓鱼佬物品共用的灰色说明行。 */
public final class FisherItemTooltips {
    private FisherItemTooltips() {
    }

    public static void append(List<Text> tooltip, Identifier itemId, int lines) {
        for (int line = 1; line <= lines; line++) {
            tooltip.add(Text.translatable("item.sparkwitch." + itemId.getPath() + ".tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
    }
}
