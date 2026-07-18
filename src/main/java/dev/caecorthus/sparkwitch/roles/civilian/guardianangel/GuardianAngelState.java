package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Data-only cooldown and owner-private shield target state. / 仅保存冷却与持有者私有的护盾目标状态。 */
public final class GuardianAngelState {
    private int cooldownTicks;
    private @Nullable UUID shieldTargetUuid;

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public @Nullable UUID shieldTargetUuid() {
        return shieldTargetUuid;
    }

    public boolean hasActiveShieldTarget() {
        return shieldTargetUuid != null;
    }

    public void initializeForPromotion() {
        cooldownTicks = GuardianAngelRules.INITIAL_COOLDOWN_TICKS;
        shieldTargetUuid = null;
    }

    public boolean assignShield(UUID targetUuid) {
        if (cooldownTicks > 0 || shieldTargetUuid != null) {
            return false;
        }
        shieldTargetUuid = targetUuid;
        cooldownTicks = GuardianAngelRules.POST_USE_COOLDOWN_TICKS;
        return true;
    }

    public boolean tickCooldown() {
        if (cooldownTicks <= 0) {
            return false;
        }
        cooldownTicks--;
        return true;
    }

    public boolean clearShieldTarget(UUID targetUuid) {
        if (!targetUuid.equals(shieldTargetUuid)) {
            return false;
        }
        shieldTargetUuid = null;
        return true;
    }

    public void restore(int cooldownTicks, @Nullable UUID shieldTargetUuid) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.shieldTargetUuid = shieldTargetUuid;
    }

    public boolean clear() {
        if (cooldownTicks == 0 && shieldTargetUuid == null) {
            return false;
        }
        cooldownTicks = 0;
        shieldTargetUuid = null;
        return true;
    }
}
