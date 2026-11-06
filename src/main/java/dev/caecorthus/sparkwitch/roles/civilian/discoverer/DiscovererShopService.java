package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 探索者 shop: one vanilla spyglass (150, stock 1), added on both sides for the exact role and passed through the
 * optional SparkTraits Charisma discount like the Seeker and USEC shops. The role gate ignores the running state
 * because Wathe builds the list on both sides, including while the round is still STARTING.
 * 探索者商店：一个原版望远镜（150，库存 1），在两端按确切职业加入，并像搜寻者与 USEC 商店一样经过可选的 SparkTraits
 * 魅力折扣。职业门槛不看运行状态，因为 Wathe 在两端构建列表，包括对局仍处于 STARTING 时。
 */
public final class DiscovererShopService {
    private static boolean registered;

    private DiscovererShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(DiscovererShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!DiscovererRules.isDiscoverer(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        ShopEntry spyglass = new ShopEntry.Builder(DiscovererRules.SPYGLASS_ENTRY_ID, new ItemStack(Items.SPYGLASS),
                DiscovererRules.SPYGLASS_PRICE, ShopEntry.Type.TOOL)
                .stock(DiscovererRules.SPYGLASS_STOCK)
                .build();
        context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, spyglass));
    }
}
