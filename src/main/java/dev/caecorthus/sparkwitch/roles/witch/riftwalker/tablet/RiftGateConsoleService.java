package dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server side of the Rift Gate console in the SparkStrength tablet (D12, D16, C9; plan §15, research 05 option c): only a
 * live Riftwalker holding {@code sparkstrength:tablet} anywhere (registry id only; fail closed without SparkStrength)
 * may open it. Snapshots go out as {@code sparkwitch:rift_gate_console} (S2C); the close request is re-validated here
 * (round ACTIVE, exact role, alive, not inside a gate, not stunned/controlled, number exists this round, throttle) and
 * then calls {@code RiftGateRegistry.close(world, number, RiftGateCloseReason.CONSOLE)}. Owned by P8.
 * SparkStrength 平板中的裂隙门控制台的服务端（D12、D16、C9；plan §15，调研 05 方案 c）：只有在任意位置持有
 * {@code sparkstrength:tablet}（仅按注册 id；未装 SparkStrength 时失败关闭）的存活隙行者才能打开。快照以
 * {@code sparkwitch:rift_gate_console}（S2C）下发；关门请求在此重新校验（对局 ACTIVE、精确职业、存活、不在门内、
 * 未被眩晕/控制、编号属于本局、节流），随后调用 {@code RiftGateRegistry.close(world, number, RiftGateCloseReason.CONSOLE)}。
 * 归属 P8。
 */
public final class RiftGateConsoleService {
    private static boolean registered;

    private RiftGateConsoleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // TODO(P8): per-player console sessions / throttles cleanup. / TODO(P8)：每名玩家的控制台会话与节流清理。
    }

    /**
     * {@code sparkwitch:rift_gate_console_request}: {@code consoleSessionId == 0} asks to open a new console
     * ({@link #tryOpen}); any other value is a refresh poll for that console session (answered with a fresh snapshot
     * while it is still valid). G0 stub: no-op.
     * {@code sparkwitch:rift_gate_console_request}：{@code consoleSessionId == 0} 表示请求打开新控制台（{@link #tryOpen}）；
     * 其他值为该控制台会话的刷新轮询（会话仍有效时回复最新快照）。G0 存根：空操作。
     */
    public static void handleRequest(ServerPlayerEntity player, int consoleSessionId) {
        // TODO(P8)
    }

    /**
     * Validates and opens a console session, sending the first snapshot; returns whether it opened. G0 stub: false.
     * 校验并打开控制台会话，发送第一份快照；返回是否打开。G0 存根：false。
     */
    public static boolean tryOpen(ServerPlayerEntity player) {
        // TODO(P8)
        return false;
    }

    /** {@code sparkwitch:rift_gate_close}: two-step confirm happens on the client. G0 stub: no-op. / 关门请求（二次确认在客户端）。 */
    public static void handleClose(ServerPlayerEntity player, int consoleSessionId, int gateNumber) {
        // TODO(P8)
    }
}
