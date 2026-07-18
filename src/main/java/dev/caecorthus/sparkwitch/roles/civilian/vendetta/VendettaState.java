package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Mutable bond and reveal-cycle state kept behind the private player component. */
final class VendettaState {
    private @Nullable UUID boundKillerUuid;
    private boolean active;
    private int revealCooldownTicks;
    private int revealActiveTicks;
    private boolean timerPaused;
    private boolean knifeAvailable;

    @Nullable UUID boundKillerUuid() {
        return boundKillerUuid;
    }

    boolean isActive() {
        return active;
    }

    int revealCooldownTicks() {
        return revealCooldownTicks;
    }

    int revealActiveTicks() {
        return revealActiveTicks;
    }

    boolean isTimerPaused() {
        return timerPaused;
    }

    boolean isRevealActive() {
        return active && revealActiveTicks > 0;
    }

    boolean isKnifeAvailable() {
        return active && knifeAvailable;
    }

    boolean stageCreditedKiller(@Nullable UUID killerUuid) {
        if (!clear()) {
            boundKillerUuid = killerUuid;
            return killerUuid != null;
        }
        boundKillerUuid = killerUuid;
        return true;
    }

    boolean activate() {
        if (boundKillerUuid == null) {
            return false;
        }
        active = true;
        revealCooldownTicks = VendettaRules.REVEAL_COOLDOWN_TICKS;
        revealActiveTicks = 0;
        timerPaused = false;
        knifeAvailable = true;
        return true;
    }

    boolean tickTimer(boolean bothOnline) {
        if (!active) {
            return false;
        }
        if (!bothOnline) {
            timerPaused = true;
            return false;
        }
        timerPaused = false;
        if (revealActiveTicks > 0) {
            revealActiveTicks--;
            if (revealActiveTicks == 0) {
                revealCooldownTicks = VendettaRules.REVEAL_COOLDOWN_TICKS;
            }
            return true;
        }
        if (revealCooldownTicks > 0) {
            revealCooldownTicks--;
            if (revealCooldownTicks == 0) {
                revealActiveTicks = VendettaRules.REVEAL_DURATION_TICKS;
            }
            return true;
        }
        revealActiveTicks = VendettaRules.REVEAL_DURATION_TICKS;
        return true;
    }

    boolean clear() {
        boolean changed = boundKillerUuid != null || active || revealCooldownTicks != 0
                || revealActiveTicks != 0 || timerPaused || knifeAvailable;
        boundKillerUuid = null;
        active = false;
        revealCooldownTicks = 0;
        revealActiveTicks = 0;
        timerPaused = false;
        knifeAvailable = false;
        return changed;
    }

    void restore(@Nullable UUID killerUuid, boolean active, int cooldownTicks, int activeTicks) {
        restore(killerUuid, active, cooldownTicks, activeTicks, active);
    }

    void restore(
            @Nullable UUID killerUuid,
            boolean active,
            int cooldownTicks,
            int activeTicks,
            boolean knifeAvailable
    ) {
        boundKillerUuid = killerUuid;
        this.active = active && killerUuid != null;
        revealCooldownTicks = Math.clamp(cooldownTicks, 0, VendettaRules.REVEAL_COOLDOWN_TICKS);
        revealActiveTicks = Math.clamp(activeTicks, 0, VendettaRules.REVEAL_DURATION_TICKS);
        timerPaused = false;
        this.knifeAvailable = this.active && knifeAvailable;
        if (!this.active) {
            revealCooldownTicks = 0;
            revealActiveTicks = 0;
        }
    }

    boolean consumeKnife() {
        if (!isKnifeAvailable()) {
            return false;
        }
        knifeAvailable = false;
        return true;
    }
}
