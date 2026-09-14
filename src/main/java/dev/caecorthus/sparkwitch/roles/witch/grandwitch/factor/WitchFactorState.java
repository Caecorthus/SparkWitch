package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server ledger; UUID ownership survives logout without copying player state.
 * 服务端账本：UUID 所有权在离线期间保留，不复制玩家组件。 */
final class WitchFactorState {
    static final int MANA_INTERVAL = 600;
    static final int MOOD_INTERVAL = 1200;
    private final Map<UUID, Factor> factors = new HashMap<>();
    private int openingParticipants;
    private boolean active;

    record Factor(UUID owner, int manaTicks, int moodTicks) {
        Factor {
            manaTicks = Math.floorMod(manaTicks, MANA_INTERVAL);
            moodTicks = Math.floorMod(moodTicks, MOOD_INTERVAL);
        }

        Factor advance() {
            return new Factor(owner, manaTicks + 1, moodTicks + 1);
        }
    }

    static int capacity(int participants) {
        return Math.max(0, participants) / 5;
    }

    static int manaReward(boolean apprentice, boolean murderous) {
        return murderous ? 60 : apprentice ? 40 : 25;
    }

    // Wathe 1.5.6 REAL mood uses normalized units (1 = 100 points).
    // Wathe 1.5.6 的真实理智使用归一化数值（1 = 100 点）。
    static float moodLoss(boolean apprentice) {
        return apprentice ? 0.30f : 0.10f;
    }

    void begin(int participants) {
        clear();
        openingParticipants = Math.max(0, participants);
        active = true;
    }

    void clear() {
        factors.clear();
        openingParticipants = 0;
        active = false;
    }

    boolean active() { return active; }
    int openingParticipants() { return openingParticipants; }
    Map<UUID, Factor> factors() { return factors; }

    java.util.Set<UUID> visibleFor(UUID viewer, boolean accomplice, boolean authorized) {
        if (!active || !authorized) {
            return java.util.Set.of();
        }
        java.util.Set<UUID> visible = new java.util.HashSet<>();
        factors.forEach((holder, factor) -> {
            if (accomplice || factor.owner().equals(viewer)) {
                visible.add(holder);
            }
        });
        return java.util.Set.copyOf(visible);
    }

    boolean spread(UUID owner, UUID holder) {
        if (!active || owner.equals(holder) || factors.containsKey(holder)
                || factors.values().stream().filter(f -> f.owner().equals(owner)).count()
                >= capacity(openingParticipants)) {
            return false;
        }
        factors.put(holder, new Factor(owner, 0, 0));
        return true;
    }

    void recoverForRecruitment(UUID recruited) {
        factors.remove(recruited);
        factors.entrySet().removeIf(e -> e.getValue().owner().equals(recruited));
    }

    boolean afterDeath(UUID victim, UUID killer, boolean killerValid, boolean accomplice) {
        Factor factor = factors.remove(victim);
        if (factor == null) {
            return false;
        }
        if (killerValid && killer != null && !victim.equals(killer)
                && !factor.owner().equals(killer) && !accomplice && !factors.containsKey(killer)) {
            factors.put(killer, factor);
        }
        return true;
    }
}
