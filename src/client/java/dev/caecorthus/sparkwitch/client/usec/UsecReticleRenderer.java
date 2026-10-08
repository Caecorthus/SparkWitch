package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.scope.ScopeFrame;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Draws a {@link UsecReticleGeometry.Layout} with plain HUD fills and the vanilla font, in framebuffer pixels: the
 * matrix is moved to the lens centre and scaled by {@code 1 / guiScale}, so a 1-px wire stays one device pixel at every
 * GUI scale. Nothing is baked into a texture. Pass order follows the spec: light halo around every stroke, dark keyline
 * around illuminated parts, the etched wire, a faint glow, the illuminated parts, then labels with their outline.
 * 用普通 HUD 填充与原版字体以帧缓冲像素绘制 {@link UsecReticleGeometry.Layout}：矩阵移到镜片中心并按
 * {@code 1 / guiScale} 缩放，因此 1 像素细线在任何 GUI 缩放下都是 1 个设备像素。不烘焙任何贴图。绘制顺序遵循规格：
 * 每条线外的浅色光晕、发光部件外的深色描边、蚀刻线、微弱辉光、发光部件，最后是带描边的标签。
 */
public final class UsecReticleRenderer {
    private UsecReticleRenderer() {
    }

    public static void draw(DrawContext context, TextRenderer font, ScopeFrame frame, UsecReticleStyle style,
                            double guiScale, double marksmanMultiplier, float zoomFovMultiplier) {
        if (frame == null || style == null || !(guiScale > 0.0)) {
            return;
        }
        UsecReticleGeometry.Layout layout = UsecReticleGeometry.layout(style, frame.projectionFovDegrees(),
                zoomFovMultiplier, frame.scaledHeight() * guiScale, frame.lensRadius() * guiScale,
                marksmanMultiplier);
        if (layout.rects().isEmpty()) {
            return;
        }
        int s = UsecReticleGeometry.strokeScale(frame.scaledHeight() * guiScale);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(frame.lensCenterX(), frame.lensCenterY(), 0.0F);
        matrices.scale((float) (1.0 / guiScale), (float) (1.0 / guiScale), 1.0F);
        for (UsecReticleGeometry.Rect rect : layout.rects()) {
            fill(context, rect, s, UsecReticleGeometry.HALO_COLOR);
        }
        for (UsecReticleGeometry.Rect rect : layout.rects()) {
            if (rect.layer() == UsecReticleGeometry.Layer.ILLUM) {
                fill(context, rect, s, UsecReticleGeometry.KEYLINE_COLOR);
            }
        }
        for (UsecReticleGeometry.Rect rect : layout.rects()) {
            if (rect.layer() == UsecReticleGeometry.Layer.ETCH) {
                fill(context, rect, 0, rect.color());
            }
        }
        for (UsecReticleGeometry.Rect rect : layout.rects()) {
            if (rect.layer() == UsecReticleGeometry.Layer.ILLUM) {
                fill(context, rect, 2 * s, UsecReticleGeometry.GLOW_COLOR);
            }
        }
        for (UsecReticleGeometry.Rect rect : layout.rects()) {
            if (rect.layer() == UsecReticleGeometry.Layer.ILLUM) {
                fill(context, rect, 0, rect.color());
            }
        }
        for (UsecReticleGeometry.Label label : layout.labels()) {
            drawLabel(context, font, matrices, label);
        }
        matrices.pop();
    }

    private static void fill(DrawContext context, UsecReticleGeometry.Rect rect, int grow, int color) {
        context.fill(rect.x0() - grow, rect.y0() - grow, rect.x1() + grow, rect.y1() + grow, color);
    }

    private static void drawLabel(DrawContext context, TextRenderer font, MatrixStack matrices,
                                  UsecReticleGeometry.Label label) {
        int scale = Math.max(1, label.scale());
        int width = font.getWidth(label.text());
        int x = switch (label.anchor()) {
            case LEFT -> label.x();
            case RIGHT -> label.x() - (width - 1) * scale;
            case CENTER -> label.x() - (width - 1) * scale / 2;
        };
        boolean illum = label.layer() == UsecReticleGeometry.Layer.ILLUM;
        int outline = illum ? UsecReticleGeometry.KEYLINE_COLOR : UsecReticleGeometry.HALO_COLOR;
        int color = illum ? UsecReticleGeometry.ILLUM_COLOR : UsecReticleGeometry.ETCH_COLOR;
        matrices.push();
        matrices.translate(x, label.y(), 0.0F);
        matrices.scale(scale, scale, 1.0F);
        // One-texel outline (keyline or halo), then the digits. / 一个字体像素的描边（深色或浅色），再画数字。
        context.drawText(font, label.text(), -1, 0, outline, false);
        context.drawText(font, label.text(), 1, 0, outline, false);
        context.drawText(font, label.text(), 0, -1, outline, false);
        context.drawText(font, label.text(), 0, 1, outline, false);
        context.drawText(font, label.text(), 0, 0, color, false);
        matrices.pop();
    }
}
