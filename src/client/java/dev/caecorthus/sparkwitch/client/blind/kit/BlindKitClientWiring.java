package dev.caecorthus.sparkwitch.client.blind.kit;

import net.minecraft.client.MinecraftClient;

/**
 * Client wiring of the Blind's kit: Attune key, HUD and ambient ducking (owner: the kit work package). Stub.
 * 盲人道具的客户端接线：凝神按键、HUD 与氛围音降低（归属：道具工作包）。占位。
 */
public final class BlindKitClientWiring {
    private BlindKitClientWiring() {
    }

    public static void register() {
    }

    /**
     * Called by the shared G-key dispatcher in {@code SparkWitchClient} when the local player's real role is the Blind.
     * 当本地玩家真实职业为盲人时，由 {@code SparkWitchClient} 的共享 G 键分发调用。
     */
    public static void onAbilityKey(MinecraftClient client) {
    }
}
