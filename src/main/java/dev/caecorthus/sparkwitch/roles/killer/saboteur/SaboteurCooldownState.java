package dev.caecorthus.sparkwitch.roles.killer.saboteur;

final class SaboteurCooldownState {
    private int cooldownTicks;

    int cooldownTicks() {
        return cooldownTicks;
    }

    boolean setCooldownTicks(int ticks) {
        int normalized = Math.max(0, ticks);
        if (cooldownTicks == normalized) {
            return false;
        }
        cooldownTicks = normalized;
        return true;
    }

    boolean tick() {
        if (cooldownTicks <= 0) {
            return false;
        }
        cooldownTicks--;
        return true;
    }
}
