package dev.caecorthus.sparkwitch.client.riftwalker;

import dev.caecorthus.sparkwitch.client.riftwalker.gate.RiftGateClientEffects;
import dev.caecorthus.sparkwitch.client.riftwalker.gate.RiftGateEntityRenderer;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import dev.caecorthus.sparkwitch.client.riftwalker.swapper.RiftSwapperClient;
import dev.caecorthus.sparkwitch.client.riftwalker.tablet.RiftGateConsoleClient;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateClientBridge;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntities;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Riftwalker client registration (frozen G0): one idempotent {@link #init()} called once from {@code SparkWitchClient}.
 * It owns every shared client registration (gate renderer, the gate client-tick bridge, the console S2C receiver) and
 * calls each client work package's own {@code init()}, so no WP edits {@code SparkWitchClient}. Presentation only; every
 * gameplay decision stays on the server.
 * 隙行者客户端注册（G0 冻结）：由 {@code SparkWitchClient} 调用一次的幂等 {@link #init()}。它负责所有共享的客户端注册
 * （门渲染器、门客户端 tick 桥接、控制台 S2C 接收器），并调用各客户端工作包自己的 {@code init()}，因此任何工作包都无需
 * 修改 {@code SparkWitchClient}。仅负责表现，所有玩法判定都在服务端。
 */
public final class RiftwalkerClient {
    private static boolean initialized;

    private RiftwalkerClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        EntityRendererRegistry.register(RiftGateEntities.riftGate(), RiftGateEntityRenderer::new);
        RiftGateClientBridge.installClientTick(RiftGateClientEffects::tick);
        // Runs on the client thread (Fabric 1.21 play receivers). / 在客户端线程执行（Fabric 1.21 play 接收器）。
        ClientPlayNetworking.registerGlobalReceiver(RiftGateConsoleS2CPacket.ID,
                (payload, context) -> RiftGateConsoleClient.open(payload));
        RiftSessionClient.init();
        RiftGateClientEffects.init();
        RiftGateConsoleClient.init();
        RiftSwapperClient.init();
    }
}
