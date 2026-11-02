package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import net.minecraft.client.gui.DrawContext;

/**
 * Draws the current Mini Wither look (the active skin, else the vanilla Wither) turning slowly inside a GUI square,
 * for the skin settings screen.
 * 在 GUI 方框内缓慢旋转地绘制当前的小凋灵外观（当前皮肤，否则为原版凋灵），供皮肤设置界面使用。
 */
public final class MiniWitherSkinPreview {
    // CONTRACT SKELETON (2026-11-02): the coordinator implements this with the renderer integration.

    private MiniWitherSkinPreview() {
    }

    public static void render(DrawContext context, int centerX, int centerY, int size, float tickDelta) {
        throw new UnsupportedOperationException("TODO");
    }
}
