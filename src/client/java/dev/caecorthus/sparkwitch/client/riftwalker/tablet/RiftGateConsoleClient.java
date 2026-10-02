package dev.caecorthus.sparkwitch.client.riftwalker.tablet;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleS2CPacket;

/**
 * Client side of the Rift Gate console (D12, D16, C9; plan §15): a Riftwalker-owned {@code UseItemCallback} copy of the
 * Seeker console opener that intercepts {@code sparkstrength:tablet} (registry id only) for a live Riftwalker on a
 * confirmed SparkWitch server and sends {@code sparkwitch:rift_gate_console_request(0)}; the "Rift Gates" screen
 * (number, distance + arrow, occupant names, two-step close, "Witch network" button back to the SparkStrength tablet);
 * polls while open. Never generalise or edit the Seeker opener. Owned by P8.
 * 裂隙门控制台的客户端（D12、D16、C9；plan §15）：复制搜寻者控制台拦截器的隙行者自有 {@code UseItemCallback}，在已确认的
 * SparkWitch 服务器上为存活的隙行者拦截 {@code sparkstrength:tablet}（仅按注册 id），并发送
 * {@code sparkwitch:rift_gate_console_request(0)}；「裂隙门」界面（编号、距离与方向箭头、门内玩家名、两步关门、
 * 切回 SparkStrength 平板的「魔女网络」按钮）；打开期间轮询。不得泛化或修改搜寻者拦截器。归属 P8。
 */
public final class RiftGateConsoleClient {
    private static boolean initialized;

    private RiftGateConsoleClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // TODO(P8): tablet opener (UseItemCallback) and screen lifecycle. / TODO(P8)：平板拦截器与界面生命周期。
    }

    /**
     * Every {@code sparkwitch:rift_gate_console} snapshot arrives here on the client thread: open the console (or
     * refresh it when already open for the same session); {@code consoleSessionId == CLOSED} closes it. G0 stub: no-op.
     * 每份 {@code sparkwitch:rift_gate_console} 快照都在客户端线程到达这里：打开控制台（同一会话已打开时刷新）；
     * {@code consoleSessionId == CLOSED} 时关闭。G0 存根：空操作。
     */
    public static void open(RiftGateConsoleS2CPacket snapshot) {
        // TODO(P8)
    }
}
