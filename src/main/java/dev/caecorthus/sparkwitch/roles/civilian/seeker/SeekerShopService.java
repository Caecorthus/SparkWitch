package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/**
 * Replaces the Seeker's shop on both sides with the camera (150, re-buyable any time with no ownership limit). The
 * entry keeps Wathe's default buy handler and passes through the optional SparkTraits Charisma discount. The
 * SparkStrength tablet is issued by SparkStrength at round start, never sold here.
 * 在两端把搜寻者商店替换为摄像头（150，随时可再买、不限拥有数量）。条目保留 Wathe 默认购买处理，
 * 并经过可选的 SparkTraits 魅力折扣。SparkStrength 平板由 SparkStrength 开局发放，此处不出售。
 */
public final class SeekerShopService {
    private static boolean registered;

    private SeekerShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(SeekerShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!SeekerShopRules.rebuildsShopFor(role)) {
            return;
        }
        // SparkTraits entries (e.g. the Impostor revolver) may already be present; keep them across the rebuild.
        // SparkTraits 条目（如内鬼左轮）可能已先加入；重建时保留它们。
        List<ShopEntry> preservedTraitEntries = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        for (SeekerShopRules.EntrySpec spec : SeekerShopRules.entries()) {
            ShopEntry entry = switch (spec.kind()) {
                case CAMERA -> cameraEntry(spec);
            };
            context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, entry));
        }
        SparkTraitsShopEntryPreserver.restore(context, preservedTraitEntries);
    }

    private static ShopEntry cameraEntry(SeekerShopRules.EntrySpec spec) {
        // No stock and no ownership limit: any number of cameras, only money limits it.
        // 不设库存也不限拥有数量：摄像头数量不限，只受金钱限制。
        return new ShopEntry.Builder(spec.id(), SparkWitchItems.seekerCamera().getDefaultStack(), spec.price(),
                ShopEntry.Type.TOOL).build();
    }
}
