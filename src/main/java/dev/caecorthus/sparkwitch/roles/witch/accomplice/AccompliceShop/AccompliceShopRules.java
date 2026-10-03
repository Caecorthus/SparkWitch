package dev.caecorthus.sparkwitch.roles.witch.accomplice.AccompliceShop;

import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Shop-building helper for special accomplices: the plain Accomplice entries, in order, minus the given
     * {@link PlannedEntry#id()} values. Variants call it from their own {@code BuildShopEntries} listener; the plain
     * Accomplice shop itself stays exact. An unknown id throws {@link IllegalArgumentException}.
     * 供特殊共犯构建商店：按原顺序返回普通共犯商品，去掉给定的 {@link PlannedEntry#id()}。特殊共犯在自己的
     * {@code BuildShopEntries} 监听器中调用；普通共犯商店本身保持不变。未知 id 抛出 {@link IllegalArgumentException}。
     */
    public static List<ShopEntry> entriesWithout(String... entryIds) {
        return plannedEntriesWithout(entryIds).stream()
                .map(PlannedEntry::toShopEntry)
                .toList();
    }

    public static List<PlannedEntry> plannedEntriesWithout(String... entryIds) {
        List<PlannedEntry> planned = plannedEntries();
        Set<String> excluded = new HashSet<>(List.of(entryIds));
        for (String id : excluded) {
            if (planned.stream().noneMatch(entry -> entry.id().equals(id))) {
                throw new IllegalArgumentException("Unknown Accomplice shop entry id: " + id);
            }
        }
        return planned.stream()
                .filter(entry -> !excluded.contains(entry.id()))
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
