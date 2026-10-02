package dev.caecorthus.sparkwitch.client.riftwalker.swapper;

/**
 * Client side of the Swapper crush (C7; plan §16): the NoellesRoles Swapper screen patch that lets the Swapper pick a
 * listed player who is in spectator mode but alive (only gate occupants are crushed; the server silently cancels every
 * other spectator target). Owned by P9 (together with its client mixin).
 * 交换者夹死的客户端（C7；plan §16）：NoellesRoles 交换界面补丁，让交换者可以点选列表中处于旁观者模式但仍存活的玩家
 * （只有门内的人会触发夹死；服务端对其他旁观者目标静默取消）。归属 P9（连同其客户端 mixin）。
 */
public final class RiftSwapperClient {
    private static boolean initialized;

    private RiftSwapperClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // TODO(P9): client registrations, if any. / TODO(P9)：客户端注册（如需要）。
    }
}
