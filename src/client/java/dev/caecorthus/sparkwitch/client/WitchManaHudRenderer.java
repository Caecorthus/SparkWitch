package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Renders the local player's mana below Wathe's money row.
 * 在 wathe 金币行下方显示本地玩家的魔力值。
 */
public final class WitchManaHudRenderer {
    private static final int RIGHT_PADDING = 12;
    private static final int TOP_PADDING = 6;
    private static final int ROW_GAP = 5;
    private static final int MANA_COLOR = 0xD6B0FF;

    private WitchManaHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (!component.hasManaSystem()) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text text = Text.translatable("gui.sparkwitch.mana", component.getMana());
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(text);
        int y = TOP_PADDING + renderer.fontHeight + ROW_GAP;
        context.drawTextWithShadow(renderer, text, x, y, MANA_COLOR);
    }
}
