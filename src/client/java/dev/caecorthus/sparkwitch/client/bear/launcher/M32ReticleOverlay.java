package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.client.scope.ScopeFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Client only: the lit-red M2A1 reflex reticle and its range-dial drum, drawn by {@link M32ScopeProfile#drawReticle}
 * on the HUD crosshair layer (the scope module draws no lens, rim or mask for this lensless sight). The layout is
 * {@link M32ReticleRules#layout} in {@link M32ReticleRules#STYLE} for the live interpolated pitch, the frame's real
 * projection FOV and the dialled range ({@link M32RangeDial}). Like the anti-tank launcher's PGO-7 it is plain
 * {@code RenderLayer.getGui()} quads and the vanilla font in framebuffer pixels (the matrix moved to the screen centre
 * and scaled by {@code 1 / guiScale}).
 * <p>
 * Depth order (the GUI layer keeps its quad order and tests LEQUAL with depth writes): lit cores, then dim cores, then
 * the key ring, each quad {@link #DEPTH_STEP} farther than the one before, so a later quad fails wherever an earlier one
 * drew. Every pixel is therefore painted once, by its top layer, and translucent dim strokes and keys never stack. The
 * drum uses the same rule farther back: opaque outline, knurls and pointer, then the translucent fill. Texts come last
 * in front (label key copies opaque, see {@link M32ReticleRules#LABEL_KEY_COLOR}). Everything stays in negative z
 * inside the crosshair layer, in front of the layer below at -200. Presentation only; the server decides every shot.
 * Render thread only.
 * 仅客户端：发光红色 M2A1 反射式分划及其射程刻度鼓，由 {@link M32ScopeProfile#drawReticle} 在 HUD 准星层绘制（开镜模块不为
 * 这种无镜片瞄具绘制镜片、镜框或遮罩）。布局为 {@link M32ReticleRules#STYLE} 下的 {@link M32ReticleRules#layout}，输入实时插值
 * 俯仰角、本帧真实投影视场角与当前刻度（{@link M32RangeDial}）。与反坦克炮筒的 PGO-7 一样，以帧缓冲像素绘制普通
 * {@code RenderLayer.getGui()} 四边形与原版字体（矩阵移到屏幕中心并按 {@code 1 / guiScale} 缩放）。
 * <p>
 * 深度顺序（GUI 渲染层保持四边形顺序，深度测试为 LEQUAL 且写入深度）：先发光主体，再暗主体，最后描边，每个四边形都比前一个远
 * {@link #DEPTH_STEP}，因此后画的四边形在先前已绘制处失败。于是每个像素只由其最上层绘制一次，半透明的暗线条与描边不会叠加。
 * 刻度鼓在更远处使用同一规则：先不透明的轮廓、滚花与指针，再半透明填充。文字最后绘制在最前（标签描边副本不透明，见
 * {@link M32ReticleRules#LABEL_KEY_COLOR}）。全部位于准星层内的负 z 区间，在下一层 -200 之前。仅负责展示，每次发射都由服务端
 * 决定。仅渲染线程。
 */
public final class M32ReticleOverlay {
    private static final float STROKE_Z = -60.0F;
    private static final float DRUM_Z = -150.0F;
    private static final float LABEL_KEY_Z = -30.0F;
    private static final float TEXT_Z = -20.0F;
    /** Each quad this much farther than the previous one (about 16 depth-buffer steps). / 深度步长。 */
    private static final float DEPTH_STEP = 0.02F;
    /** Label key offsets, in strokes: the 3 x 3 neighbourhood. / 标签描边偏移（以线宽为单位）。 */
    private static final int[][] KEY_OFFSETS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    /** The last layout and its inputs, so a still sight reuses it. / 上次的布局及其输入，静止时复用。 */
    @Nullable
    private static LayoutKey lastKey;
    @Nullable
    private static M32ReticleRules.Reticle lastReticle;

    private M32ReticleOverlay() {
    }

    public static void draw(DrawContext context, ScopeFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || frame == null) {
            return;
        }
        TextRenderer font = client.textRenderer;
        double guiScale = client.getWindow().getScaleFactor();
        // The frame centre is the exact framebuffer centre, so twice its y is the framebuffer height.
        // 帧中心即帧缓冲精确中心，因此其 y 的两倍就是帧缓冲高度。
        LayoutKey key = new LayoutKey(M32ReticleRules.STYLE, player.getPitch(frame.tickDelta()),
                frame.projectionFovDegrees(), frame.lensCenterY() * 2.0 * guiScale, M32RangeDial.metres());
        M32ReticleRules.Reticle reticle = lastReticle;
        if (reticle == null || !key.equals(lastKey)) {
            reticle = M32ReticleRules.layout(key.style(), key.pitch(), key.fov(), key.height(), key.metres(),
                    text -> font.getWidth(text) - 1);
            lastKey = key;
            lastReticle = reticle;
        }
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(frame.lensCenterX(), frame.lensCenterY(), 0.0F);
        matrices.scale((float) (1.0 / guiScale), (float) (1.0 / guiScale), 1.0F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        float z = fills(buffer, matrix, reticle.lit(), M32ReticleRules.LIT_COLOR, STROKE_Z);
        z = fills(buffer, matrix, reticle.dim(), M32ReticleRules.LIT_DIM_COLOR, z);
        fills(buffer, matrix, reticle.key(), M32ReticleRules.KEY_COLOR, z);
        M32ReticleRules.Drum drum = reticle.drum();
        if (drum != null) {
            float d = fills(buffer, matrix, drum.outline(), M32ReticleRules.DRUM_OUTLINE_COLOR, DRUM_Z);
            d = fills(buffer, matrix, drum.knurls(), M32ReticleRules.DRUM_KNURL_COLOR, d);
            d = fills(buffer, matrix, drum.pointer(), M32ReticleRules.LIT_COLOR, d);
            fills(buffer, matrix, drum.fill(), M32ReticleRules.DRUM_FILL_COLOR, d);
        }
        context.draw();
        int scale = reticle.textScale();
        int s = reticle.strokeScale();
        for (M32ReticleRules.Label label : reticle.labels()) {
            for (int[] offset : KEY_OFFSETS) {
                text(context, font, matrix, label.text(), label.x() + offset[0] * s, label.y() + offset[1] * s,
                        scale, LABEL_KEY_Z, M32ReticleRules.LABEL_KEY_COLOR, false);
            }
            text(context, font, matrix, label.text(), label.x(), label.y(), scale, TEXT_Z, label.color(), false);
        }
        if (drum != null) {
            for (M32ReticleRules.DrumText drumText : drum.texts()) {
                text(context, font, matrix, drumText.text(), drumText.x(), drumText.y(), scale, TEXT_Z,
                        drumText.color(), drumText.shadow());
            }
        }
        context.draw();
        matrices.pop();
    }

    /**
     * One GUI quad per rect from {@code z} on, each {@link #DEPTH_STEP} farther; returns the next depth.
     * 每个矩形一个 GUI 四边形，从 {@code z} 起每个远 {@link #DEPTH_STEP}；返回下一个深度。
     */
    private static float fills(VertexConsumer buffer, Matrix4f matrix, List<M32ReticleRules.Rect> rects, int color,
                               float z) {
        float depth = z;
        for (M32ReticleRules.Rect r : rects) {
            // DrawContext.fill's winding. / 与 DrawContext.fill 相同的绕序。
            buffer.vertex(matrix, r.x0(), r.y0(), depth).color(color);
            buffer.vertex(matrix, r.x0(), r.y1(), depth).color(color);
            buffer.vertex(matrix, r.x1(), r.y1(), depth).color(color);
            buffer.vertex(matrix, r.x1(), r.y0(), depth).color(color);
            depth -= DEPTH_STEP;
        }
        return depth;
    }

    /** One text copy at ({@code x}, {@code y}) framebuffer px, {@code scale} px per texel. / 一份文字副本。 */
    private static void text(DrawContext context, TextRenderer font, Matrix4f matrix, String text, int x, int y,
                             int scale, float z, int color, boolean shadow) {
        Matrix4f placed = new Matrix4f(matrix).translate(x, y, z).scale(scale, scale, 1.0F);
        font.draw(text, 0.0F, 0.0F, color, shadow, placed, context.getVertexConsumers(),
                TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    /** What a layout depends on besides the font. / 布局所依赖的输入（字体除外）。 */
    private record LayoutKey(M32ReticleStyle style, float pitch, double fov, double height, int metres) {
    }
}
