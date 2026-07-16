package dev.caecorthus.sparkwitch.roles.killer.ninja;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Replaces Ninja's native killer shop while retaining Wathe's exact blackout contract.
 * 替换忍者的原生杀手商店，同时原样保留 Wathe 关灯条目的动态价格与回调。
 */
public final class NinjaShopService {
    private static boolean registered;

    private NinjaShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(NinjaShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!NinjaRules.isNinja(role)) {
            return;
        }

        // Capture instead of reconstructing so Wathe remains the owner of blackout pricing and shared cooldown.
        // 直接保留原条目而非重建，让动态价格与共享冷却继续由 Wathe 负责。
        ShopEntry blackoutEntry = context.getEntries().stream()
                .filter(entry -> "blackout".equals(entry.id()))
                .findFirst()
                .orElse(null);

        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(
                "ninja_knife",
                new ItemStack(SparkWitchItems.ninjaKnife()),
                NinjaRules.NINJA_KNIFE_PRICE,
                ShopEntry.Type.WEAPON
        ).stock(1).build());
        context.addEntry(new ShopEntry.Builder(
                "ninja_shuriken",
                new ItemStack(SparkWitchItems.ninjaShuriken()),
                NinjaRules.NINJA_SHURIKEN_PRICE,
                ShopEntry.Type.WEAPON
        ).build());
        context.addEntry(new ShopEntry.Builder(
                "lockpick",
                new ItemStack(WatheItems.LOCKPICK),
                NinjaRules.LOCKPICK_PRICE,
                ShopEntry.Type.TOOL
        ).build());
        if (blackoutEntry != null) {
            context.addEntry(blackoutEntry);
        }
    }
}
