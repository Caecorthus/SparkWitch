package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockBounds;
import dev.caecorthus.sparkwitch.client.witchmaiden.MiniWitherRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

/**
 * Draws the current Mini Wither look (the active skin, else the vanilla Wither) turning slowly inside a GUI square,
 * for the skin settings screen. The square frames the auto-fit box with its animation allowance, so a skin shows at
 * exactly the size it will have in game.
 * 在 GUI 方框内缓慢旋转地绘制当前的小凋灵外观（当前皮肤，否则为原版凋灵），供皮肤设置界面使用。方框按自动缩放包围盒及其动画
 * 余量取景，因此皮肤显示的大小与游戏中完全一致。
 */
public final class MiniWitherSkinPreview {
    /** One full turn, in milliseconds. / 转一整圈的毫秒数。 */
    private static final long TURN_MS = 12_000L;

    private MiniWitherSkinPreview() {
    }

    public static void render(DrawContext context, int centerX, int centerY, int size, float tickDelta) {
        BedrockBounds box = MiniWitherRenderer.previewBounds();
        float extent = Math.max(box.sizeY(), Math.max(box.sizeX(), box.sizeZ())) * MiniWitherSkinFit.ALLOWANCE;
        float scale = size / Math.max(extent, 1.0E-3F);
        float spin = (Util.getMeasuringTimeMs() % TURN_MS) * 360.0F / TURN_MS;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        // InventoryScreen#drawEntity's GUI entity frame: y up, facing the viewer.
        // 沿用 InventoryScreen#drawEntity 的 GUI 实体坐标系：y 向上、面向观察者。
        matrices.translate(centerX, centerY, 50.0F);
        matrices.scale(scale, scale, -scale);
        matrices.multiply(new Quaternionf().rotateZ((float) Math.PI));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        matrices.translate(-box.centerX(), -box.centerY(), -box.centerZ());
        DiffuseLighting.method_34742();
        MiniWitherRenderer.drawPreview(matrices, context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE);
        context.draw();
        matrices.pop();
        DiffuseLighting.enableGuiDepthLighting();
    }
}
