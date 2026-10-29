package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/** Builds Hunter's restricted killer shop without changing any other role's entries. */
public final class HunterShopService {
    private static final Set<String> RETAINED_NATIVE_ENTRIES = Set.of(
            "knife",
            "body_bag",
            "crowbar",
            "scorpion"
    );
    private static boolean registered;

    private HunterShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(HunterShopService::buildEntries);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !HunterRules.ROLE_ID.equals(role.identifier())) {
            return;
        }

        context.getEntries().removeIf(entry -> !RETAINED_NATIVE_ENTRIES.contains(entry.id()));
        context.addEntry(0, new ShopEntry.Builder(
                "hunter_trap",
                new ItemStack(Registries.ITEM.get(HunterTrapItem.ID)),
                HunterRules.TRAP_PRICE,
                ShopEntry.Type.WEAPON
        ).build());
        context.addEntry(0, new ShopEntry.Builder(
                "double_barrel_shell",
                new ItemStack(Registries.ITEM.get(DoubleBarrelShellItem.ID)),
                HunterRules.SHELL_PRICE,
                ShopEntry.Type.WEAPON
        ).build());
        context.addEntry(0, new ShopEntry.Builder(
                "double_barrel_shotgun",
                new ItemStack(Registries.ITEM.get(DoubleBarrelShotgunItem.ID)),
                HunterRules.SHOTGUN_PRICE,
                ShopEntry.Type.WEAPON
        ).stock(HunterRules.SHOTGUN_STOCK).build());
        // Index 2: the slug sits right after the shell (and the shotgun before both, the add order at 0 reverses).
        // 下标 2：独头弹紧挨霰弹之后（下标 0 依次前插使顺序反转，猎枪在最前）。
        context.addEntry(2, new ShopEntry.Builder(
                "double_barrel_slug",
                new ItemStack(Registries.ITEM.get(DoubleBarrelSlugItem.ID)),
                HunterRules.SLUG_PRICE,
                ShopEntry.Type.WEAPON
        ).build());
    }
}
