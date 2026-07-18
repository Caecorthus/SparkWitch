package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import java.util.UUID;
import net.minecraft.util.Identifier;

/**
 * Pure Focused Footsteps targeting, timing, and run-phase rules.
 * 聚焦步伐的纯目标、时间与跑步阶段规则。
 */
public final class FocusedFootstepsRules {
    public static final Identifier SKILL_ID = WitchMaidenRules.FOCUSED_FOOTSTEPS_SKILL_ID;
    public static final int COLOR = WitchMaidenRules.COLOR;
    public static final int INITIAL_COOLDOWN_TICKS =
            WitchMaidenRules.FOCUSED_FOOTSTEPS_INITIAL_COOLDOWN_TICKS;
    public static final int EFFECT_TICKS = WitchMaidenRules.FOCUSED_FOOTSTEPS_DURATION_TICKS;
    public static final int COOLDOWN_TICKS = WitchMaidenRules.FOCUSED_FOOTSTEPS_COOLDOWN_TICKS;
    public static final int COOLDOWN_AFTER_EFFECT_TICKS = COOLDOWN_TICKS - EFFECT_TICKS;

    private FocusedFootstepsRules() {
    }

    public static boolean isValidTarget(
            UUID casterUuid,
            UUID targetUuid,
            boolean online,
            boolean sameWorld,
            boolean assignedToMatch,
            boolean dead,
            boolean playingAndAlive
    ) {
        return casterUuid != null
                && targetUuid != null
                && !casterUuid.equals(targetUuid)
                && online
                && sameWorld
                && assignedToMatch
                && !dead
                && playingAndAlive;
    }

    public static Phase initialPhase(boolean infiniteStamina, boolean exhausted) {
        return !infiniteStamina && exhausted ? Phase.WALKING : Phase.RUNNING;
    }

    public static Phase nextPhase(Phase current, boolean infiniteStamina, boolean exhausted) {
        if (current == Phase.WALKING) {
            return Phase.WALKING;
        }
        return !infiniteStamina && exhausted ? Phase.WALKING : Phase.RUNNING;
    }

    public static boolean confirmsSuccessfulUse(int previousCooldownTicks, int currentCooldownTicks) {
        return previousCooldownTicks == 0 && currentCooldownTicks > COOLDOWN_AFTER_EFFECT_TICKS;
    }

    public static int effectTicksFromCooldown(int cooldownTicks) {
        return Math.max(0, cooldownTicks - COOLDOWN_AFTER_EFFECT_TICKS);
    }

    public enum Phase {
        RUNNING(0),
        WALKING(1);

        private final int amplifier;

        Phase(int amplifier) {
            this.amplifier = amplifier;
        }

        public int amplifier() {
            return amplifier;
        }

        public static Phase fromAmplifier(int amplifier) {
            return amplifier >= WALKING.amplifier ? WALKING : RUNNING;
        }
    }
}
