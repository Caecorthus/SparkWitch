package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Client wiring of the visible BEAR smoke: the per-world-render decision at {@code WorldRenderEvents.START}, the
 * march and composite at {@code WorldRenderEvents.LAST} (both fire again inside secondary world passes such as the
 * scope lens, which therefore get their own smoke), the noise prefetch, and {@link SmokeRenderer#reset()} on login,
 * disconnect and resource reload. The Fabulous! depth copy runs from {@code SmokeFabulousDepthMixin}.
 * BEAR 可见烟雾的客户端接线：{@code WorldRenderEvents.START} 判断每次世界渲染是否要画，{@code WorldRenderEvents.LAST}
 * 执行步进与合成（两者在瞄具镜内等第二次世界渲染中会再次触发，因此镜内也有自己的烟），预生成噪声，以及登录、断线与资源
 * 重载时的 {@link SmokeRenderer#reset()}。Fabulous! 深度拷贝由 {@code SmokeFabulousDepthMixin} 执行。
 */
public final class SmokeRenderWiring {
    private static final Identifier RELOAD_LISTENER_ID = SparkWitch.id("bear_smoke_renderer");
    private static boolean registered;

    private SmokeRenderWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SmokeNoiseTexture.prefetch();
        WorldRenderEvents.START.register(SmokeRenderer::beginWorldPass);
        WorldRenderEvents.LAST.register(SmokeRenderer::renderWorldPass);
        ClientLoginConnectionEvents.INIT.register((handler, client) -> client.execute(SmokeRenderer::reset));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SmokeRenderer::reset));
        // Also on JOIN: a proxy backend switch re-joins without a disconnect, and play state only arrives after JOIN.
        // JOIN 时同样重置：代理切换后端时不会断线而是重新加入，而 play 阶段的状态只会在 JOIN 之后到达。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(SmokeRenderer::reset));
        // Reload listeners apply on the render thread, where GL objects may be released.
        // 重载监听器在渲染线程执行，可在此安全释放 GL 对象。
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return RELOAD_LISTENER_ID;
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        SmokeRenderer.reset();
                    }
                });
    }
}
