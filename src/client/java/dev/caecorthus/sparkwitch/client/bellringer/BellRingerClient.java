package dev.caecorthus.sparkwitch.client.bellringer;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellEchoPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.TollBellGlint;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Registers only Bell-Ringer-owned client presentation: the bell glint supplier and the heard hint HUD.
 * Never touches the {@code gui.sparkwitch.skills} inventory panel.
 * 只注册敲钟人自有的客户端展示：钟的光效提供器与“听到钟声”提示 HUD；从不触及 {@code gui.sparkwitch.skills} 背包面板。
 */
public final class BellRingerClient {
    private static boolean initialized;

    private BellRingerClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // Reads only the local owner's synced flag; other viewers never receive it. / 只读取本地拥有者的同步标记，其他观察者永远收不到。
        TollBellGlint.install(BellRingerClient::isLocalTollReady);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                BellEchoHintHud.render(context, tickCounter);
            }
        });
    }

    private static boolean isLocalTollReady() {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        return player != null && client.world != null && BellEchoPlayerComponent.KEY.get(player).tollReady();
    }
}
