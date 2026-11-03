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
 * Draws the current Mini Wither look (the active skin, else the vanilla Wither) inside a GUI square, for the skin
 * settings screen: turning slowly, or at an explicit yaw the screen controls (drag to rotate). The square frames the
 * auto-fit box with its animation allowance, so a skin shows at exactly the size it will have in game.
 * 在 GUI 方框内绘制当前的小凋灵外观（当前皮肤，否则为原版凋灵），供皮肤设置界面使用：缓慢旋转，或按界面控制的偏航角绘制
 * （拖动旋转）。方框按自动缩放包围盒及其动画余量取景，因此皮肤显示的大小与游戏中完全一致。
 */
public final class MiniWitherSkinPreview {
    /** One full turn, in milliseconds. / 转一整圈的毫秒数。 */
    private static final long TURN_MS = 12_000L;

    private MiniWitherSkinPreview() {
    }

    /** Turning slowly with the clock. / 随时间缓慢旋转。 */
    public static void render(DrawContext context, int centerX, int centerY, int size, float tickDelta) {
        render(context, centerX, centerY, size, tickDelta,
                (Util.getMeasuringTimeMs() % TURN_MS) * 360.0F / TURN_MS);
    }

    /**
     * At {@code yawDegrees} around the vertical axis (0 = the same pose as the auto-spin's start).
     * 绕竖直轴转到 {@code yawDegrees}（0 与自动旋转起点的姿态相同）。
     */
    public static void render(DrawContext context, int centerX, int centerY, int size, float tickDelta,
                              float yawDegrees) {
        BedrockBounds box = MiniWitherRenderer.previewBounds();
        float extent = Math.max(box.sizeY(), Math.max(box.sizeX(), box.sizeZ())) * MiniWitherSkinFit.ALLOWANCE;
        float scale = size / Math.max(extent, 1.0E-3F);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        // A throwing draw (the settings screen catches it) must not leave the pose stack or the lighting changed.
        // 绘制抛出异常时（设置界面会捕获）不得遗留姿态栈或光照的改动。
        try {
            // InventoryScreen#drawEntity's GUI entity frame: y up, facing the viewer.
            // 沿用 InventoryScreen#drawEntity 的 GUI 实体坐标系：y 向上、面向观察者。
            matrices.translate(centerX, centerY, 50.0F);
            matrices.scale(scale, scale, -scale);
            matrices.multiply(new Quaternionf().rotateZ((float) Math.PI));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
            matrices.translate(-box.centerX(), -box.centerY(), -box.centerZ());
            DiffuseLighting.method_34742();
            try {
                MiniWitherRenderer.drawPreview(matrices, context.getVertexConsumers(),
                        LightmapTextureManager.MAX_LIGHT_COORDINATE);
                context.draw();
            } finally {
                DiffuseLighting.enableGuiDepthLighting();
            }
        } finally {
            matrices.pop();
        }
    }
}
