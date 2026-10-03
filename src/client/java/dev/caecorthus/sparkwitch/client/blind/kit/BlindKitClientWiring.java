package dev.caecorthus.sparkwitch.client.blind.kit;

import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.UseBlindAttuneC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client wiring of the Blind's kit: the owner-only HUD and the Attune key. Ambient ducking belongs to the client
 * gates. Role-owned presentation; never part of the witch skill inventory panel.
 * 盲人道具的客户端接线：仅本人可见的 HUD 与凝神按键。氛围音降低属于客户端闸门部分。属于职业自有展示，
 * 从不属于魔女技能背包面板。
 */
public final class BlindKitClientWiring {
    private static boolean registered;

    private BlindKitClientWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlindKitHud.register();
    }

    /**
     * Called by the shared G-key dispatcher in {@code SparkWitchClient} when the local player's real role is the Blind.
     * Sends the empty Attune request while the Blind view is active; the server validates everything.
     * 当本地玩家真实职业为盲人时，由 {@code SparkWitchClient} 的共享 G 键分发调用。盲人视图激活时发送空的凝神请求；
     * 全部校验在服务端完成。
     */
    public static void onAbilityKey(MinecraftClient client) {
        if (client != null && client.player != null && BlindView.isActive(client)) {
            ClientPlayNetworking.send(new UseBlindAttuneC2SPayload());
        }
    }
}
