package dev.caecorthus.sparkwitch.roles.witch.riftwalker.projectile;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-projectile gate-pass counter (teleports and reflections both count), keyed weakly by the projectile instance so
 * discarded projectiles drop out on their own; nothing is saved. Server thread only. Pure (no Minecraft types).
 * 每个投掷物的穿门计数（传送与反弹都计入），以投掷物实例为弱键，被移除的投掷物会自动释放；不存盘。仅服务端线程。纯逻辑。
 */
public final class RiftGatePassLedger {
    private final int limit;
    private final Map<Object, Integer> passes = new WeakHashMap<>();

    public RiftGatePassLedger(int limit) {
        this.limit = Math.max(0, limit);
    }

    public int limit() {
        return limit;
    }

    public int passes(Object projectile) {
        return projectile == null ? 0 : passes.getOrDefault(projectile, 0);
    }

    public boolean hasPassesLeft(Object projectile) {
        return projectile != null && passes(projectile) < limit;
    }

    /** Counts one pass if any is left; false once the cap is reached. / 尚有余量时计一次并返回 true；达到上限后返回 false。 */
    public boolean tryConsume(Object projectile) {
        if (!hasPassesLeft(projectile)) {
            return false;
        }
        passes.put(projectile, passes(projectile) + 1);
        return true;
    }

    public void clear() {
        passes.clear();
    }
}
