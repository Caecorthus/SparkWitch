package dev.caecorthus.sparkwitch.roles.civilian.judge;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Round-owned identities survive role changes, death and reconnects.
 * 回合身份按 UUID 保留，不因转职、死亡或重连清零。 */
public final class JudgeRoundState {
    private UUID match;
    private int openingParticipants;
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Long> sentences = new HashMap<>();

    public void begin(UUID match, Collection<UUID> openingRoster) {
        clear();
        this.match = Objects.requireNonNull(match);
        participants.addAll(openingRoster);
        openingParticipants = participants.size();
    }

    public void clear() {
        match = null;
        openingParticipants = 0;
        participants.clear();
        kills.clear();
        sentences.clear();
    }

    public UUID match() {
        return match;
    }

    public boolean active() {
        return match != null;
    }

    public int openingParticipants() {
        return openingParticipants;
    }

    public int requiredKills() {
        return JudgeRules.requiredKills(openingParticipants);
    }

    public boolean hasParticipant(UUID player) {
        return player != null && participants.contains(player);
    }

    public void admit(UUID player) {
        if (active() && player != null) {
            participants.add(player);
        }
    }

    /** The caller must observe a committed death, not an attack or a cancelled attempt.
     * 调用方必须观察到真实死亡提交，不能把攻击或被取消的尝试传入。 */
    public boolean recordKill(UUID actor, UUID victim) {
        if (!active() || !hasParticipant(actor) || !hasParticipant(victim) || actor.equals(victim)) {
            return false;
        }
        kills.compute(actor, (ignored, count) -> count == null ? 1 : count == Integer.MAX_VALUE ? count : count + 1);
        return true;
    }

    public int kills(UUID player) {
        return player == null ? 0 : kills.getOrDefault(player, 0);
    }

    public boolean isSentenced(UUID player, long now) {
        return remainingTicks(player, now) > 0;
    }

    public int remainingTicks(UUID player, long now) {
        if (!active() || player == null) {
            return 0;
        }
        long expiry = sentences.getOrDefault(player, 0L);
        return expiry <= now ? 0 : (int) Math.min(JudgeRules.SENTENCE_TICKS, expiry - now);
    }

    public Verdict judge(UUID target, long now, int balance) {
        if (!active() || !hasParticipant(target)) {
            return Verdict.INVALID;
        }
        if (isSentenced(target, now)) {
            return Verdict.ALREADY_SENTENCED;
        }
        if (balance < JudgeRules.JUDGMENT_COST) {
            return Verdict.INSUFFICIENT_MONEY;
        }
        if (kills(target) < requiredKills()) {
            return Verdict.BELOW_THRESHOLD;
        }
        sentences.put(target, now + JudgeRules.SENTENCE_TICKS);
        return Verdict.SENTENCED;
    }

    public boolean expire(long now) {
        return sentences.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public Map<UUID, Integer> kills() {
        return Map.copyOf(kills);
    }

    public Map<UUID, Long> sentences() {
        return Map.copyOf(sentences);
    }

    public void restore(UUID match, int openingParticipants, Collection<UUID> participants,
                        Map<UUID, Integer> kills, Map<UUID, Long> sentences) {
        clear();
        if (match == null) {
            return;
        }
        this.match = match;
        this.openingParticipants = Math.max(0, openingParticipants);
        this.participants.addAll(participants);
        kills.forEach((player, count) -> {
            if (hasParticipant(player) && count > 0) {
                this.kills.put(player, count);
            }
        });
        sentences.forEach((player, expiry) -> {
            if (hasParticipant(player) && expiry > 0) {
                this.sentences.put(player, expiry);
            }
        });
    }

    public enum Verdict {
        INVALID(false), ALREADY_SENTENCED(false), INSUFFICIENT_MONEY(false),
        BELOW_THRESHOLD(true), SENTENCED(true);

        private final boolean charged;

        Verdict(boolean charged) {
            this.charged = charged;
        }

        public int cost() {
            return charged ? JudgeRules.JUDGMENT_COST : 0;
        }
    }
}
