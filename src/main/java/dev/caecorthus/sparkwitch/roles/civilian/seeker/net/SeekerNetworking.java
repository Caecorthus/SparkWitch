package dev.caecorthus.sparkwitch.roles.civilian.seeker.net;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerCameraLookService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerCarMoveService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerCarUseService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie.SeekerTaotieService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Stable packet contract: registers every Seeker payload (the only place that does) and routes each C2S receiver to a
 * frozen service method, which performs all validation on the server thread. Payload ids are classified for the
 * Control Expert stun list, the Fear list and the Seeker session guard (see {@code SeekerPayloadClassificationTest}).
 * 稳定数据包契约：注册所有搜寻者数据包（唯一注册处），并把每个 C2S 接收器转交给冻结的服务方法，
 * 由其在服务端线程完成全部校验。数据包 id 已针对控场专家眩晕名单、恐惧名单与搜寻者会话拦截完成归类。
 */
public final class SeekerNetworking {
    /** Every Seeker C2S payload id, in registration order. / 所有搜寻者 C2S 数据包 id，按注册顺序。 */
    public static final List<Identifier> C2S_IDS = List.of(
            SeekerRemoteOpenC2SPacket.ID.id(),
            SeekerRemoteCloseC2SPacket.ID.id(),
            SeekerCarMoveC2SPacket.ID.id(),
            SeekerCarSwallowC2SPacket.ID.id(),
            SeekerCarRecallC2SPacket.ID.id(),
            SeekerCameraLookC2SPacket.ID.id(),
            SeekerCarUseC2SPacket.ID.id());
    /** Every Seeker S2C payload id. / 所有搜寻者 S2C 数据包 id。 */
    public static final List<Identifier> S2C_IDS = List.of(SeekerCarCorrectS2CPacket.ID.id());

    private static boolean registered;

    private SeekerNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(SeekerRemoteOpenC2SPacket.ID, SeekerRemoteOpenC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SeekerRemoteCloseC2SPacket.ID, SeekerRemoteCloseC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SeekerCarMoveC2SPacket.ID, SeekerCarMoveC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SeekerCarSwallowC2SPacket.ID, SeekerCarSwallowC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SeekerCarRecallC2SPacket.ID, SeekerCarRecallC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SeekerCameraLookC2SPacket.ID, SeekerCameraLookC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SeekerCarUseC2SPacket.ID, SeekerCarUseC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SeekerCarCorrectS2CPacket.ID, SeekerCarCorrectS2CPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SeekerRemoteOpenC2SPacket.ID,
                (payload, context) -> SeekerRemoteSessionService.handleOpen(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SeekerRemoteCloseC2SPacket.ID,
                (payload, context) -> SeekerRemoteSessionService.handleClose(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SeekerCarMoveC2SPacket.ID,
                (payload, context) -> SeekerCarMoveService.handleMove(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SeekerCarSwallowC2SPacket.ID,
                (payload, context) -> SeekerTaotieService.handleSwallow(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SeekerCarRecallC2SPacket.ID,
                (payload, context) -> SeekerDeviceService.remoteRecallCar(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(SeekerCameraLookC2SPacket.ID,
                (payload, context) -> SeekerCameraLookService.handleLook(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(SeekerCarUseC2SPacket.ID,
                (payload, context) -> SeekerCarUseService.handleUse(context.player(), payload));
    }

    /** Owner-only correction; skipped when the client cannot receive it. / 仅发给拥有者的纠正；客户端无法接收时跳过。 */
    public static void sendCorrection(ServerPlayerEntity owner, SeekerCarCorrectS2CPacket packet) {
        if (ServerPlayNetworking.canSend(owner, SeekerCarCorrectS2CPacket.ID)) {
            ServerPlayNetworking.send(owner, packet);
        }
    }
}
