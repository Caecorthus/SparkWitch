package dev.caecorthus.sparkwitch.client.timestealer;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Time-Stamp price labels for the local Time Stealer's shop entries, called from {@code WitchShopClientTexts}. Returns
 * {@code null} for every other entry or viewer so existing labels are untouched. Presentation only.
 * 本地窃时者商店商品的时光邮票价格标签，由 {@code WitchShopClientTexts} 调用。其他商品或观察者一律返回 {@code null}，
 * 使既有标签保持不变。仅用于展示。
 */
public final class TimeStealerShopTexts {
    private TimeStealerShopTexts() {
    }

    public static @Nullable Text price(ShopEntry entry) {
        // TODO(WP-05): exact local role && TimeStealerShopRules.stampCost(entry.id()) > 0 -> "gui.sparkwitch.shop.time_stamp_price".
        return null;
    }
}
