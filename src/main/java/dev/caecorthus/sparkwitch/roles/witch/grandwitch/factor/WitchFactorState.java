package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A round-wide ledger: ownership is provenance, never a lifetime or quota boundary.
 * 全局回合账本：来源仅用于溯源，不控制存续或单独额度。 */
final class WitchFactorState {
    static final int ADMISSION_TICKS = 400;
    static final int MANA_INTERVAL = 600;
    static final int MOOD_INTERVAL = 1200;
    private final Map<UUID, Factor> factors = new HashMap<>();
    private int openingParticipants;
    private int limit;
    private int spent;
    private boolean active;
    private boolean speedUnlocked;

    record Factor(UUID owner, int manaTicks, int moodTicks, int admissionTicks) {
        Factor {
            java.util.Objects.requireNonNull(owner);
            manaTicks = Math.floorMod(manaTicks, MANA_INTERVAL);
            moodTicks = Math.floorMod(moodTicks, MOOD_INTERVAL);
            admissionTicks = Math.clamp(admissionTicks, 0, ADMISSION_TICKS);
        }
        boolean mature() { return admissionTicks == 0; }
        Factor advance() {
            return new Factor(owner, manaTicks + 1, moodTicks + 1, Math.max(0, admissionTicks - 1));
        }
        Factor inherited() { return new Factor(owner, manaTicks, moodTicks, ADMISSION_TICKS); }
    }

    static int capacity(int participants) { return WitchFactorSettings.DEFAULT.limit(participants); }
    static int manaReward(boolean apprentice, boolean murderous) { return murderous ? 60 : apprentice ? 40 : 25; }
    static float moodLoss(boolean apprentice) { return apprentice ? 0.30f : 0.10f; }

    void begin(int participants, int roundLimit) {
        clear();
        openingParticipants = Math.max(0, participants);
        limit = Math.max(WitchFactorSettings.UNLIMITED, roundLimit);
        active = true;
    }

    void clear() {
        factors.clear();
        openingParticipants = 0;
        limit = 0;
        spent = 0;
        active = false;
        speedUnlocked = false;
    }

    boolean active() { return active; }
    int openingParticipants() { return openingParticipants; }
    int limit() { return limit; }
    int spent() { return spent; }
    int remaining() { return limit == -1 ? -1 : Math.max(0, limit - spent); }
    boolean speedUnlocked() { return speedUnlocked; }
    Map<UUID, Factor> factors() { return factors; }
    boolean mature(UUID holder) { return factors.containsKey(holder) && factors.get(holder).mature(); }

    boolean spread(UUID source, UUID holder) {
        if (!active || source.equals(holder) || factors.containsKey(holder) || remaining() == 0) return false;
        factors.put(holder, new Factor(source, 0, 0, ADMISSION_TICKS));
        if (spent < Integer.MAX_VALUE) spent++;
        return true;
    }

    boolean recover(UUID holder) { return factors.remove(holder) != null; }

    boolean afterDeath(UUID victim, UUID killer, boolean killerEligible) {
        Factor factor = factors.remove(victim);
        if (factor == null) return false;
        if (killerEligible && killer != null && !victim.equals(killer) && !factors.containsKey(killer)) {
            factors.put(killer, factor.inherited());
        }
        return true;
    }

    void observeMatureCarriers(int count) {
        if (active && count >= WitchFactorSettings.speedThreshold(openingParticipants, limit)) speedUnlocked = true;
    }

    /** Restoring records must not call spread or reconstruct used quota from survivors.
     * 恢复记录不得调用首传，也不得根据幸存因子反推已用额度。 */
    void restoreProgress(int used, boolean unlocked) {
        spent = Math.max(Math.max(0, used), factors.size());
        speedUnlocked = unlocked;
    }
}
