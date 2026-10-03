package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

/**
 * Data-only Deep Dark Zone exposure counter (remaining ticks, never absolute server ticks).
 * Sync rule: the owner is synced only when exposure starts (0 to positive) and when it ends (back to 0). Refreshes
 * while already exposed are not synced, so the client prediction stops at one tick and only the server's zero sync
 * ends the exposure on the client; the pseudo task and the drain prediction therefore never flicker.
 * 仅保存深暗领域暴露计时（剩余刻数，从不保存服务端绝对时刻）。
 * 同步规则：只在暴露开始（0 变为正数）与结束（回到 0）时同步给拥有者。已暴露期间的刷新不同步，因此客户端预测停在 1 刻，
 * 只有服务端同步的零值才会在客户端结束暴露；临时任务行与理智下降预测因此不会闪烁。
 */
public final class AbyssZoneExposureState {
    private int exposureTicks;

    public int exposureTicks() {
        return exposureTicks;
    }

    public boolean isExposed() {
        return exposureTicks > 0;
    }

    /**
     * Max semantics; returns true only when exposure starts and the owner must be synced.
     * 取最大值；仅当暴露开始、需要同步给拥有者时返回 true。
     */
    public boolean expose(int ticks) {
        int next = Math.max(exposureTicks, ticks);
        if (next == exposureTicks) {
            return false;
        }
        boolean started = exposureTicks == 0;
        exposureTicks = next;
        return started;
    }

    /**
     * Server countdown; returns true when exposure just ended and the owner must be synced.
     * 服务端倒计时；暴露刚结束、需要同步给拥有者时返回 true。
     */
    public boolean serverTick() {
        if (exposureTicks <= 0) {
            return false;
        }
        exposureTicks--;
        return exposureTicks == 0;
    }

    /**
     * Client prediction stops at one tick: only the server's zero sync ends the exposure.
     * 客户端预测停在 1 刻：只有服务端同步的零值才会结束暴露。
     */
    public void clientTick() {
        if (exposureTicks > 1) {
            exposureTicks--;
        }
    }

    /** Returns whether anything changed (and the owner must be synced). / 返回是否有改变（需要同步给拥有者）。 */
    public boolean clear() {
        if (exposureTicks == 0) {
            return false;
        }
        exposureTicks = 0;
        return true;
    }

    public void restore(int exposureTicks) {
        this.exposureTicks = Math.max(0, exposureTicks);
    }
}
