package dev.caecorthus.sparkwitch.client.text;

import dev.caecorthus.sparkwitch.client.timestealer.TimeStealerShopTexts;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Client-only shop labels for the two non-coin currencies: mana-priced Grand Witch spells and Time-Stamp-priced
 * Time Stealer entries (delegated to the role-owned {@link TimeStealerShopTexts}), so neither shows as 0 coins.
 * Presentation only: the server alone validates and charges every purchase.
 * 两种非金币货币的客户端商店文本：大魔女的魔力法术，以及窃时者的时光邮票商品（委托给职业自有的
 * {@link TimeStealerShopTexts}），避免显示成 0 金币。仅用于展示：每次购买的校验与扣费只由服务端完成。
 */
public final class WitchShopClientTexts {
    private WitchShopClientTexts() {
    }

    public static MutableText price(ShopEntry entry, String fallback) {
        Text stampPrice = TimeStealerShopTexts.price(entry);
        if (stampPrice != null) {
            return stampPrice.copy();
        }
        GrandWitchRules.GrandWitchSpell spell = GrandWitchRules.GrandWitchSpell.fromEntryId(entry.id());
        if (spell == null) {
            return Text.literal(fallback);
        }
        return Text.translatable("gui.sparkwitch.shop.mana_price", spell.manaCost())
                .formatted(Formatting.LIGHT_PURPLE);
    }
}
