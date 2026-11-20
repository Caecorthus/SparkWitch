package dev.caecorthus.sparkwitch.client.props;

import dev.caecorthus.sparkwitch.net.props.NeonSettingsS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Opens the neon settings screen when the server answers a sneak-right-click on a pillar.
 * <p>
 * 服务端回应灯柱的潜行右键后，打开霓虹设置界面。
 */
public final class NeonClientNetworking {
    private NeonClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NeonSettingsS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> client.setScreen(new NeonSettingsScreen(
                    payload.pos(), payload.onTicks(), payload.offTicks(), payload.enabled(), payload.random())));
        });
    }
}
