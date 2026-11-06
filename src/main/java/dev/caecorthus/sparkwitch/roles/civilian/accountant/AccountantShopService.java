package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 会计 shop: one 存折 passbook ({@link AccountantRules#PASSBOOK_PRICE} coins, unlimited stock,
 * {@link AccountantRules#PASSBOOK_PURCHASE_COOLDOWN_TICKS} ticks between purchases). Added on both sides for the exact
 * role (Wathe builds the list on both sides and buys by index, so the gate ignores the running state) and passed
 * through the optional SparkTraits Charisma discount like the Discoverer, Seeker and USEC shops. Having an entry is
 * also what lets the Accountant open the shop and see the money HUD (Wathe's {@code canAccessShop} fallback).
 * 会计商店：一个存折（{@link AccountantRules#PASSBOOK_PRICE} 金币，不限购，两次购买间隔
 * {@link AccountantRules#PASSBOOK_PURCHASE_COOLDOWN_TICKS} 刻）。在两端按确切职业加入（Wathe 在两端构建列表并按下标购买，
 * 因此门槛不看运行状态），并像探索者、搜寻者与 USEC 商店一样经过可选的 SparkTraits 魅力折扣。有条目本身就让会计能打开商店并
 * 看到金币 HUD（Wathe 的 {@code canAccessShop} 回退）。
 */
final class AccountantShopService {
    private static boolean registered;

    private AccountantShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(AccountantShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!AccountantRules.isAccountant(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        ShopEntry passbook = new ShopEntry.Builder(AccountantRules.PASSBOOK_ENTRY_ID,
                new ItemStack(SparkWitchItems.accountantPassbook()), AccountantRules.PASSBOOK_PRICE,
                ShopEntry.Type.TOOL)
                .cooldown(AccountantRules.PASSBOOK_PURCHASE_COOLDOWN_TICKS)
                .build();
        context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, passbook));
    }
}
