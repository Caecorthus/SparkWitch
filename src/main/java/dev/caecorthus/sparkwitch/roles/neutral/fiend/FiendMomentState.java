package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure server-side Fiend Moment state: the moment Fiend, an absolute world-tick deadline, the Wathe match it is
 * bound to, and the absolute tick at which Dash is ready again (ready from the start; the cooldown dies with the moment).
 * At most one moment exists per world; {@link #start} replaces any previous one.
 * 纯服务端魔人时刻状态：时刻中的魔人、绝对世界 tick 截止时间、绑定的 Wathe 对局，以及疾驰再次就绪的绝对 tick（开始时
 * 即就绪；冷却随时刻一同结束）。每个世界至多一个时刻；{@link #start} 会替换之前的时刻。
 */
public final class FiendMomentState {
    private @Nullable UUID fiend;
    private long deadlineTick;
    private @Nullable UUID matchId;
    private long dashReadyTick;

    public void start(UUID fiend, long nowTick, int durationTicks, @Nullable UUID matchId) {
        this.fiend = Objects.requireNonNull(fiend, "fiend");
        this.deadlineTick = nowTick + Math.max(0, durationTicks);
        this.matchId = matchId;
        this.dashReadyTick = nowTick;
    }

    public void clear() {
        fiend = null;
        deadlineTick = 0L;
        matchId = null;
        dashReadyTick = 0L;
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

    /** Ready on the ready tick; never ready without a moment. / 就绪刻当刻即就绪；无时刻时永不就绪。 */
    public boolean isDashReady(long nowTick) {
        return fiend != null && nowTick >= dashReadyTick;
    }

    /** Ticks until Dash is ready, 0 when ready or without a moment. / 距疾驰就绪的 tick 数；已就绪或无时刻时为 0。 */
    public int dashCooldownRemaining(long nowTick) {
        if (fiend == null) {
            return 0;
        }
        long remaining = dashReadyTick - nowTick;
        return remaining <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    public long dashReadyTick() {
        return dashReadyTick;
    }

    /** A no-op without a moment. / 无时刻时无效果。 */
    public void setDashReadyTick(long readyTick) {
        if (fiend != null) {
            dashReadyTick = readyTick;
        }
    }
}
