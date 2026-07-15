package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfumerRulesTest {
    @Test
    void exposesApprovedIdsAndTuning() {
        assertEquals("sparkwitch:perfumer", PerfumerRules.ROLE_ID);
        assertEquals("sparkwitch:perfume_essence", PerfumerRules.PERFUME_ESSENCE_ID);
        assertEquals("sparkwitch:cologne", PerfumerRules.COLOGNE_ID);
        assertEquals("perfume_essence", PerfumerRules.PERFUME_ESSENCE_ENTRY_ID);
        assertEquals("cologne", PerfumerRules.COLOGNE_ENTRY_ID);
        assertEquals(0xF2A4A4, PerfumerRules.ROLE_COLOR);
        assertEquals(0xC13838, PerfumerRules.BLOODY_OUTLINE_COLOR);
        assertEquals(0xD8D8D8, PerfumerRules.CORPSE_OUTLINE_COLOR);
        assertEquals(100, PerfumerRules.PERFUME_ESSENCE_PRICE);
        assertEquals(50, PerfumerRules.COLOGNE_PRICE);
        assertEquals(50, PerfumerRules.TASK_REWARD);
        assertEquals(200, PerfumerRules.COLOGNE_DURATION_TICKS);
        assertEquals(20, PerfumerRules.COLOGNE_PULSE_INTERVAL_TICKS);
        assertEquals(0.05F, PerfumerRules.COLOGNE_MOOD_PER_PULSE);
    }

    @Test
    void outlineAndCorpseRangesIncludeTheirExactBoundaries() {
        assertTrue(PerfumerRules.isWithinVisibleOutlineRange(12.0D * 12.0D));
        assertFalse(PerfumerRules.isWithinVisibleOutlineRange(Math.nextUp(12.0D * 12.0D)));

        assertTrue(PerfumerRules.isWithinWallOutlineRange(4.0D * 4.0D));
        assertFalse(PerfumerRules.isWithinWallOutlineRange(Math.nextUp(4.0D * 4.0D)));

        assertTrue(PerfumerRules.isWithinCorpseSanityRange(4.0D * 4.0D));
        assertFalse(PerfumerRules.isWithinCorpseSanityRange(Math.nextUp(4.0D * 4.0D)));
    }

    @Test
    void outlineUsesTheShorterRangeWhenWallsBlockSight() {
        assertTrue(PerfumerRules.shouldOutlinePlayer(12.0D * 12.0D, true));
        assertFalse(PerfumerRules.shouldOutlinePlayer(Math.nextUp(12.0D * 12.0D), true));

        assertTrue(PerfumerRules.shouldOutlinePlayer(4.0D * 4.0D, false));
        assertFalse(PerfumerRules.shouldOutlinePlayer(Math.nextUp(4.0D * 4.0D), false));
    }

    @Test
    void nearbyCorpsesDoubleRatherThanStackTheBaselineDrain() {
        assertEquals(0.0F, PerfumerRules.extraCorpseMoodDrain(0.02F, 0));
        assertEquals(0.02F, PerfumerRules.extraCorpseMoodDrain(0.02F, 1));
        assertEquals(0.02F, PerfumerRules.extraCorpseMoodDrain(0.02F, 4));
    }

    @Test
    void cologneRangeIncludesThreeBlocksAndMoodPulsesCapAtOne() {
        assertTrue(PerfumerRules.isWithinCologneRange(3.0D * 3.0D));
        assertFalse(PerfumerRules.isWithinCologneRange(Math.nextUp(3.0D * 3.0D)));

        assertEquals(0.55F, PerfumerRules.applyCologneMoodPulse(0.5F), 0.0001F);
        assertEquals(1.0F, PerfumerRules.applyCologneMoodPulse(0.98F), 0.0001F);
        assertEquals(1.0F, PerfumerRules.applyCologneMoodPulse(1.0F), 0.0001F);
    }

    @Test
    void perfumeEssenceRequiresAPlayingPerfumerAndAnotherLivingTarget() {
        assertTrue(PerfumerRules.canApplyPerfumeEssence(true, true, true, false));
        assertFalse(PerfumerRules.canApplyPerfumeEssence(false, true, true, false));
        assertFalse(PerfumerRules.canApplyPerfumeEssence(true, false, true, false));
        assertFalse(PerfumerRules.canApplyPerfumeEssence(true, true, false, false));
        assertFalse(PerfumerRules.canApplyPerfumeEssence(true, true, true, true));
    }

    @Test
    void cologneAllowsSelfOrAnUnobstructedLivingTargetWithinThreeBlocks() {
        assertTrue(PerfumerRules.canApplyCologne(true, true, true, true, 100.0D, false));
        assertTrue(PerfumerRules.canApplyCologne(true, true, true, false, 9.0D, true));
        assertFalse(PerfumerRules.canApplyCologne(true, true, true, false, Math.nextUp(9.0D), true));
        assertFalse(PerfumerRules.canApplyCologne(true, true, true, false, 4.0D, false));
        assertFalse(PerfumerRules.canApplyCologne(false, true, true, true, 0.0D, true));
        assertFalse(PerfumerRules.canApplyCologne(true, false, true, true, 0.0D, true));
        assertFalse(PerfumerRules.canApplyCologne(true, true, false, false, 1.0D, true));
    }
}
