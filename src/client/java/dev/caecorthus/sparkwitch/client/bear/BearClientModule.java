package dev.caecorthus.sparkwitch.client.bear;

import dev.caecorthus.sparkwitch.client.bear.launcher.M32LauncherClient;
import dev.caecorthus.sparkwitch.client.bear.launcher.SmokeRoundEntityRenderer;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.BearEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * BEAR client registration hub: one line in {@code SparkWitchClient.onInitializeClient}, right before the USEC hub
 * (USEC stays last), plus one in its entity-renderer block. Work packages append their client {@code register()} calls
 * here in a fixed order. Presentation only: the server decides every BEAR rule. The smoke client (spike) keeps its own
 * lines in {@code SparkWitchClient}.
 * BEAR 客户端注册中心：在 {@code SparkWitchClient.onInitializeClient} 中占一行，紧挨 USEC 注册中心之前（USEC 保持最后），
 * 并在其实体渲染器段中另占一行。各工作包按固定顺序在此追加客户端 {@code register()} 调用。纯表现：一切 BEAR 规则由服务端决定。
 * 烟雾客户端（原型）在 {@code SparkWitchClient} 中保留自己的行。
 */
public final class BearClientModule {
    private static boolean registered;

    private BearClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // WP1: M2A1 sight, chamber HUD, fire input, cursor-loading probe, models. / WP1：瞄具、弹膛 HUD、开火输入、装填探针、模型。
        M32LauncherClient.init();
    }

    /**
     * WP1: the flying smoke round draws its standalone 3D projectile model. / WP1：飞行中的烟雾榴弹绘制独立 3D 投射物模型。
     */
    public static void registerEntityRenderers() {
        EntityRendererRegistry.register(BearEntities.SMOKE_ROUND, SmokeRoundEntityRenderer::new);
    }
}
