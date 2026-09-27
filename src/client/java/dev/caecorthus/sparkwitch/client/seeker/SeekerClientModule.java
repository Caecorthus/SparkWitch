package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.client.seeker.console.SeekerConsoleOpener;
import dev.caecorthus.sparkwitch.client.seeker.console.SeekerQuickConnectHandler;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerCarClientDriver;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerCctvOverlay;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerViewFilter;
import dev.caecorthus.sparkwitch.client.seeker.render.SeekerCameraEntityRenderer;
import dev.caecorthus.sparkwitch.client.seeker.render.SeekerCarEntityRenderer;
import dev.caecorthus.sparkwitch.client.seeker.render.SeekerModels;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerEntities;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarCorrectS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Seeker client registration hub: the only Seeker lines in SparkWitchClient call {@link #register()},
 * {@link #registerEntityRenderers()} and {@link #reset()}. Each client work package owns its own {@code register()}.
 * The Seeker never renders in the witch skill panel and does not touch the shared G-key chain.
 * 搜寻者客户端注册中心：SparkWitchClient 中搜寻者相关的行只调用 {@link #register()}、{@link #registerEntityRenderers()}
 * 与 {@link #reset()}。各客户端工作包拥有自己的 {@code register()}。搜寻者从不在魔女技能
 * 面板中渲染，也不改动共享 G 键分发链。
 */
public final class SeekerClientModule {
    private static boolean registered;
    private static boolean renderersRegistered;

    private SeekerClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Handlers run on the client thread (Fabric 1.20.5+ play payload semantics).
        // 处理器在客户端主线程执行（Fabric 1.20.5+ 的 play 数据包语义）。
        ClientPlayNetworking.registerGlobalReceiver(SeekerCarCorrectS2CPacket.ID,
                (payload, context) -> SeekerCarClientDriver.onCorrection(payload));
        SeekerModels.register();
        SeekerRemoteViewClient.register();
        SeekerViewFilter.register();
        SeekerCctvOverlay.register();
        SeekerStatusHud.register();
        SeekerInstinctClientHooks.register();
        SeekerMarkClientHooks.register();
        SeekerTaotieClient.register();
        SeekerConsoleOpener.register();
        SeekerQuickConnectHandler.register();
    }

    public static synchronized void registerEntityRenderers() {
        if (renderersRegistered) {
            return;
        }
        renderersRegistered = true;
        EntityRendererRegistry.register(SeekerEntities.car(), SeekerCarEntityRenderer::new);
        EntityRendererRegistry.register(SeekerEntities.camera(), SeekerCameraEntityRenderer::new);
    }

    /** Connection lifecycle edges: disconnect, login init, login disconnect. / 连接生命周期节点。 */
    public static void reset() {
        SeekerRemoteViewClient.forceExit();
        SeekerViewFilter.reset();
        SeekerTaotieClient.reset();
        SeekerConsoleOpener.reset();
    }
}
