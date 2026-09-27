package dev.caecorthus.sparkwitch.client.timestealer;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Registers only Time-Stealer-owned client presentation (the owner's stamp HUD). Never touches the Witch skill
 * inventory panel, which belongs only to the three witch roles (AGENTS.md).
 * 只注册窃时者自有的客户端展示（拥有者的邮票 HUD）；从不触及仅属于三个魔女职业的魔女技能背包面板（AGENTS.md）。
 */
public final class TimeStealerClient {
    private static boolean initialized;

    private TimeStealerClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                renderHud(context, tickCounter);
            }
        });
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        // TODO(WP-06): bottom-right stamp count from TimeStampInventory.balance(local player) for the exact local role.
    }
}
