package dev.caecorthus.sparkwitch.roles.witch.accomplice.AccompliceShop;

import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Accomplice shop entries kept separate from Wathe's native killer shop.
 * 共犯商店条目独立于 wathe 原生杀手商店，避免把共犯塞进原生杀手桶。
 */
public final class AccompliceShopRules {
    private AccompliceShopRules() {
    }

    public static List<PlannedEntry> plannedEntries() {
        return List.of(
                new PlannedEntry("knife", ItemKind.KNIFE, 100, ShopEntry.Type.WEAPON, 1, 1),
                new PlannedEntry("revolver", ItemKind.REVOLVER, 300, ShopEntry.Type.WEAPON, 1, -1),
                new PlannedEntry("lockpick", ItemKind.LOCKPICK, 50, ShopEntry.Type.TOOL, 1, 1),
                new PlannedEntry("crowbar", ItemKind.CROWBAR, 25, ShopEntry.Type.TOOL, 1, 1),
                new PlannedEntry("firecracker", ItemKind.FIRECRACKER, 25, ShopEntry.Type.TOOL, 1, -1),
                new PlannedEntry("note", ItemKind.NOTE, 5, ShopEntry.Type.TOOL, 4, -1)
        );
    }

    public static List<ShopEntry> entries() {
        return plannedEntries().stream()
                .map(PlannedEntry::toShopEntry)
                .toList();
    }

    public record PlannedEntry(
            String id,
            ItemKind item,
            int price,
            ShopEntry.Type type,
            int count,
            int maxStock
    ) {
        private ShopEntry toShopEntry() {
            ShopEntry.Builder builder = new ShopEntry.Builder(id, item.stack(count), price, type);
            if (maxStock > 0) {
                builder.stock(maxStock);
            }
            return builder.build();
        }
    }

    public enum ItemKind {
        KNIFE,
        REVOLVER,
        LOCKPICK,
        CROWBAR,
        FIRECRACKER,
        NOTE;

        private ItemStack stack(int count) {
            return switch (this) {
                case KNIFE -> new ItemStack(WatheItems.KNIFE, count);
                case REVOLVER -> new ItemStack(WatheItems.REVOLVER, count);
                case LOCKPICK -> new ItemStack(WatheItems.LOCKPICK, count);
                case CROWBAR -> new ItemStack(WatheItems.CROWBAR, count);
                case FIRECRACKER -> new ItemStack(WatheItems.FIRECRACKER, count);
                case NOTE -> new ItemStack(WatheItems.NOTE, count);
            };
        }
    }
}
