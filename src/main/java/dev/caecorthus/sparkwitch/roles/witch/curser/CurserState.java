package dev.caecorthus.sparkwitch.roles.witch.curser;

/** Data-only owner-private Curser cooldown and recipient confusion state. / 仅保存诅咒师私有冷却与受术者混乱状态。 */
public final class CurserState {
    private int cooldownTicks;
    private int confusionTicks;

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public int confusionTicks() {
        return confusionTicks;
    }

    public boolean isConfused() {
        return confusionTicks > 0;
    }

    public void initializeForPromotion() {
        cooldownTicks = CurserRules.INITIAL_COOLDOWN_TICKS;
        confusionTicks = 0;
    }

    public boolean startCooldown() {
        if (cooldownTicks > 0) {
            return false;
        }
        cooldownTicks = CurserRules.COOLDOWN_TICKS;
        return true;
    }

    public void applyConfusion() {
        confusionTicks = CurserRules.CONFUSION_TICKS;
    }

    public boolean tickCooldown() {
        if (cooldownTicks <= 0) {
            return false;
        }
        cooldownTicks--;
        return true;
    }

    public boolean tickConfusion() {
        if (confusionTicks <= 0) {
            return false;
        }
        confusionTicks--;
        return true;
    }

    public void restore(int cooldownTicks, int confusionTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.confusionTicks = Math.max(0, confusionTicks);
    }

    public boolean clear() {
        if (cooldownTicks == 0 && confusionTicks == 0) {
            return false;
        }
        cooldownTicks = 0;
        confusionTicks = 0;
        return true;
    }
}
