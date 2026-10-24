package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.compat.SparkStrengthHudBridge;
import dev.caecorthus.sparkwitch.client.gui.OwnerInventoryPresenter;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Renders the local player's mana below Wathe's money row, and below the killer team-wallet row SparkStrength stacks
 * under it while that row is showing.
 * 在 wathe 金币行下方显示本地玩家的魔力值；SparkStrength 在金币下方显示杀手团队余额行时，改为显示在该行下方。
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
        MinecraftClient client = MinecraftClient.getInstance();
        // The owner inventory card's header tail replaces this row while it shows the same mana (last frame's
        // state; the HUD draws before the screen), so the value is never shown twice.
        // 背包卡片标题尾注显示魔力时由其取代此行（取上一帧状态，HUD 先于界面绘制），避免同一数值显示两次。
        if (OwnerInventoryPresenter.showsManaTail(client.currentScreen)) {
            return;
        }

        TextRenderer renderer = client.textRenderer;
        Text text = Text.translatable("gui.sparkwitch.mana", component.getMana());
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(text);
        int y = rowY(renderer.fontHeight, SparkStrengthHudBridge.topRightHudBottom());
        context.drawTextWithShadow(renderer, text, x, y, MANA_COLOR);
    }

    /**
     * Mana row y. {@code strengthRowsBottom} is SparkStrength's exclusive band bottom (0 when no row shows); that band
     * ends one pixel below its row's text, so the mana keeps the same {@link #ROW_GAP} under the team row as under
     * Wathe's money.
     * 魔力行 y。{@code strengthRowsBottom} 为 SparkStrength 叠加行的底边（不含，未显示时为 0）；该底边比其文字底部低 1 像素，
     * 因此魔力行与团队余额行之间保持与 Wathe 金币行相同的 {@link #ROW_GAP} 间距。
     */
    static int rowY(int fontHeight, int strengthRowsBottom) {
        int underMoney = TOP_PADDING + fontHeight + ROW_GAP;
        return strengthRowsBottom > 0 ? Math.max(underMoney, strengthRowsBottom - 1 + ROW_GAP) : underMoney;
    }
}
