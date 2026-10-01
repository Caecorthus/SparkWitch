package dev.caecorthus.sparkwitch.client.fisher;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Owner-only Glimmerfish countdown just below the crosshair (clear of the knife glyph and the bottom HUD stack). It reads
 * the local player's own synced remaining ticks; other clients only ever receive the flag. Role-owned presentation,
 * never part of the witch skill inventory panel.
 * 仅拥有者可见的灵光倒计时，位于准星正下方（避开刀的图标与底部 HUD）。只读取本地玩家自己同步到的剩余刻数；
 * 其他客户端只会收到标记。属于职业自有展示，从不属于魔女技能背包面板。
 */
public final class FisherGlimmerHud {
    /** Gap below the screen centre; Wathe's knife glyph occupies +5..+12. / 屏幕中心下方的间距；刀图标占用 +5..+12。 */
    private static final int OFFSET_Y = 16;
    private static boolean registered;

    private FisherGlimmerHud() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                render(context);
            }
        });
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()) {
            return;
        }
        int remaining = FisherGlimmerClient.remainingTicks(client.player);
        if (remaining <= 0) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        Text text = Text.translatable(FisherGlimmerClientRules.HUD_KEY, FisherGlimmerClientRules.seconds(remaining));
        int x = (context.getScaledWindowWidth() - renderer.getWidth(text)) / 2;
        int y = context.getScaledWindowHeight() / 2 + OFFSET_Y;
        context.drawTextWithShadow(renderer, text, x, y, 0xFF000000 | FisherGlimmerClientRules.OUTLINE_COLOR);
    }
}
