package dev.caecorthus.sparkwitch.client.bear.smoke.shaderpack;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.blind.render.BlindShaderPackCheck;
import dev.caecorthus.sparkwitch.client.render.entity.SecondaryWorldPass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Client wiring of BEAR smoke fairness under an Iris shader pack (everything here is a no-op without a pack in use):
 * <ul>
 *   <li>{@code WorldRenderEvents.START} of each main-view world render latches whether the after-final-pass smoke
 *   ({@link SmokeShaderPackBridge}, driven by {@code SmokeIrisFinalPassMixin}) worked in the previous frame;</li>
 *   <li>{@code WorldRenderEvents.AFTER_ENTITIES}: while it did not, {@link SmokeBillboards} draws the smoke through the
 *   pack's own entity program (fail closed);</li>
 *   <li>end of each client tick: {@link SmokeEntityOcclusion} recomputes which entities are behind smoke, for the
 *   {@code hasOutline} veto;</li>
 *   <li>login, disconnect and resource reload reset all of it.</li>
 * </ul>
 * Client-only presentation; reads {@code SmokeClientState} and {@code SmokeVolumes} only.
 * Iris 光影包下 BEAR 烟雾公平性的客户端接线（未使用光影包时这里全部不做任何事）：每次主画面世界渲染的
 * {@code WorldRenderEvents.START} 锁存上一帧“最终处理之后的烟雾”（{@link SmokeShaderPackBridge}，由
 * {@code SmokeIrisFinalPassMixin} 驱动）是否正常；{@code WorldRenderEvents.AFTER_ENTITIES}：不正常时由 {@link SmokeBillboards}
 * 通过光影包自己的实体程序画烟（失败即遮挡）；每个客户端 tick 结束时 {@link SmokeEntityOcclusion} 重新计算哪些实体在烟后，
 * 供 {@code hasOutline} 否决使用；登录、断线与资源重载时全部重置。纯客户端展示；只读取 {@code SmokeClientState} 与
 * {@code SmokeVolumes}。
 */
public final class SmokeShaderPackWiring {
    private static final Identifier RELOAD_LISTENER_ID = SparkWitch.id("bear_smoke_shader_pack");
    private static boolean registered;
    private static boolean shaderPack;

    private SmokeShaderPackWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        WorldRenderEvents.START.register(context -> {
            if (!SecondaryWorldPass.isActive()) {
                shaderPack = BlindShaderPackCheck.isShaderPackInUse();
                SmokeShaderPackBridge.onMainWorldPassStart();
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (shaderPack && !SmokeShaderPackBridge.primaryHealthy() && !SmokeClientState.isEmpty()) {
                SmokeBillboards.render(context);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> SmokeEntityOcclusion.tick(client,
                shaderPack, !SmokeShaderPackBridge.primaryHealthy()));
        ClientLoginConnectionEvents.INIT.register((handler, client) -> client.execute(SmokeShaderPackWiring::reset));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SmokeShaderPackWiring::reset));
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return RELOAD_LISTENER_ID;
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        reset();
                    }
                });
    }

    private static void reset() {
        SmokeShaderPackBridge.reset();
        SmokeBillboards.reset();
        SmokeEntityOcclusion.clear();
    }
}
