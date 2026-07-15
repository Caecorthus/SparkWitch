package dev.caecorthus.sparkwitch.roles.killer.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HunterPoisonAttributionTest {
    private static final UUID PLACER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID POISONER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void confirmsTheLatestMatchingTrapPoisonThroughItsExpectedExpiry() {
        HunterPoisonAttribution attribution = HunterPoisonAttribution.forTrap(
                PLACER,
                POISONER,
                1_000L,
                HunterRules.TRAP_POISON_TICKS
        );

        assertTrue(attribution.matchesConfirmedPoisonDeath(true, 1_800L, POISONER));
        assertTrue(attribution.matchesConfirmedPoisonDeath(true, 1_700L, POISONER));
        assertFalse(attribution.matchesConfirmedPoisonDeath(true, 1_800L, PLACER));
        assertFalse(attribution.matchesConfirmedPoisonDeath(false, 1_800L, POISONER));
        assertFalse(attribution.matchesConfirmedPoisonDeath(true, 1_801L, POISONER));
    }

    @Test
    void fallsBackToThePlacerWhenTheTrapHadNoExplicitPoisoner() {
        HunterPoisonAttribution attribution = HunterPoisonAttribution.forTrap(
                PLACER,
                null,
                50L,
                HunterRules.TRAP_POISON_TICKS
        );

        assertEquals(PLACER, attribution.effectivePoisonerUuid());
        assertTrue(attribution.matchesConfirmedPoisonDeath(true, 850L, PLACER));
    }

    @Test
    void rejectsAttributionAfterItsExpectedExpiry() {
        HunterPoisonAttribution attribution = HunterPoisonAttribution.forTrap(
                PLACER,
                POISONER,
                100L,
                HunterRules.TRAP_POISON_TICKS
        );

        assertTrue(attribution.matchesConfirmedPoisonDeath(true, 500L, POISONER));
        assertTrue(attribution.matchesConfirmedPoisonDeath(true, 900L, POISONER));
        assertFalse(attribution.matchesConfirmedPoisonDeath(true, 901L, POISONER));
    }
}
