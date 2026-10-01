package dev.caecorthus.sparkwitch.client.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.AbyssListenerEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Abyss Listener client registration: one idempotent {@link #init()} called once from {@code SparkWitchClient}.
 * Presentation only; every gameplay decision stays on the server. The role never uses the top-left
 * {@code gui.sparkwitch.skills} panel.
 * 聆渊者客户端注册：由 {@code SparkWitchClient} 调用一次的幂等 {@link #init()}。仅负责表现，所有玩法判定都在服务端。
 * 本职业从不使用左上角的 {@code gui.sparkwitch.skills} 面板。
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
    }
}
