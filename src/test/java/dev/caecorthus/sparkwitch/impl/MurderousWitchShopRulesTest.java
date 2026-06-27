package dev.caecorthus.sparkwitch.impl;

import dev.doctor4t.wathe.util.ShopEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MurderousWitchShopRulesTest {
    @Test
    void murderousWitchShopContainsOnlyPlannedToolsAndWeapons() {
        List<MurderousWitchShopRules.PlannedEntry> entries = MurderousWitchShopRules.plannedEntries();

        assertEquals(5, entries.size());
        assertEntry(entries, "knife", MurderousWitchShopRules.ItemKind.KNIFE, 100, ShopEntry.Type.WEAPON, 1, 1);
        assertEntry(entries, "lockpick", MurderousWitchShopRules.ItemKind.LOCKPICK, 50, ShopEntry.Type.TOOL, 1, 1);
        assertEntry(entries, "crowbar", MurderousWitchShopRules.ItemKind.CROWBAR, 25, ShopEntry.Type.TOOL, 1, 1);
        assertEntry(entries, "throwing_axe", MurderousWitchShopRules.ItemKind.THROWING_AXE, 150, ShopEntry.Type.WEAPON, 1, -1);
        assertEntry(entries, "fire_poker", MurderousWitchShopRules.ItemKind.FIRE_POKER, 50, ShopEntry.Type.WEAPON, 1, 1);

        assertEquals("noellesroles:throwing_axe", MurderousWitchShopRules.ItemKind.THROWING_AXE.id().toString());
        assertEquals("sparkwitch:fire_poker", MurderousWitchShopRules.ItemKind.FIRE_POKER.id().toString());
    }

    private static void assertEntry(
            List<MurderousWitchShopRules.PlannedEntry> entries,
            String id,
            MurderousWitchShopRules.ItemKind item,
            int price,
            ShopEntry.Type type,
            int count,
            int stock
    ) {
        MurderousWitchShopRules.PlannedEntry entry = entries.stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
        assertEquals(item, entry.item());
        assertEquals(price, entry.price());
        assertEquals(type, entry.type());
        assertEquals(count, entry.count());
        assertEquals(stock, entry.maxStock());
    }
}
