package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Stores one Perfumer's private round state and a player's active cologne timer.
 * 保存单个调香师的私有本局状态，以及玩家身上的古龙水计时。
 */
public final class PerfumerState {
    private final LinkedHashSet<UUID> markedTargets = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> bloodyTargets = new LinkedHashSet<>();
    private int cologneTicks;
    private int colognePulseTicks;

    public boolean mark(UUID targetUuid) {
        return !bloodyTargets.contains(targetUuid) && markedTargets.add(targetUuid);
    }

    public boolean promoteToBloody(UUID targetUuid) {
        if (!markedTargets.remove(targetUuid)) {
            return false;
        }
        bloodyTargets.add(targetUuid);
        return true;
    }

    public boolean isMarked(UUID targetUuid) {
        return markedTargets.contains(targetUuid);
    }

    public boolean isBloody(UUID targetUuid) {
        return bloodyTargets.contains(targetUuid);
    }

    public boolean removeTarget(UUID targetUuid) {
        boolean removed = markedTargets.remove(targetUuid);
        return bloodyTargets.remove(targetUuid) || removed;
    }

    public Set<UUID> markedTargets() {
        return Set.copyOf(markedTargets);
    }

    public Set<UUID> bloodyTargets() {
        return Set.copyOf(bloodyTargets);
    }

    public void startCologne() {
        cologneTicks = PerfumerRules.COLOGNE_DURATION_TICKS;
        colognePulseTicks = PerfumerRules.COLOGNE_PULSE_INTERVAL_TICKS;
    }

    public int cologneTicks() {
        return cologneTicks;
    }

    public int colognePulseTicks() {
        return colognePulseTicks;
    }

    public boolean tickCologne() {
        if (cologneTicks <= 0) {
            return false;
        }
        cologneTicks--;
        colognePulseTicks--;
        if (colognePulseTicks > 0) {
            return false;
        }
        colognePulseTicks = cologneTicks > 0 ? PerfumerRules.COLOGNE_PULSE_INTERVAL_TICKS : 0;
        return true;
    }

    public boolean stopCologne() {
        boolean changed = cologneTicks > 0 || colognePulseTicks > 0;
        cologneTicks = 0;
        colognePulseTicks = 0;
        return changed;
    }

    /**
     * Restores persisted owner-only state while keeping the two target sets disjoint.
     * 恢复仅对持有者同步的持久化状态，并保证标记与血腥目标集合互斥。
     */
    public void restore(
            Collection<UUID> marked,
            Collection<UUID> bloody,
            int cologneTicks,
            int colognePulseTicks
    ) {
        clear();
        if (marked != null) {
            marked.stream().filter(java.util.Objects::nonNull).forEach(markedTargets::add);
        }
        if (bloody != null) {
            bloody.stream().filter(java.util.Objects::nonNull).forEach(bloodyTargets::add);
            markedTargets.removeAll(bloodyTargets);
        }
        this.cologneTicks = Math.clamp(cologneTicks, 0, PerfumerRules.COLOGNE_DURATION_TICKS);
        if (this.cologneTicks > 0) {
            int maxPulseTicks = Math.min(this.cologneTicks, PerfumerRules.COLOGNE_PULSE_INTERVAL_TICKS);
            this.colognePulseTicks = Math.clamp(colognePulseTicks, 1, maxPulseTicks);
        }
    }

    public void clear() {
        markedTargets.clear();
        bloodyTargets.clear();
        stopCologne();
    }
}
