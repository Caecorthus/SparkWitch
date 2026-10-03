package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure ledger of owned harmless-burn windows: player UUID → exclusive server-tick deadline. A re-ignite only ever
 * extends a window. Server thread only; never saved or synced.
 * 自有无伤燃烧窗口的纯登记表：玩家 UUID → 截止服务端刻（不含）。再次点燃只会延长窗口。
 * 仅限服务端线程；不存盘、不同步。
 */
final class PotionShellBurnWindow {
    private final Map<UUID, Long> deadlines = new HashMap<>();

    void open(UUID player, long now, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        deadlines.merge(player, now + ticks, Math::max);
    }

    boolean isActive(UUID player, long now) {
        Long deadline = player == null ? null : deadlines.get(player);
        return deadline != null && now < deadline;
    }

    /** Forgets the window; true when one was recorded. / 遗忘窗口；若曾登记则返回 true。 */
    boolean close(UUID player) {
        return player != null && deadlines.remove(player) != null;
    }

    void expire(long now) {
        deadlines.values().removeIf(deadline -> now >= deadline);
    }

    /** Forgets every window and returns their owners. / 遗忘全部窗口并返回其所有者。 */
    Set<UUID> drain() {
        Set<UUID> owners = new HashSet<>(deadlines.keySet());
        deadlines.clear();
        return owners;
    }

    boolean isEmpty() {
        return deadlines.isEmpty();
    }

    void clear() {
        deadlines.clear();
    }

    /** A longer burn from another source is never shortened. / 其他来源更长的燃烧绝不缩短。 */
    static int fireTicksAfterIgnite(int currentFireTicks, int ticks) {
        return Math.max(currentFireTicks, ticks);
    }

    /** Only fire damage, and only inside an owned window, is vetoed. / 仅在自有窗口内否决火焰伤害。 */
    static boolean vetoes(boolean fireDamage, boolean windowActive) {
        return fireDamage && windowActive;
    }
}
