package dev.caecorthus.sparkwitch.roles.neutral.sniper;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Sniper shop: Springfield .30-06 service ammunition (weapon frame, unlimited) then a crowbar (tool frame, one per
 * round), both 25. SparkTraits entries are preserved across the rebuild. The master key and tablet are issued at round
 * start and never sold here.
 * 狙击手商店：春田 .30-06 制式步枪弹（武器类边框，不限量）与撬棍（工具类边框，每局限购一件），各 25 块。重建时保留
 * SparkTraits 词条。万能钥匙与平板在开局发放，此处不出售。
 */
public final class SniperShopService {
    private static boolean registered;

    private SniperShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SniperShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SniperRules.isSniperRole(role)) {
            return;
        }
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(
                SniperRules.AMMO_ENTRY_ID,
                new ItemStack(SparkWitchItems.ammo3006()),
                SniperRules.AMMO_PRICE,
                ShopEntry.Type.WEAPON
        ).build());
        context.addEntry(new ShopEntry.Builder(
                SniperRules.CROWBAR_ENTRY_ID,
                WatheItems.CROWBAR.getDefaultStack(),
                SniperRules.CROWBAR_PRICE,
                ShopEntry.Type.TOOL
        ).stock(SniperRules.CROWBAR_STOCK).build());
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }
}
