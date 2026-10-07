package dev.caecorthus.sparkwitch.client.blind;

import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGateWiring;
import dev.caecorthus.sparkwitch.client.blind.kit.BlindKitClientWiring;
import dev.caecorthus.sparkwitch.client.blind.render.BlindRenderWiring;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Blind client module: receives {@code sparkwitch:blind_pulse} into {@link BlindPerceptionClientState}, forgets that
 * state whenever {@link BlindView#isActive} flips (role change, death, round end) or the world changes, and calls each
 * work package's client wiring. Role-owned presentation; never part of the witch skill inventory panel.
 * 盲人客户端模块：把 {@code sparkwitch:blind_pulse} 写入 {@link BlindPerceptionClientState}；当 {@link BlindView#isActive}
 * 翻转（换职业、死亡、回合结束）或世界变化时清空该状态；并调用各工作包的客户端接线。属于职业自有展示，从不属于魔女技能背包面板。
 */
public final class BlindClient {
    private static boolean registered;
    private static boolean wasActive;
    private static @Nullable ClientWorld lastWorld;

    private BlindClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(BlindPulseS2CPayload.ID, (payload, context) ->
                context.client().execute(() -> receive(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(BlindClient::tick);
        BlindRenderWiring.register();
        BlindClientGateWiring.register();
        BlindKitClientWiring.register();
    }

    /** Connection edges (login, disconnect). / 连接生命周期节点（登录、断开）。 */
    public static void reset() {
        BlindPerceptionClientState.get().reset();
        wasActive = false;
        lastWorld = null;
    }

    private static void receive(MinecraftClient client, BlindPulseS2CPayload payload) {
        if (client.player == null || !BlindView.isActive(client)) {
            return;
        }
        BlindPerceptionClientState.get().accept(payload, Util.getMeasuringTimeNano(), client.player.getId());
    }

    private static void tick(MinecraftClient client) {
        boolean active = BlindView.isActive(client);
        ClientWorld world = client.world;
        if (active != wasActive || world != lastWorld) {
            BlindPerceptionClientState.get().reset();
        }
        wasActive = active;
        lastWorld = world;
        if (active) {
            BlindPerceptionClientState.get().prune(Util.getMeasuringTimeNano());
        }
    }
}
