package dev.caecorthus.sparkwitch.client.timestealer;

import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStampInventory;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerShopRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Time-Stamp price labels for the local Time Stealer's shop entries, called from {@code WitchShopClientTexts}. Returns
 * {@code null} for every other entry or viewer so existing labels are untouched. Presentation only.
 * 本地窃时者商店商品的时光邮票价格标签，由 {@code WitchShopClientTexts} 调用。其他商品或观察者一律返回 {@code null}，
 * 使既有标签保持不变。仅用于展示。
 *
 * <p>The role check is required because {@code psycho_mode} is shared with every other killer's coin entry. The
 * affordability color reads the local inventory plus cursor through the same {@link TimeStampInventory#balance} the
 * server uses, so no stamp count is synced; the server still validates and charges every purchase.
 * 必须判定职业，因为 {@code psycho_mode} 与其他杀手的金币商品共用。可负担性颜色经服务端同样使用的
 * {@link TimeStampInventory#balance} 读取本地背包加光标，因此无需同步邮票数；每次购买仍由服务端校验与扣除。
 */
public final class TimeStealerShopTexts {
    private static final String PRICE_KEY = "gui.sparkwitch.shop.time_stamp_price";

    private TimeStealerShopTexts() {
    }

    public static @Nullable Text price(ShopEntry entry) {
        if (entry == null) {
            return null;
        }
        int cost = TimeStealerShopRules.stampCost(entry.id());
        if (cost <= 0) {
            return null;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null
                || !TimeStealerRules.isTimeStealer(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return null;
        }
        boolean affordable = TimeStampInventory.balance(player) >= cost;
        return Text.translatable(PRICE_KEY, cost).formatted(affordable ? Formatting.WHITE : Formatting.RED);
    }
}
