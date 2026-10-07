package dev.caecorthus.sparkwitch.client.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.AbyssListenerEntities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Abyss Listener client registration: one idempotent {@link #init()} called once from {@code SparkWitchClient}.
 * Presentation only; every gameplay decision stays on the server. The role's own panel skill (Warden's Shriek, D13)
 * is drawn by the shared {@code gui.sparkwitch.skills} panel, not here.
 * 聆渊者客户端注册：由 {@code SparkWitchClient} 调用一次的幂等 {@link #init()}。仅负责表现，所有玩法判定都在服务端。
 * 本职业自有的面板技能（监守之啸，D13）由共享的 {@code gui.sparkwitch.skills} 面板绘制，不在此处。
 */
public final class AbyssListenerClient {
    private static boolean initialized;

    private AbyssListenerClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // The thrown flask renders its synced item stack like vanilla thrown items.
        // 投出的孢瓶与原版投掷物一样渲染其同步的物品。
        EntityRendererRegistry.register(AbyssListenerEntities.deepDarkSporeFlask(), FlyingItemEntityRenderer::new);
        // The pseudo task line's fade is static: settled every HUD frame, dropped on every disconnect and join.
        // 临时任务行的淡入淡出是静态状态：每个 HUD 帧结算一次，每次断开或加入连接时丢弃。
        HudRenderCallback.EVENT.register((context, tickCounter) ->
                AbyssZoneExposureTaskLine.settleFrame(MinecraftClient.getInstance().player));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AbyssZoneExposureTaskLine.reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> AbyssZoneExposureTaskLine.reset());
    }
}
