package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure server-side Fiend Moment state: the moment Fiend, an absolute world-tick deadline, and the Wathe match it is
 * bound to. At most one moment exists per world; {@link #start} replaces any previous one.
 * 纯服务端魔人时刻状态：时刻中的魔人、绝对世界 tick 截止时间以及绑定的 Wathe 对局。每个世界至多一个时刻；
 * {@link #start} 会替换之前的时刻。
 */
public final class FiendMomentState {
    private @Nullable UUID fiend;
    private long deadlineTick;
    private @Nullable UUID matchId;

    public void start(UUID fiend, long nowTick, int durationTicks, @Nullable UUID matchId) {
        this.fiend = Objects.requireNonNull(fiend, "fiend");
        this.deadlineTick = nowTick + Math.max(0, durationTicks);
        this.matchId = matchId;
    }

    public void clear() {
        fiend = null;
        deadlineTick = 0L;
        matchId = null;
    }

    public boolean isActive() {
        return fiend != null;
    }

    public boolean isMomentFiend(@Nullable UUID player) {
        return player != null && player.equals(fiend);
    }

    public int remainingTicks(long nowTick) {
        if (fiend == null) {
            return 0;
        }
        long remaining = deadlineTick - nowTick;
        return remaining <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    /** Inclusive deadline: complete on the deadline tick. / 截止 tick 当刻即算完成。 */
    public boolean isComplete(long nowTick) {
        return fiend != null && nowTick >= deadlineTick;
    }

    public @Nullable UUID fiend() {
        return fiend;
    }

    public long deadlineTick() {
        return deadlineTick;
    }

    public @Nullable UUID matchId() {
        return matchId;
    }
}
