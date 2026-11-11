package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A round-wide ledger: ownership is provenance, never a lifetime or quota boundary.
 * 全局回合账本：来源仅用于溯源，不控制存续或单独额度。 */
final class WitchFactorState {
    static final int ADMISSION_TICKS = 400;
    static final int MANA_INTERVAL = 600;
    static final int MOOD_INTERVAL = 1200;
    private final Map<UUID, Factor> factors = new HashMap<>();
    /**
     * Players shown in their own role colour to the network view (a self-made witch), with or without a factor. Not
     * saved: the one writer ({@code WitchFactorService.exposeRoleColor}) re-asserts it from its own saved state.
     * 在因子网络视图中以自身职业色显示的玩家（自成魔女），与是否仍持有因子无关。不存档：唯一的写入方
     * （{@code WitchFactorService.exposeRoleColor}）会依据其自身的存档状态重新写入。
     */
    private final Set<UUID> roleColored = new HashSet<>();
    private int openingParticipants;
    private int limit;
    private int spent;
    /** Self-made factors granted this round; they never came out of the quota. / 本局授予的自成因子数；它们不占用额度。 */
    private int selfMade;
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
        roleColored.clear();
        openingParticipants = 0;
        limit = 0;
        spent = 0;
        selfMade = 0;
        active = false;
        speedUnlocked = false;
    }

    boolean active() { return active; }
    int openingParticipants() { return openingParticipants; }
    int limit() { return limit; }
    int spent() { return spent; }
    int selfMade() { return selfMade; }
    int remaining() { return limit == -1 ? -1 : Math.max(0, limit - spent); }
    boolean speedUnlocked() { return speedUnlocked; }
    Map<UUID, Factor> factors() { return factors; }
    Set<UUID> roleColored() { return roleColored; }
    boolean mature(UUID holder) { return factors.containsKey(holder) && factors.get(holder).mature(); }

    boolean spread(UUID source, UUID holder) {
        if (!active || source.equals(holder) || factors.containsKey(holder) || remaining() == 0) return false;
        factors.put(holder, new Factor(source, 0, 0, ADMISSION_TICKS));
        if (spent < Integer.MAX_VALUE) spent++;
        return true;
    }

    /**
     * A factor its holder made herself (二阶堂希罗's 魔女化): no source, so it spends no quota and ignores an exhausted
     * one, and it is mature at once. The owner is the holder herself, since provenance must name somebody and nobody
     * spread it; from then on it is an ordinary factor (income, mood, purification, inheritance keep that owner).
     * 持有者自己生成的因子（二阶堂希罗的魔女化）：没有来源，因此不消耗额度、额度耗尽时也可生成，并且立即成熟。来源记为持有者
     * 本人（溯源必须指向某人，而它并非他人传播）；此后它就是普通因子（收益、情绪、净化与继承都沿用该来源）。
     */
    boolean grantSelfMade(UUID holder) {
        if (!active || factors.containsKey(holder)) return false;
        factors.put(holder, new Factor(holder, 0, 0, 0));
        if (selfMade < Integer.MAX_VALUE) selfMade++;
        return true;
    }

    boolean recover(UUID holder) { return factors.remove(holder) != null; }

    boolean exposeRoleColor(UUID player) { return active && roleColored.add(player); }
    boolean concealRoleColor(UUID player) { return roleColored.remove(player); }

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

    /** Call before {@link #restoreProgress}. / 须在 {@link #restoreProgress} 之前调用。 */
    void restoreSelfMade(int count) { selfMade = Math.max(0, count); }

    /** Restoring records must not call spread or reconstruct used quota from survivors.
     * 恢复记录不得调用首传，也不得根据幸存因子反推已用额度。 */
    void restoreProgress(int used, boolean unlocked) {
        // Self-made factors were never spread, so they do not raise the floor. / 自成因子并非传播所得，不抬高下限。
        spent = Math.max(Math.max(0, used), factors.size() - selfMade);
        speedUnlocked = unlocked;
    }
}
