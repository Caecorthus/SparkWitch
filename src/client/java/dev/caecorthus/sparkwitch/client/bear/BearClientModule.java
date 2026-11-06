package dev.caecorthus.sparkwitch.client.bear;

import dev.caecorthus.sparkwitch.client.bear.ricochet.RysTDazeClient;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.BearEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

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
        dev.caecorthus.sparkwitch.client.bear.helmet.RysTHelmetClient.register();
        // WP3: Rys-T ricochet daze (jolt, double vision, tinnitus, muffle). / WP3：Rys-T 跳弹眩晕（震动、重影、耳鸣、发闷）。
        RysTDazeClient.register();
    }

    /**
     * WP0 stub: the flying smoke round draws its synced item stack like vanilla thrown items; WP1 replaces it with the
     * 3D projectile model. / WP0 桩：飞行中的烟雾榴弹像原版投掷物一样绘制其同步物品；WP1 将替换为 3D 投射物模型。
     */
    public static void registerEntityRenderers() {
        EntityRendererRegistry.register(BearEntities.SMOKE_ROUND, FlyingItemEntityRenderer::new);
    }
}
