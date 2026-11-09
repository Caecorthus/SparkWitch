package dev.caecorthus.sparkwitch.client.bear.hearing;

/**
 * Pure state of the ricochet-daze muffle (D5) behind {@link BearHearingClient#muffle}: world sounds sit at
 * {@code depth} until {@code untilTick}, then ease back to 1 over {@link #RECOVERY_TICKS}. A call while the hold still
 * runs merges, so the longer end and the deeper level win; a call during the recovery or afterwards starts fresh. The
 * level never drops below {@link #MIN_VOLUME}, because vanilla skips a sound whose start volume is 0 (it would then stay
 * silent after the daze) and stops a source re-volumed to 0. Ticks come from the caller's own clock. Not thread-safe:
 * client thread only.
 * 跳弹眩晕发闷（D5）的纯状态，供 {@link BearHearingClient#muffle} 使用：世界声音在 {@code untilTick} 前保持 {@code depth}，之后在
 * {@link #RECOVERY_TICKS} 内缓回 1。保持期间的再次调用会合并，较晚的结束与较深的程度胜出；恢复期间或之后的调用重新开始。
 * 程度从不低于 {@link #MIN_VOLUME}，因为原版会跳过起始音量为 0 的声音（眩晕后它仍会无声），并会停止被重设为 0 的声源。
 * 刻数来自调用方自己的时钟。非线程安全：仅限客户端线程。
 */
public final class BearMuffleEnvelope {
    /** Ease-back window after the hold ends. / 保持结束后的缓回窗口。 */
    public static final int RECOVERY_TICKS = 10;
    /** Floor of the muffle level (never 0, see the class note). / 发闷程度的下限（从不为 0，见类说明）。 */
    public static final float MIN_VOLUME = 0.05F;

    private long untilTick = Long.MIN_VALUE;
    private float depth = 1.0F;

    /**
     * Muffles to {@code volume} for {@code ticks} from {@code now}; a non-positive duration or a volume of 1 or more is
     * ignored. / 从 {@code now} 起在 {@code ticks} 内降到 {@code volume}；非正时长或不小于 1 的音量被忽略。
     */
    public void muffle(long now, int ticks, float volume) {
        if (ticks <= 0 || !(volume < 1.0F)) {
            return;
        }
        float level = Math.max(MIN_VOLUME, volume);
        long end = now + ticks;
        if (now < untilTick) {
            untilTick = Math.max(untilTick, end);
            depth = Math.min(depth, level);
            return;
        }
        untilTick = end;
        depth = level;
    }

    /** Level at {@code now}: 1 when idle. / {@code now} 时的程度：空闲时为 1。 */
    public float factorAt(long now) {
        if (untilTick == Long.MIN_VALUE || depth >= 1.0F) {
            return 1.0F;
        }
        if (now < untilTick) {
            return depth;
        }
        long since = now - untilTick;
        if (since >= RECOVERY_TICKS) {
            return 1.0F;
        }
        float t = (float) since / RECOVERY_TICKS;
        float eased = t * t * (3.0F - 2.0F * t);
        return Math.max(MIN_VOLUME, depth + (1.0F - depth) * eased);
    }

    /** Whether the hold or its recovery still lowers sounds at {@code now}. / {@code now} 时是否仍在压低声音。 */
    public boolean isActive(long now) {
        return factorAt(now) < 1.0F;
    }

    /** Forgets any muffle (disconnect). / 清除发闷（断线时）。 */
    public void reset() {
        untilTick = Long.MIN_VALUE;
        depth = 1.0F;
    }
}
