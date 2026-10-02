package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import net.minecraft.server.world.ServerWorld;

/**
 * Gate lifecycle (plan §5.2): sweeps at {@code ON_GAME_START}, {@code ON_FINISH_INITIALIZE},
 * {@code ON_FINISH_FINALIZE} and {@code SERVER_STOPPED} over every server world (map voting moves players between
 * dimensions), registry repair for lost entities, and the per-tick entity self-check. Gates survive their placer's
 * death (D4). Owned by P1.
 * 门的生命周期（plan §5.2）：在 {@code ON_GAME_START}、{@code ON_FINISH_INITIALIZE}、{@code ON_FINISH_FINALIZE} 与
 * {@code SERVER_STOPPED} 清扫所有服务端世界（地图投票会在维度间移动玩家），修复丢失实体的登记，以及实体每 tick 的自检。
 * 放置者死亡后门保留（D4）。归属 P1。
 */
public final class RiftGateLifecycle {
    private static boolean registered;

    private RiftGateLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // TODO(P1): register the round-edge sweeps and registry repair. / TODO(P1)：注册对局边界清扫与登记修复。
    }

    /**
     * Server self-check run by {@link RiftGateEntity#tick}: false discards the entity (round not running, match id
     * differs, or the registry no longer lists this gate). G0 stub: always true.
     * 由 {@link RiftGateEntity#tick} 调用的服务端自检：返回 false 时移除实体（对局未进行、对局 id 不符或登记表已不含此门）。
     * G0 存根：恒为 true。
     */
    public static boolean passesSelfCheck(ServerWorld world, RiftGateEntity gate) {
        // TODO(P1): GameWorldComponent running + match id + registry membership. / TODO(P1)：对局、对局 id 与登记校验。
        return true;
    }
}
