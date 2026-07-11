package dev.caecorthus.sparkwitch.client.text;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Client-only shop labels for mana-priced Grand Witch spells.
 * 大魔女魔力商店的客户端文本，避免法术显示成 0 金币。
 */
public final class WitchShopClientTexts {
    private WitchShopClientTexts() {
    }

    public static MutableText price(ShopEntry entry, String fallback) {
        GrandWitchRules.GrandWitchSpell spell = GrandWitchRules.GrandWitchSpell.fromEntryId(entry.id());
        if (spell == null) {
            return Text.literal(fallback);
        }
        return Text.translatable("gui.sparkwitch.shop.mana_price", spell.manaCost())
                .formatted(Formatting.LIGHT_PURPLE);
    }
}
