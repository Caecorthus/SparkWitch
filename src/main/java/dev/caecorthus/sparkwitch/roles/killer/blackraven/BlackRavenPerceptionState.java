package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Perception progress; only completed snapshots are client-visible.
 * 服务端权威的感知进度；客户端只能看到已完成的身份快照。
 */
public final class BlackRavenPerceptionState {
    private @Nullable UUID matchId;
    private int activeTicks;
    private final Map<UUID, Progress> progressByTarget = new LinkedHashMap<>();
    private final Map<UUID, BlackRavenIdentitySnapshot> snapshots = new LinkedHashMap<>();

    public @Nullable UUID matchId() {
        return matchId;
    }

    public int activeTicks() {
        return activeTicks;
    }

    public boolean isActive() {
        return activeTicks > 0;
    }

    public boolean bindMatch(@Nullable UUID matchId) {
        if (this.matchId == null ? matchId == null : this.matchId.equals(matchId)) {
            return false;
        }
        this.matchId = matchId;
        activeTicks = 0;
        progressByTarget.clear();
        snapshots.clear();
        return true;
    }

    public boolean begin(int durationTicks) {
        int normalized = Math.max(0, durationTicks);
        if (matchId == null || activeTicks > 0 || normalized == 0) {
            return false;
        }
        activeTicks = normalized;
        return true;
    }

    public int decrementActiveTicks() {
        if (activeTicks > 0) {
            activeTicks--;
        }
        return activeTicks;
    }

    public boolean cancelActive() {
        if (activeTicks == 0) {
            return false;
        }
        activeTicks = 0;
        return true;
    }

    /** Returns true only when this call creates the frozen reveal. */
    public boolean accumulate(UUID targetId, int qualifyingTicks, Supplier<BlackRavenIdentitySnapshot> snapshotFactory) {
        if (matchId == null || targetId == null || qualifyingTicks <= 0 || snapshots.containsKey(targetId)) {
            return false;
        }
        Progress progress = progressByTarget.computeIfAbsent(targetId, ignored -> new Progress());
        int combinedTicks = progress.fractionalTicks + qualifyingTicks;
        int gainedPoints = combinedTicks / BlackRavenRules.PERCEPTION_POINT_TICKS;
        progress.fractionalTicks = combinedTicks % BlackRavenRules.PERCEPTION_POINT_TICKS;
        progress.points = Math.min(BlackRavenRules.PERCEPTION_REVEAL_POINTS, progress.points + gainedPoints);
        if (progress.points < BlackRavenRules.PERCEPTION_REVEAL_POINTS) {
            return false;
        }

        BlackRavenIdentitySnapshot snapshot = snapshotFactory.get();
        if (snapshot == null || !targetId.equals(snapshot.playerId())) {
            return false;
        }
        progress.fractionalTicks = 0;
        snapshots.put(targetId, snapshot);
        return true;
    }

    public int points(UUID targetId) {
        Progress progress = progressByTarget.get(targetId);
        return progress == null ? 0 : progress.points;
    }

    public int fractionalTicks(UUID targetId) {
        Progress progress = progressByTarget.get(targetId);
        return progress == null ? 0 : progress.fractionalTicks;
    }

    public @Nullable BlackRavenIdentitySnapshot snapshot(UUID targetId) {
        return snapshots.get(targetId);
    }

    public List<BlackRavenIdentitySnapshot> completedSnapshots() {
        return List.copyOf(snapshots.values());
    }

    public Map<UUID, ProgressView> progress() {
        Map<UUID, ProgressView> copy = new LinkedHashMap<>();
        progressByTarget.forEach((id, progress) -> copy.put(id, new ProgressView(progress.points, progress.fractionalTicks)));
        return Collections.unmodifiableMap(copy);
    }

    public void restore(
            @Nullable UUID matchId,
            int activeTicks,
            Map<UUID, ProgressView> progress,
            List<BlackRavenIdentitySnapshot> completedSnapshots
    ) {
        this.matchId = matchId;
        this.activeTicks = Math.max(0, activeTicks);
        progressByTarget.clear();
        if (progress != null) {
            progress.forEach((id, view) -> progressByTarget.put(id, new Progress(view.points(), view.fractionalTicks())));
        }
        snapshots.clear();
        if (completedSnapshots != null) {
            for (BlackRavenIdentitySnapshot snapshot : new ArrayList<>(completedSnapshots)) {
                snapshots.putIfAbsent(snapshot.playerId(), snapshot);
                progressByTarget.computeIfAbsent(snapshot.playerId(), ignored -> new Progress(
                        BlackRavenRules.PERCEPTION_REVEAL_POINTS,
                        0
                ));
            }
        }
    }

    public void clear() {
        matchId = null;
        activeTicks = 0;
        progressByTarget.clear();
        snapshots.clear();
    }

    public record ProgressView(int points, int fractionalTicks) {
        public ProgressView {
            points = Math.clamp(points, 0, BlackRavenRules.PERCEPTION_REVEAL_POINTS);
            fractionalTicks = Math.clamp(fractionalTicks, 0, BlackRavenRules.PERCEPTION_POINT_TICKS - 1);
        }
    }

    private static final class Progress {
        private int points;
        private int fractionalTicks;

        private Progress() {
        }

        private Progress(int points, int fractionalTicks) {
            ProgressView normalized = new ProgressView(points, fractionalTicks);
            this.points = normalized.points();
            this.fractionalTicks = normalized.fractionalTicks();
        }
    }
}
