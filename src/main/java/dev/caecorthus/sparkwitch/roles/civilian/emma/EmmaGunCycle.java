package dev.caecorthus.sparkwitch.roles.civilian.emma;

/** One reward per accepted trigger pull, including its Niko repeats.
 * 一次有效扣动扳机及其 Niko 补射共用一次奖励。 */
public final class EmmaGunCycle {
    private boolean rewarded;
    private boolean cooldownKnown;
    private boolean cooldownApplied;
    private int startTick;
    private int endTick;

    public boolean confirmKill(boolean wasCarrier, boolean terminalDeath) {
        if (rewarded || !wasCarrier || !terminalDeath) {
            return false;
        }
        rewarded = true;
        return true;
    }

    public void establishCooldown(int startTick, int endTick) {
        if (!cooldownKnown) {
            this.startTick = startTick;
            this.endTick = Math.max(startTick, endTick);
            cooldownKnown = true;
        }
    }

    public boolean needsCooldownReward() {
        return rewarded && cooldownKnown && !cooldownApplied;
    }

    public int reducedRemaining(int currentTick) {
        long duration = Math.max(0L, (long) endTick - startTick);
        long reducedEnd = (long) startTick + (duration + 9L) / 10L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, reducedEnd - currentTick));
    }

    public void markCooldownApplied() {
        cooldownApplied = true;
    }
}
