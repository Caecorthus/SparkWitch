package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Murderous Witch shop entries, separate from Wathe's native killer shop.
 * 杀意魔女商店条目独立于 wathe 原生杀手商店，避免授予杀手阵营身份。
 */
public final class MurderousWitchShopRules {
    public static final Identifier THROWING_AXE_ID = Identifier.of("noellesroles", "throwing_axe");
    public static final Identifier FIRE_POKER_ID = SparkWitch.id("fire_poker");

    private MurderousWitchShopRules() {
    }

    public static List<PlannedEntry> plannedEntries() {
        return List.of(
                new PlannedEntry("knife", ItemKind.KNIFE, 100, ShopEntry.Type.WEAPON, 1, 1),
                new PlannedEntry("lockpick", ItemKind.LOCKPICK, 50, ShopEntry.Type.TOOL, 1, 1),
                new PlannedEntry("crowbar", ItemKind.CROWBAR, 25, ShopEntry.Type.TOOL, 1, 1),
                new PlannedEntry("throwing_axe", ItemKind.THROWING_AXE, 150, ShopEntry.Type.WEAPON, 1, -1),
                new PlannedEntry("fire_poker", ItemKind.FIRE_POKER, 50, ShopEntry.Type.WEAPON, 1, 1)
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
        LOCKPICK,
        CROWBAR,
        THROWING_AXE,
        FIRE_POKER;

        public Identifier id() {
            return switch (this) {
                case KNIFE -> Registries.ITEM.getId(WatheItems.KNIFE);
                case LOCKPICK -> Registries.ITEM.getId(WatheItems.LOCKPICK);
                case CROWBAR -> Registries.ITEM.getId(WatheItems.CROWBAR);
                case THROWING_AXE -> THROWING_AXE_ID;
                case FIRE_POKER -> FIRE_POKER_ID;
            };
        }

        private ItemStack stack(int count) {
            return switch (this) {
                case KNIFE -> new ItemStack(WatheItems.KNIFE, count);
                case LOCKPICK -> new ItemStack(WatheItems.LOCKPICK, count);
                case CROWBAR -> new ItemStack(WatheItems.CROWBAR, count);
                case THROWING_AXE -> new ItemStack(Registries.ITEM.get(THROWING_AXE_ID), count);
                case FIRE_POKER -> new ItemStack(SparkWitchItems.firePoker(), count);
            };
        }
    }
}
