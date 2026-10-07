package dev.caecorthus.sparkwitch.roles.neutral.insider;

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
 * Replaces the Insider's shop on both sides with {@link InsiderShopRules#entries}: revolver and crowbar. Every entry
 * keeps Wathe's default buy handler; there is no Charisma discount. The SparkStrength tablet is issued by
 * SparkStrength at round start, never sold here.
 * 在两端把内应商店替换为 {@link InsiderShopRules#entries}：左轮与撬棍。所有条目保留 Wathe 默认购买处理，
 * 不接入魅力折扣。SparkStrength 平板由 SparkStrength 开局发放，此处不出售。
 */
public final class InsiderShopService {
    private static boolean registered;

    private InsiderShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(InsiderShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!InsiderShopRules.rebuildsShopFor(role)) {
            return;
        }
        // SparkTraits entries may already be present; keep them across the rebuild.
        // SparkTraits 条目可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        for (InsiderShopRules.EntrySpec spec : InsiderShopRules.entries()) {
            ShopEntry entry = switch (spec.kind()) {
                case REVOLVER -> itemEntry(spec, WatheItems.REVOLVER.getDefaultStack(), ShopEntry.Type.WEAPON);
                case CROWBAR -> itemEntry(spec, WatheItems.CROWBAR.getDefaultStack(), ShopEntry.Type.TOOL);
            };
            context.addEntry(entry);
        }
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    private static ShopEntry itemEntry(InsiderShopRules.EntrySpec spec, ItemStack stack, ShopEntry.Type type) {
        return new ShopEntry.Builder(spec.id(), stack, spec.price(), type)
                .stock(spec.stock())
                .build();
    }
}
