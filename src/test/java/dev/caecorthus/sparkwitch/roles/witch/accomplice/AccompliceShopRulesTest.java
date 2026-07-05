package dev.caecorthus.sparkwitch.roles.witch.accomplice;

import dev.caecorthus.sparkwitch.roles.witch.accomplice.AccompliceShop.AccompliceShopRules;
import dev.doctor4t.wathe.util.ShopEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccompliceShopRulesTest {
    @Test
    void accompliceShopContainsOnlyPlannedKillerStyleTools() {
        List<AccompliceShopRules.PlannedEntry> entries = AccompliceShopRules.plannedEntries();

        assertEquals(6, entries.size());
        assertEntry(entries, "knife", AccompliceShopRules.ItemKind.KNIFE, 100, ShopEntry.Type.WEAPON, 1, 1);
        assertEntry(entries, "revolver", AccompliceShopRules.ItemKind.REVOLVER, 300, ShopEntry.Type.WEAPON, 1, -1);
        assertEntry(entries, "lockpick", AccompliceShopRules.ItemKind.LOCKPICK, 50, ShopEntry.Type.TOOL, 1, 1);
        assertEntry(entries, "crowbar", AccompliceShopRules.ItemKind.CROWBAR, 25, ShopEntry.Type.TOOL, 1, 1);
        assertEntry(entries, "firecracker", AccompliceShopRules.ItemKind.FIRECRACKER, 25, ShopEntry.Type.TOOL, 1, -1);
        assertEntry(entries, "note", AccompliceShopRules.ItemKind.NOTE, 5, ShopEntry.Type.TOOL, 4, -1);

        assertTrue(entries.stream().noneMatch(entry -> "grenade".equals(entry.id())));
        assertTrue(entries.stream().noneMatch(entry -> "blackout".equals(entry.id())));
        assertTrue(entries.stream().noneMatch(entry -> "poison_vial".equals(entry.id())));
    }

    private static void assertEntry(
            List<AccompliceShopRules.PlannedEntry> entries,
            String id,
            AccompliceShopRules.ItemKind item,
            int price,
            ShopEntry.Type type,
            int count,
            int stock
    ) {
        AccompliceShopRules.PlannedEntry entry = entries.stream()
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
