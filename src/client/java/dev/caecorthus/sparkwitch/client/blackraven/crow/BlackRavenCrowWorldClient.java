package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenFeatherRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client registration of Raven Form's world content (crow-world): the Homing Feather marker renderer and the
 * {@code sparkwitch:black_feather} landing particle. The landing hold's input lock needs no client code of its own: it
 * rides the Control Expert stun lock ({@code BlackRavenCrowLandingHold}).
 * 化鸦世界内容（crow-world）的客户端注册：归羽标记渲染器与 {@code sparkwitch:black_feather} 落地粒子。落地定身的输入锁无需自己的客户端代码：
 * 它沿用控场专家眩晕锁（{@code BlackRavenCrowLandingHold}）。
 */
public final class BlackRavenCrowWorldClient {
    private static boolean registered;

    private BlackRavenCrowWorldClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        EntityRendererRegistry.register(BlackRavenFeatherRegistry.featherMarker(),
                BlackRavenFeatherEntityRenderer::new);
        ParticleFactoryRegistry.getInstance().register(BlackRavenFeatherRegistry.blackFeather(),
                BlackFeatherParticle.Factory::new);
    }
}
