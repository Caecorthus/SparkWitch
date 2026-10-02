package dev.caecorthus.sparkwitch.client.riftwalker.tablet;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleRequestC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Hand;

/**
 * Client side of the Rift Gate console (D12, D16, C9; plan §15): {@link #init()} registers the Riftwalker-owned tablet
 * opener ({@link RiftGateConsoleOpener}, a copy of the Seeker console opener) and the connection resets; {@link #open}
 * dispatches every {@code sparkwitch:rift_gate_console} snapshot (open for an outstanding request, refresh the open
 * console of the same session, CLOSED closes) through {@link RiftGateConsoleClientRules#snapshotAction}. The screen
 * ({@link RiftGateConsoleScreen}) polls while open. Presentation only; never generalise or edit the Seeker opener.
 * Owned by P8.
 * 裂隙门控制台的客户端（D12、D16、C9；plan §15）：{@link #init()} 注册隙行者自有的平板拦截器
 * （{@link RiftGateConsoleOpener}，搜寻者控制台拦截器的副本）与连接重置；{@link #open} 通过
 * {@link RiftGateConsoleClientRules#snapshotAction} 分派每份 {@code sparkwitch:rift_gate_console} 快照（为未完成的请求打开、
 * 刷新同一会话的已打开控制台、CLOSED 关闭）。界面（{@link RiftGateConsoleScreen}）打开期间轮询。仅负责展示；不得泛化或
 * 修改搜寻者拦截器。归属 P8。
 */
public final class RiftGateConsoleClient {
    private static boolean initialized;
    /** Client tick of the outstanding open request, or -1. / 未完成打开请求的客户端刻，或 -1。 */
    private static volatile long openRequestTick = -1L;
    private static volatile Hand openRequestHand = Hand.MAIN_HAND;
    /**
     * Highest console session id seen on this connection; server ids strictly increase, so a console opens at most once
     * per session.
     * 本次连接见过的最大控制台会话 id；服务端 id 严格递增，因此每个会话最多打开一次控制台。
     */
    private static volatile int sessionFloor;

    private RiftGateConsoleClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RiftGateConsoleOpener.register();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /**
     * Every {@code sparkwitch:rift_gate_console} snapshot arrives here on the client thread: open the console for an
     * outstanding open request (or refresh it when already open for the same session); {@code consoleSessionId ==
     * CLOSED} closes it or answers the open request.
     * 每份 {@code sparkwitch:rift_gate_console} 快照都在客户端线程到达这里：为未完成的打开请求打开控制台（同一会话已打开时
     * 刷新）；{@code consoleSessionId == CLOSED} 时关闭控制台或应答打开请求。
     */
    public static void open(RiftGateConsoleS2CPacket snapshot) {
        if (snapshot == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Screen current = client.currentScreen;
        RiftGateConsoleScreen console = current instanceof RiftGateConsoleScreen screen ? screen : null;
        long now = RiftGateConsoleOpener.clientTicks();
        int session = snapshot.consoleSessionId();
        RiftGateConsoleClientRules.SnapshotAction action = RiftGateConsoleClientRules.snapshotAction(session,
                console != null, console == null ? RiftGateConsoleS2CPacket.CLOSED : console.sessionId(),
                isOpenPending(now), current != null && console == null, sessionFloor);
        if (session != RiftGateConsoleS2CPacket.CLOSED) {
            sessionFloor = Math.max(sessionFloor, session);
        }
        switch (action) {
            case OPEN -> {
                openRequestTick = -1L;
                if (client.player != null) {
                    client.setScreen(new RiftGateConsoleScreen(openRequestHand, snapshot));
                }
            }
            case REFRESH -> console.applySnapshot(snapshot);
            case CLOSE -> {
                openRequestTick = -1L;
                console.close();
            }
            case DROP_PENDING -> openRequestTick = -1L;
            case IGNORE -> {
            }
        }
    }

    /**
     * Sends {@code rift_gate_console_request(OPEN)} for the opener and remembers it as outstanding.
     * 为拦截器发送 {@code rift_gate_console_request(OPEN)}，并记为未完成请求。
     */
    static void requestOpen(Hand hand, long now) {
        if (!ClientPlayNetworking.canSend(RiftGateConsoleRequestC2SPacket.ID)) {
            return;
        }
        openRequestHand = hand;
        openRequestTick = now;
        ClientPlayNetworking.send(new RiftGateConsoleRequestC2SPacket(RiftGateConsoleRequestC2SPacket.OPEN));
    }

    static boolean isOpenPending(long now) {
        return RiftGateConsoleClientRules.openPending(now, openRequestTick);
    }

    /** Connection edges; may run off the client thread, so it only clears fields. / 连接节点；只清字段。 */
    static void reset() {
        openRequestTick = -1L;
        openRequestHand = Hand.MAIN_HAND;
        sessionFloor = 0;
        RiftGateConsoleOpener.reset();
    }
}
