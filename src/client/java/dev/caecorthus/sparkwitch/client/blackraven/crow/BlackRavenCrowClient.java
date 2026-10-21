package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenPerceptionScreenEffects;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntities;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowToggleC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Client entry of Raven Form (化鸦): the Black Raven's key-2 handler (sends {@code sparkwitch:black_raven_crow_toggle};
 * the server checks every start condition and answers refusals on the action bar), the crow renderer (model B), the
 * owner's pilot view and the boundary indicator. The HUD line lives in {@code BlackRavenHudRenderer}; the body, name-tag,
 * instinct and Blind gates read {@link BlackRavenCrowBodyVisibility} and {@code BlackRavenCrowForm}.
 * 化鸦的客户端入口：黑羽鸦的技能键 2 处理器（发送 black_raven_crow_toggle；所有开始条件由服务端校验，拒绝时在动作栏回复）、乌鸦渲染器
 * （模型 B）、本人的操控画面与边界提示。HUD 行位于 BlackRavenHudRenderer；本体、名牌、本能与盲人门槛读取
 * {@link BlackRavenCrowBodyVisibility} 与 BlackRavenCrowForm。
 */
public final class BlackRavenCrowClient {
    private static boolean registered;

    private BlackRavenCrowClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // The key-2 slot of the RAW role (SecondaryAbilityController dispatches by getRole), so it also answers while
        // disguised; the server refuses a disguised start. / 按原始职业分发的技能键 2，伪装期间也会响应；伪装中开始由服务端拒绝。
        SecondaryAbilityRegistry.register(BlackRavenRules.ROLE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player != null && ClientPlayNetworking.canSend(BlackRavenCrowToggleC2SPayload.ID)) {
                    ClientPlayNetworking.send(new BlackRavenCrowToggleC2SPayload());
                }
            }

            @Override
            public void reset() {
                // Role change or controller reset: drop the Perception post-processor like the old key-2 handler.
                // 职业变化或控制器重置：与旧的技能键 2 处理器一样释放感知后处理。
                BlackRavenPerceptionScreenEffects.close();
            }
        });
        EntityModelLayerRegistry.registerModelLayer(BlackRavenCrowModel.LAYER, BlackRavenCrowModel::getTexturedModelData);
        EntityRendererRegistry.register(BlackRavenCrowEntities.crow(), BlackRavenCrowRenderer::new);
        BlackRavenCrowPilotClient.register();
        HudRenderCallback.EVENT.register(BlackRavenCrowBoundaryOverlay::render);
    }
}
