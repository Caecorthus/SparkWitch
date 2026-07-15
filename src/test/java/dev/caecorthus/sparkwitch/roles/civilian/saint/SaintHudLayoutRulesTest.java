package dev.caecorthus.sparkwitch.roles.civilian.saint;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaintHudLayoutRulesTest {
    private static final int FONT_HEIGHT = 9;

    @Test
    void reservesTheBundledNoellesRolesHudRows() {
        assertEquals(36, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("noellesroles", "assassin"), FONT_HEIGHT, false));
        assertEquals(31, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("noellesroles", "taotie"), FONT_HEIGHT, false));
        assertEquals(18, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("noellesroles", "morphling"), FONT_HEIGHT, false));
        assertEquals(18, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("noellesroles", "reporter"), FONT_HEIGHT, false));
        assertEquals(9, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("noellesroles", "detective"), FONT_HEIGHT, false));
        assertEquals(0, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("noellesroles", "jester"), FONT_HEIGHT, false));
    }

    @Test
    void reservesTheExistingSparkWitchSkillLineOnlyForSparkWitchRoles() {
        assertEquals(14, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("sparkwitch", "pig_god"), FONT_HEIGHT, true));
        assertEquals(0, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("sparkwitch", "saint"), FONT_HEIGHT, false));
        assertEquals(9, SaintHudLayoutRules.reservedBottomHeight(
                Identifier.of("noellesroles", "detective"), FONT_HEIGHT, true));
    }

    @Test
    void placesTheSaintLineAboveReservedRowsWithAStableGap() {
        assertEquals(171, SaintHudLayoutRules.drawY(200, FONT_HEIGHT, 18));
        assertEquals(191, SaintHudLayoutRules.drawY(200, FONT_HEIGHT, 0));
    }

    @Test
    void keepsTheAssassinAndSparkWitchRightPadding() {
        assertEquals(5, SaintHudLayoutRules.rightPadding(
                Identifier.of("noellesroles", "assassin"), false));
        assertEquals(5, SaintHudLayoutRules.rightPadding(
                Identifier.of("sparkwitch", "grand_witch"), true));
        assertEquals(0, SaintHudLayoutRules.rightPadding(
                Identifier.of("noellesroles", "reporter"), false));
    }
}
