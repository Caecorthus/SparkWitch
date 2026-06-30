package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/**
 * Pure tuning and predicates for Pig God.
 * 皮革噶的的纯数值与判断规则集中在这里，避免影响其他职业。
 */
public final class PigGodRules {
    public static final Identifier PIG_CHASE_ID = SparkWitch.id("pig_chase");
    public static final int COLOR = 0xF2A4FC;
    public static final int COIN_COST = 150;
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final int FREEZE_TICKS = GameConstants.getInTicks(0, 5);
    public static final int CHASE_TICKS = GameConstants.getInTicks(0, 12);
    public static final int SPEED_AMPLIFIER = 4;
    public static final int INSTINCT_PRIORITY = 90;
    public static final float SOUND_VOLUME = 1.0f;
    public static final float SOUND_PITCH = 1.0f;
    public static final double SOUND_STOP_RANGE_BLOCKS = 16.0D;

    private PigGodRules() {
    }

    public static boolean isPigGod(Role role) {
        return role == dev.caecorthus.sparkwitch.SparkWitchRoles.pigGod();
    }

    public static boolean shouldHighlight(
            Role viewerRole,
            boolean viewerAlive,
            boolean viewerSpectatingOrCreative,
            boolean chaseActive,
            boolean targetAlive,
            boolean targetSpectatingOrCreative,
            boolean hiddenSurvivalMaster
    ) {
        return isPigGod(viewerRole)
                && viewerAlive
                && !viewerSpectatingOrCreative
                && chaseActive
                && targetAlive
                && !targetSpectatingOrCreative
                && !hiddenSurvivalMaster;
    }

    public static boolean shouldUseDoorBlast(
            Role role,
            boolean chaseActive,
            boolean serverSide,
            boolean blasted,
            DoorInteraction.DoorType doorType
    ) {
        return isPigGod(role)
                && chaseActive
                && serverSide
                && !blasted
                && (doorType == DoorInteraction.DoorType.SMALL_DOOR
                || doorType == DoorInteraction.DoorType.TRAIN_DOOR);
    }

    public static boolean shouldBlockDamage(Role role, boolean freezeActive) {
        return isPigGod(role) && freezeActive;
    }

    public static boolean shouldPunishPigChaseCivilianKill(
            Role killerRole,
            boolean chaseActive,
            boolean killerAlive,
            boolean victimCivilian
    ) {
        return isPigGod(killerRole)
                && chaseActive
                && killerAlive
                && victimCivilian;
    }

    public static boolean shouldStopSoundForListener(
            double soundX,
            double soundY,
            double soundZ,
            double listenerX,
            double listenerY,
            double listenerZ
    ) {
        double deltaX = listenerX - soundX;
        double deltaY = listenerY - soundY;
        double deltaZ = listenerZ - soundZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                <= SOUND_STOP_RANGE_BLOCKS * SOUND_STOP_RANGE_BLOCKS;
    }

    public static boolean instinctPriorityPreservesHardSkips() {
        return INSTINCT_PRIORITY < GetInstinctHighlight.HighlightResult.PRIORITY_HIGH;
    }
}
