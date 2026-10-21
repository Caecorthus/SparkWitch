package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowFeatherService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowFlightService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSkillService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowUseService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Stable packet contract: registers every Raven Form payload (the only place that does) and routes each C2S receiver
 * to its service, which validates everything on the server thread. Runs from the common initializer, so the payload
 * types exist on both sides. The C2S ids are classified on the Control Expert stun, Seeker session, Riftwalker session
 * and Fear deny-lists.
 * 稳定数据包契约：注册所有化鸦数据包（唯一注册处），并把每个 C2S 接收器转交给对应服务，由其在服务端线程完成全部校验。
 * 由通用初始化器调用，因此数据包类型在两端都存在。C2S id 已归入控场专家眩晕、搜寻者会话、隙行者会话与恐惧拦截名单。
 */
public final class BlackRavenCrowNetworking {
    /** Every Raven Form C2S payload id, in registration order. / 所有化鸦 C2S 数据包 id，按注册顺序。 */
    public static final List<Identifier> C2S_IDS = List.of(
            BlackRavenCrowToggleC2SPayload.PAYLOAD_ID,
            BlackRavenCrowMoveC2SPayload.PAYLOAD_ID,
            BlackRavenCrowUseC2SPayload.PAYLOAD_ID,
            BlackRavenCrowDropFeatherC2SPayload.PAYLOAD_ID);
    /** Every Raven Form S2C payload id. / 所有化鸦 S2C 数据包 id。 */
    public static final List<Identifier> S2C_IDS = List.of(BlackRavenCrowCorrectionS2CPayload.PAYLOAD_ID);

    private static boolean registered;

    private BlackRavenCrowNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(BlackRavenCrowToggleC2SPayload.ID, BlackRavenCrowToggleC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlackRavenCrowMoveC2SPayload.ID, BlackRavenCrowMoveC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlackRavenCrowUseC2SPayload.ID, BlackRavenCrowUseC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlackRavenCrowDropFeatherC2SPayload.ID,
                BlackRavenCrowDropFeatherC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlackRavenCrowCorrectionS2CPayload.ID,
                BlackRavenCrowCorrectionS2CPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BlackRavenCrowToggleC2SPayload.ID,
                (payload, context) -> BlackRavenCrowSkillService.toggle(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(BlackRavenCrowMoveC2SPayload.ID,
                (payload, context) -> BlackRavenCrowFlightService.handleMove(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BlackRavenCrowUseC2SPayload.ID,
                (payload, context) -> BlackRavenCrowUseService.handleUse(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BlackRavenCrowDropFeatherC2SPayload.ID,
                (payload, context) -> BlackRavenCrowFeatherService.handleDrop(context.player(), payload));
    }

    /** Owner-only correction; skipped when the client cannot receive it. / 仅发给主人的纠正；客户端无法接收时跳过。 */
    public static void sendCorrection(ServerPlayerEntity owner, BlackRavenCrowCorrectionS2CPayload payload) {
        if (ServerPlayNetworking.canSend(owner, BlackRavenCrowCorrectionS2CPayload.ID)) {
            ServerPlayNetworking.send(owner, payload);
        }
    }
}
