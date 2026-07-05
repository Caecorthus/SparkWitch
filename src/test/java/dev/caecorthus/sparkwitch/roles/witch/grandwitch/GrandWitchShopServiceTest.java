package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrandWitchShopServiceTest {
    @Test
    void obscureAndBlindnessUseRequestedDisplayItems() {
        assertEquals(
                Identifier.ofVanilla("barrier"),
                GrandWitchShopService.displayItemIdForSpell(WitchFactionRules.GrandWitchSpell.OBSCURE)
        );
        assertEquals(
                Identifier.ofVanilla("ender_pearl"),
                GrandWitchShopService.displayItemIdForSpell(WitchFactionRules.GrandWitchSpell.BLINDNESS)
        );
    }

    @Test
    void otherSpellDisplayItemsStayUnchanged() {
        assertEquals(
                Identifier.ofVanilla("soul_lantern"),
                GrandWitchShopService.displayItemIdForSpell(WitchFactionRules.GrandWitchSpell.FEAR)
        );
        assertEquals(
                Identifier.ofVanilla("anvil"),
                GrandWitchShopService.displayItemIdForSpell(WitchFactionRules.GrandWitchSpell.HEAVINESS)
        );
    }

    @Test
    void spellDescriptionsUseBottomShopLoreStyle() {
        for (WitchFactionRules.GrandWitchSpell spell : WitchFactionRules.GrandWitchSpell.values()) {
            LoreComponent lore = GrandWitchShopService.descriptionLoreForSpell(spell);

            assertNotNull(lore, spell.name());
            assertEquals(1, lore.lines().size(), spell.name());
            assertEquals(0x808080, lore.lines().getFirst().getStyle().getColor().getRgb(), spell.name());
            assertFalse(lore.lines().getFirst().getStyle().isItalic(), spell.name());
            assertTranslatable(spell.descriptionTranslationKey(), lore.lines().getFirst());
            assertEquals(0x808080, lore.styledLines().getFirst().getStyle().getColor().getRgb(), spell.name());
            assertFalse(lore.styledLines().getFirst().getStyle().isItalic(), spell.name());
        }
    }

    private static void assertTranslatable(String expectedKey, Text text) {
        TranslatableTextContent content = assertInstanceOf(TranslatableTextContent.class, text.getContent());
        assertEquals(expectedKey, content.getKey());
    }
}
