package dev.caecorthus.sparkwitch.roles.witch.riftwalker.net;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet.RiftGateConsoleService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Stable packet contract (G0): registers every Riftwalker payload (the only place that does, called once from
 * {@code SparkWitchPackets}) and routes each C2S receiver to a frozen service method, which performs all validation on
 * the server thread. The client S2C receiver lives in {@code RiftwalkerClient}. Payload ids are deliberately classified
 * for the Control Expert stun list and the Seeker session guard ({@code RiftwalkerPayloadClassificationTest}).
 * 稳定数据包契约（G0）：注册所有隙行者数据包（唯一注册处，由 {@code SparkWitchPackets} 调用一次），并把每个 C2S 接收器
 * 转交给冻结的服务方法，由其在服务端线程完成全部校验。客户端 S2C 接收器位于 {@code RiftwalkerClient}。数据包 id 已针对
 * 控场专家眩晕名单与搜寻者会话拦截有意识地归类（{@code RiftwalkerPayloadClassificationTest}）。
 */
public final class RiftwalkerNetworking {
    /** Every Riftwalker C2S payload id, in registration order. / 所有隙行者 C2S 数据包 id，按注册顺序。 */
    public static final List<Identifier> C2S_IDS = List.of(
            RiftHopC2SPacket.PAYLOAD_ID,
            RiftExitC2SPacket.PAYLOAD_ID,
            RiftGateCloseC2SPacket.PAYLOAD_ID,
            RiftGateConsoleRequestC2SPacket.PAYLOAD_ID);
    /** Every Riftwalker S2C payload id. / 所有隙行者 S2C 数据包 id。 */
    public static final List<Identifier> S2C_IDS = List.of(RiftGateConsoleS2CPacket.PAYLOAD_ID);

    private static boolean registered;

    private RiftwalkerNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(RiftHopC2SPacket.ID, RiftHopC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RiftExitC2SPacket.ID, RiftExitC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RiftGateCloseC2SPacket.ID, RiftGateCloseC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RiftGateConsoleRequestC2SPacket.ID,
                RiftGateConsoleRequestC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(RiftGateConsoleS2CPacket.ID, RiftGateConsoleS2CPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RiftHopC2SPacket.ID,
                (payload, context) -> RiftSessionService.handleHop(
                        context.player(), payload.sessionId(), payload.direction()));
        ServerPlayNetworking.registerGlobalReceiver(RiftExitC2SPacket.ID,
                (payload, context) -> RiftSessionService.handleExit(context.player(), payload.sessionId()));
        ServerPlayNetworking.registerGlobalReceiver(RiftGateCloseC2SPacket.ID,
                (payload, context) -> RiftGateConsoleService.handleClose(
                        context.player(), payload.consoleSessionId(), payload.gateNumber()));
        ServerPlayNetworking.registerGlobalReceiver(RiftGateConsoleRequestC2SPacket.ID,
                (payload, context) -> RiftGateConsoleService.handleRequest(
                        context.player(), payload.consoleSessionId()));
    }

    /** Sends a console snapshot when the client can receive it. / 客户端可接收时发送控制台快照。 */
    public static void sendConsole(ServerPlayerEntity player, RiftGateConsoleS2CPacket packet) {
        if (ServerPlayNetworking.canSend(player, RiftGateConsoleS2CPacket.ID)) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
