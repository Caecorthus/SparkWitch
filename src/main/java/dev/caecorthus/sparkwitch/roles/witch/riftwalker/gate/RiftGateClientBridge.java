package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Client-safe indirection for the gate's client tick: common code must never reference client classes, so the client
 * initializer ({@code RiftwalkerClient.init}) installs {@code RiftGateClientEffects::tick} here once. On a dedicated
 * server the hook stays a no-op.
 * 门的客户端 tick 的客户端安全间接层：通用代码不得引用客户端类，因此由客户端初始化（{@code RiftwalkerClient.init}）
 * 在此安装一次 {@code RiftGateClientEffects::tick}。专用服务器上该钩子保持空操作。
 */
public final class RiftGateClientBridge {
    private static final Consumer<RiftGateEntity> NO_OP = gate -> {
    };
    private static volatile Consumer<RiftGateEntity> clientTick = NO_OP;

    private RiftGateClientBridge() {
    }

    /** Client init only. / 仅客户端初始化调用。 */
    public static void installClientTick(Consumer<RiftGateEntity> hook) {
        clientTick = Objects.requireNonNull(hook, "hook");
    }

    static void clientTick(RiftGateEntity gate) {
        clientTick.accept(gate);
    }
}
