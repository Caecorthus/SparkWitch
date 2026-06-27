package dev.caecorthus.sparkwitch.impl;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrandWitchShopServiceTest {
    @Test
    void obscureAndBlindnessUseEnderEyeDisplayItems() {
        assertEquals(
                Identifier.ofVanilla("ender_eye"),
                GrandWitchShopService.displayItemIdForSpell(GrandWitchRules.GrandWitchSpell.OBSCURE)
        );
        assertEquals(
                Identifier.ofVanilla("ender_eye"),
                GrandWitchShopService.displayItemIdForSpell(GrandWitchRules.GrandWitchSpell.BLINDNESS)
        );
    }

    @Test
    void otherSpellDisplayItemsStayUnchanged() {
        assertEquals(
                Identifier.ofVanilla("soul_lantern"),
                GrandWitchShopService.displayItemIdForSpell(GrandWitchRules.GrandWitchSpell.FEAR)
        );
        assertEquals(
                Identifier.ofVanilla("anvil"),
                GrandWitchShopService.displayItemIdForSpell(GrandWitchRules.GrandWitchSpell.HEAVINESS)
        );
    }
}
