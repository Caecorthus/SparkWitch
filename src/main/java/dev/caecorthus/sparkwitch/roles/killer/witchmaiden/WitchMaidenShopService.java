package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Replaces only Witch Maiden's shop while preserving Wathe's exact blackout entry.
 * 仅替换巫女商店，并原样保留 Wathe 构造的停电条目。
 */
public final class WitchMaidenShopService {
    private static boolean registered;

    private WitchMaidenShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(WitchMaidenShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!WitchMaidenRules.isWitchMaiden(role)) {
            return;
        }

        // Capture instead of reconstructing so Wathe keeps ownership of dynamic price and cooldown behavior.
        // 保留原条目而非重建，让动态价格和冷却行为继续由 Wathe 负责。
        ShopEntry blackoutEntry = context.getEntries().stream()
                .filter(entry -> "blackout".equals(entry.id()))
                .findFirst()
                .orElse(null);

        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(
                "knife",
                WatheItems.KNIFE.getDefaultStack(),
                WitchMaidenRules.KNIFE_PRICE,
                ShopEntry.Type.WEAPON
        ).stock(1).build());
        context.addEntry(new ShopEntry.Builder(
                "lockpick",
                WatheItems.LOCKPICK.getDefaultStack(),
                WitchMaidenRules.LOCKPICK_PRICE,
                ShopEntry.Type.TOOL
        ).stock(1).build());
        context.addEntry(new ShopEntry.Builder(
                "poison_vial",
                WatheItems.POISON_VIAL.getDefaultStack(),
                WitchMaidenRules.POISON_PRICE,
                ShopEntry.Type.POISON
        ).build());
        context.addEntry(new ShopEntry.Builder(
                "scorpion",
                WatheItems.SCORPION.getDefaultStack(),
                WitchMaidenRules.POISON_PRICE,
                ShopEntry.Type.POISON
        ).build());
        context.addEntry(new ShopEntry.Builder(
                "poison_apple",
                SparkWitchItems.poisonApple().getDefaultStack(),
                WitchMaidenRules.POISON_PRICE,
                ShopEntry.Type.POISON
        ).build());
        context.addEntry(new ShopEntry.Builder(
                "tofana_elixir",
                SparkWitchItems.tofanaElixir().getDefaultStack(),
                WitchMaidenRules.TOFANA_PRICE,
                ShopEntry.Type.POISON
        ).stock(1).build());
        if (blackoutEntry != null) {
            context.addEntry(blackoutEntry);
        }
    }
}
