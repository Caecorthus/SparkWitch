package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Client only. Owned by WP4: the USEC rifle's client side, called once from {@code UsecClientModule.register()}
 * (right after {@code ScopeClient.register()}). It registers, in this order: the scope profile provider, the
 * {@code usec_magazine}/{@code usec_suppressor} model predicates, the bottom-right ammo HUD, the scope glint world
 * pass, and one end-of-tick hook for the fire latch, the scope intent and the recoil clock (all reset off a SparkWitch
 * server and on disconnect). The mixins in {@code client/mixin/usec/} read the state kept here. Presentation and
 * intent only: the server decides every shot and every scope flag. It never touches the witch skill inventory panel.
 * 仅客户端。归 WP4 所有：USEC 步枪的客户端部分，由 {@code UsecClientModule.register()} 调用一次（紧接在
 * {@code ScopeClient.register()} 之后）。按以下顺序注册：开镜配置提供者、{@code usec_magazine}/{@code usec_suppressor}
 * 模型谓词、右下角弹药 HUD、镜头反光世界绘制，以及一个刻末钩子（开火闩锁、开镜意图与后坐计时；非 SparkWitch 服务器
 * 与断线时全部重置）。{@code client/mixin/usec/} 中的 mixin 读取这里维护的状态。仅为表现与意图：每次开火与每个开镜标记
 * 都由服务端决定。从不触及魔女技能背包面板。
 */
public final class UsecHudClient {
    private static boolean registered;

    private UsecHudClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ScopeClient.registerProvider(UsecScopeProfile::provide);
        UsecModelPredicates.register();
        HudRenderCallback.EVENT.register(UsecAmmoHud::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(UsecScopeGlintRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            UsecRecoil.tick();
            if (!SparkWitchServerConnection.isConfirmedServer()) {
                resetInput();
                return;
            }
            UsecFireInput.tick(client);
            UsecScopeInput.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetInput());
    }

    private static void resetInput() {
        UsecFireInput.reset();
        UsecScopeInput.reset();
        UsecRecoil.reset();
    }
}
