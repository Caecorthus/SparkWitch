package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;

/**
 * Minimal client registration for Raven Form so the crow entity type can spawn on a client: a placeholder renderer that
 * draws nothing. crow-client replaces it with the real crow renderer and adds the camera, flight input, body freeze,
 * crow hotbar, boundary indicator, HUD and the correction receiver.
 * 化鸦的最小客户端注册，使乌鸦实体类型能在客户端生成：一个不绘制任何内容的占位渲染器。crow-client 将以真正的乌鸦渲染器替换它，
 * 并加入镜头、飞行输入、本体冻结、乌鸦快捷栏、边界提示、HUD 与纠正包接收器。
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
        EntityRendererRegistry.register(BlackRavenCrowEntities.crow(), EmptyEntityRenderer::new);
    }
}
