package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WraithPromotionServiceTest {
    private static final Identifier WIND = SparkWitchRoles.WIND_SPIRIT_ID;
    private static final Identifier GUARDIAN = SparkWitchRoles.GUARDIAN_ANGEL_ID;
    private static final Identifier VENDETTA = SparkWitchRoles.VENDETTA_ID;
    private static final Identifier SABOTEUR = SparkWitchRoles.SABOTEUR_ID;
    private static final Identifier CURSER = SparkWitchRoles.CURSER_ID;

    @Test
    void forcedPromotionStillRequiresAnActiveUnpromotedWraith() {
        assertEquals(WraithPromotionService.Failure.INACTIVE,
                WraithPromotionService.validateState(false, false, WraithState.Alignment.GOOD));
        assertEquals(WraithPromotionService.Failure.ALREADY_PROMOTED,
                WraithPromotionService.validateState(true, true, WraithState.Alignment.GOOD));
        assertEquals(WraithPromotionService.Failure.MISSING_ALIGNMENT,
                WraithPromotionService.validateState(true, false, null));
        assertNull(WraithPromotionService.validateState(
                true, false, WraithState.Alignment.GOOD));
    }

    @Test
    void rejectsInactivePromotedAndMissingAlignmentBeforePoolLookup() {
        assertEquals(WraithPromotionService.Failure.INACTIVE,
                WraithPromotionService.validate(false, false, null, WIND, true));
        assertEquals(WraithPromotionService.Failure.ALREADY_PROMOTED,
                WraithPromotionService.validate(true, true, WraithState.Alignment.GOOD, WIND, true));
        assertEquals(WraithPromotionService.Failure.MISSING_ALIGNMENT,
                WraithPromotionService.validate(true, false, null, WIND, true));
    }

    @Test
    void validatesGoodKillerAndWitchPoolsWithoutCrossAlignmentPromotion() {
        List<Identifier> good = List.of(WIND, GUARDIAN, VENDETTA);
        List<Identifier> killer = List.of(SABOTEUR);
        List<Identifier> witch = List.of(CURSER);

        assertNull(WraithPromotionService.validateAgainstAllowedIds(
                true, false, WraithState.Alignment.GOOD, WIND, true, good));
        assertNull(WraithPromotionService.validateAgainstAllowedIds(
                true, false, WraithState.Alignment.KILLER, SABOTEUR, true, killer));
        assertNull(WraithPromotionService.validateAgainstAllowedIds(
                true, false, WraithState.Alignment.WITCH, CURSER, true, witch));
        assertEquals(WraithPromotionService.Failure.WRONG_ALIGNMENT,
                WraithPromotionService.validateAgainstAllowedIds(
                        true, false, WraithState.Alignment.GOOD, SABOTEUR, true, good));
        assertEquals(WraithPromotionService.Failure.WRONG_ALIGNMENT,
                WraithPromotionService.validateAgainstAllowedIds(
                        true, false, WraithState.Alignment.KILLER, CURSER, true, killer));
        assertEquals(WraithPromotionService.Failure.WRONG_ALIGNMENT,
                WraithPromotionService.validateAgainstAllowedIds(
                        true, false, WraithState.Alignment.WITCH, WIND, true, witch));
    }

    @Test
    void preservesVendettaEligibilityFilter() {
        assertEquals(WraithPromotionService.Failure.VENDETTA_INELIGIBLE,
                WraithPromotionService.validateAgainstAllowedIds(
                        true, false, WraithState.Alignment.GOOD, VENDETTA, false,
                        List.of(WIND, GUARDIAN)));
    }
}
