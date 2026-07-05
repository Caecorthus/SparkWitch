package dev.caecorthus.sparkwitch.roles.civilian.piggod;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PigGodRulesTest {
    private static final Path PIG_GOD_FEATURE_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/roles/civilian/piggod/PigGodFeatureService.java");

    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void constantsMatchDesign() {
        assertEquals(0xF2A4FC, PigGodRules.COLOR);
        assertEquals(150, PigGodRules.COIN_COST);
        assertEquals(GameConstants.getInTicks(1, 0), PigGodRules.COOLDOWN_TICKS);
        assertEquals(0, PigGodRules.FREEZE_TICKS);
        assertEquals(GameConstants.getInTicks(0, 17), PigGodRules.CHASE_TICKS);
        assertEquals(4, PigGodRules.SPEED_AMPLIFIER);
        assertEquals(16.0D, PigGodRules.SOUND_STOP_RANGE_BLOCKS);
    }

    @Test
    void highlightRequiresActiveLivingPigGodAndPreservesHiddenSurvivalMaster() {
        assertTrue(PigGodRules.shouldHighlight(
                SparkWitchRoles.pigGod(),
                true,
                false,
                true,
                true,
                false,
                false
        ));
        assertFalse(PigGodRules.shouldHighlight(SparkWitchRoles.pigGod(), true, false, true, true, false, true));
        assertFalse(PigGodRules.shouldHighlight(WatheRoles.CIVILIAN, true, false, true, true, false, false));
        assertFalse(PigGodRules.shouldHighlight(SparkWitchRoles.pigGod(), false, false, true, true, false, false));
        assertFalse(PigGodRules.shouldHighlight(SparkWitchRoles.pigGod(), true, true, true, true, false, false));
        assertFalse(PigGodRules.shouldHighlight(SparkWitchRoles.pigGod(), true, false, false, true, false, false));
        assertFalse(PigGodRules.shouldHighlight(SparkWitchRoles.pigGod(), true, false, true, false, false, false));
        assertFalse(PigGodRules.shouldHighlight(SparkWitchRoles.pigGod(), true, false, true, true, true, false));
    }

    @Test
    void doorBlastOnlyAppliesToActivePigGodWatheDoors() {
        assertTrue(PigGodRules.shouldUseDoorBlast(
                SparkWitchRoles.pigGod(),
                true,
                true,
                false,
                DoorInteraction.DoorType.SMALL_DOOR
        ));
        assertTrue(PigGodRules.shouldUseDoorBlast(
                SparkWitchRoles.pigGod(),
                true,
                true,
                false,
                DoorInteraction.DoorType.TRAIN_DOOR
        ));
        assertFalse(PigGodRules.shouldUseDoorBlast(WatheRoles.CIVILIAN, true, true, false, DoorInteraction.DoorType.SMALL_DOOR));
        assertFalse(PigGodRules.shouldUseDoorBlast(SparkWitchRoles.pigGod(), false, true, false, DoorInteraction.DoorType.SMALL_DOOR));
        assertFalse(PigGodRules.shouldUseDoorBlast(SparkWitchRoles.pigGod(), true, false, false, DoorInteraction.DoorType.SMALL_DOOR));
        assertFalse(PigGodRules.shouldUseDoorBlast(SparkWitchRoles.pigGod(), true, true, true, DoorInteraction.DoorType.SMALL_DOOR));
    }

    @Test
    void pigChaseDoorBlastUsesWatheCrowbarPrySound() throws IOException {
        String source = Files.readString(PIG_GOD_FEATURE_SOURCE);
        assertTrue(source.contains("WatheSounds.ITEM_CROWBAR_PRY"));
        assertTrue(source.contains("SoundCategory.BLOCKS"));
        assertEquals(2.5f, PigGodRules.DOOR_BLAST_SOUND_VOLUME);
        assertEquals(1.0f, PigGodRules.DOOR_BLAST_SOUND_PITCH);
    }

    @Test
    void damageBlockOnlyAppliesDuringFreeze() {
        assertTrue(PigGodRules.shouldBlockDamage(SparkWitchRoles.pigGod(), true));
        assertFalse(PigGodRules.shouldBlockDamage(SparkWitchRoles.pigGod(), false));
        assertFalse(PigGodRules.shouldBlockDamage(WatheRoles.CIVILIAN, true));
    }

    @Test
    void zeroFreezeStartsChaseImmediatelyOnlyWhenChaseTimeExists() {
        assertTrue(PigGodRules.shouldStartChaseImmediately(0, PigGodRules.CHASE_TICKS));
        assertTrue(PigGodRules.shouldStartChaseImmediately(-20, PigGodRules.CHASE_TICKS));
        assertFalse(PigGodRules.shouldStartChaseImmediately(1, PigGodRules.CHASE_TICKS));
        assertFalse(PigGodRules.shouldStartChaseImmediately(0, 0));
    }

    @Test
    void civilianKillPunishmentOnlyAppliesToActiveLivingPigGod() {
        assertTrue(PigGodRules.shouldPunishPigChaseCivilianKill(
                SparkWitchRoles.pigGod(),
                true,
                true,
                true
        ));
        assertFalse(PigGodRules.shouldPunishPigChaseCivilianKill(WatheRoles.CIVILIAN, true, true, true));
        assertFalse(PigGodRules.shouldPunishPigChaseCivilianKill(SparkWitchRoles.pigGod(), false, true, true));
        assertFalse(PigGodRules.shouldPunishPigChaseCivilianKill(SparkWitchRoles.pigGod(), true, false, true));
        assertFalse(PigGodRules.shouldPunishPigChaseCivilianKill(SparkWitchRoles.pigGod(), true, true, false));
    }

    @Test
    void instinctPriorityPreservesWatheHardSkips() {
        assertTrue(PigGodRules.instinctPriorityPreservesHardSkips());
    }

    @Test
    void soundStopOnlyTargetsNearbyListeners() {
        assertTrue(PigGodRules.shouldStopSoundForListener(0, 64, 0, 16, 64, 0));
        assertFalse(PigGodRules.shouldStopSoundForListener(0, 64, 0, 16.01, 64, 0));
    }
}
