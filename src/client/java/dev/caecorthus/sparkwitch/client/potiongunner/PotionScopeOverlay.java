package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeFrame;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * The launcher reticle, drawn by {@link PotionScopeProfile#drawReticle} after {@code client/scope} has drawn the lens
 * and its rim: the lit (amber) PGO-7 of {@link PotionScopeRules#layout}, placed from the {@link ScopeFrame} (lens
 * centre and radius, the real projection FOV, the interpolated pitch) and corrected for the lens's barrel distortion
 * only while a lens composite distorts this frame ({@link ScopeClient#lensDistorts}, never under the HUD fallback
 * ring), then the loaded shell at the lens's upper right. Like the USEC reticle it draws plain HUD quads and the vanilla
 * font in framebuffer pixels (the matrix moved to the lens centre and scaled by {@code 1 / guiScale}). Presentation
 * only; the server decides every shot.
 * <p>
 * Paint (A_LIT_SPEC.md §5, without the optional glow): a dark key ring around every stroke and digit, then the amber
 * strokes and digits. The strokes' key is translucent (55 %) and must cover a pixel once, as the spec's dilated mask
 * does, even where grown rectangles overlap (grid crossings, the rangefinder curve). The GUI layer keeps its quad
 * order (it is not a sorted layer), so the GUI depth test (LEQUAL, with depth writes) does that: every key quad sits a
 * little farther than the previous one and fails wherever an earlier one drew. The text layer re-sorts its quads by
 * depth, so the digits' eight outline copies are opaque instead and need no depth order. The reticle lives in negative
 * z inside the crosshair layer ({@code LayeredDrawer} puts layers 200 apart, and the depth buffer is cleared before
 * the HUD), behind anything drawn later in this layer and in front of the layer below.
 * 炮筒分划，由 {@link PotionScopeProfile#drawReticle} 在 {@code client/scope} 画好镜片与镜框后绘制：
 * {@link PotionScopeRules#layout} 的发光（琥珀色）PGO-7，依据 {@link ScopeFrame}（镜片中心与半径、真实投影视场角、插值
 * 俯仰角）定位，仅在本帧镜片合成着色器确实产生畸变时（{@link ScopeClient#lensDistorts}，HUD 回退环下从不）校正桶形畸变；
 * 随后在镜片右上方绘制已装填弹种。与 USEC 分划一样，以帧缓冲像素绘制普通 HUD 四边形与原版字体（矩阵移到镜片中心并按
 * {@code 1 / guiScale} 缩放）。仅负责展示，每次发射都由服务端决定。
 * <p>
 * 配色（规格第 5 节，不含可选的辉光）：每条线与每个数字外的一圈深色描边，然后是琥珀色线条与数字。线条描边半透明（55%），
 * 即使扩展后的矩形相互重叠（网格交点、测距尺曲线），也必须对每个像素只覆盖一次（与规格中膨胀后的蒙版一致）。GUI 渲染层保持
 * 四边形顺序（不是会排序的渲染层），因此 GUI 深度测试（LEQUAL，写入深度）可以做到：每个描边四边形都比前一个稍远，在先前已
 * 绘制处深度测试失败。文字渲染层会按深度重新排序四边形，因此数字的八个描边副本改为不透明，不依赖深度顺序。分划位于准星层内
 * 的负 z 区间（{@code LayeredDrawer} 各层相距 200，且 HUD 之前已清除深度缓冲），位于本层之后绘制的内容之后、下一层之前。
 */
public final class PotionScopeOverlay {
    /**
     * GUI z inside the crosshair layer: the stroke keys farthest (room for well over a thousand quads above the layer
     * below at -200; an 8K frame needs about 600), then the digit outlines, then the amber core.
     * 准星层内的 GUI z：线条描边最远（在下一层 -200 之上可容纳一千多个四边形；8K 画面约需 600 个），其次是数字描边，最后是
     * 琥珀色主体。
     */
    private static final float KEY_Z = -170.0F;
    private static final float LABEL_KEY_Z = -100.0F;
    private static final float CORE_Z = -60.0F;
    /** Each key quad this much farther than the previous one (about 16 depth-buffer steps). / 深度步长。 */
    private static final float DEPTH_STEP = 0.02F;
    /** Digit outline offsets, in strokes: the 3 x 3 neighbourhood. / 数字描边偏移（以线宽为单位）：3 x 3 邻域。 */
    private static final int[][] KEY_OFFSETS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    /** Swatch (6 px) plus its gap before the shell name. / 色块（6 像素）及其与弹种名之间的间隙。 */
    private static final int SWATCH_ADVANCE = 9;
    /** Black at alpha 150 under the swatch, offset (+1, +1). / 色块下方偏移 (+1, +1) 的黑色阴影，透明度 150。 */
    private static final int SWATCH_SHADOW = 0x96000000;
    /** The last layout and its inputs, so a still scope reuses it; render thread only. / 上次的布局及其输入；仅渲染线程。 */
    @Nullable
    private static LayoutKey lastKey;
    @Nullable
    private static PotionScopeRules.Reticle lastReticle;

    private PotionScopeOverlay() {
    }

    public static void draw(DrawContext context, ScopeFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || frame == null || !(frame.lensRadius() > 0.0F)) {
            return;
        }
        TextRenderer font = client.textRenderer;
        double guiScale = client.getWindow().getScaleFactor();
        // The lens centre is the exact framebuffer centre, so twice its y is the framebuffer height.
        // 镜片中心即帧缓冲精确中心，因此其 y 的两倍就是帧缓冲高度。
        LayoutKey key = new LayoutKey(player.getPitch(frame.tickDelta()), frame.projectionFovDegrees(),
                frame.lensCenterY() * 2.0 * guiScale, frame.lensRadius() * guiScale,
                ScopeClient.lensDistorts(frame.mode()));
        PotionScopeRules.Reticle reticle = lastReticle;
        if (reticle == null || !key.equals(lastKey)) {
            reticle = PotionScopeRules.layout(key.pitch(), key.fov(), key.height(), key.radius(), key.distorted(),
                    text -> font.getWidth(text) - 1);
            lastKey = key;
            lastReticle = reticle;
        }
        renderReticle(context, font, frame, guiScale, reticle);
        renderLoadedShell(context, font, frame, PotionScopeClient.loaded(player.getActiveItem()));
    }

    private static void renderReticle(DrawContext context, TextRenderer font, ScopeFrame frame, double guiScale,
                                      PotionScopeRules.Reticle reticle) {
        if (reticle.strokes().isEmpty()) {
            return;
        }
        int s = reticle.strokeScale();
        double radius = frame.lensRadius() * guiScale;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(frame.lensCenterX(), frame.lensCenterY(), 0.0F);
        matrices.scale((float) (1.0 / guiScale), (float) (1.0 / guiScale), 1.0F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        fills(context, matrix, reticle, s, radius, KEY_Z, DEPTH_STEP, PotionScopeRules.LIT_KEY_COLOR);
        for (PotionScopeRules.Label label : reticle.labels()) {
            for (int[] offset : KEY_OFFSETS) {
                text(context, font, matrix, label, reticle.textScale(), offset[0] * s, offset[1] * s, LABEL_KEY_Z,
                        PotionScopeRules.LIT_LABEL_KEY_COLOR);
            }
        }
        context.draw();
        fills(context, matrix, reticle, 0, radius, CORE_Z, 0.0F, PotionScopeRules.LIT_COLOR);
        for (PotionScopeRules.Label label : reticle.labels()) {
            text(context, font, matrix, label, reticle.textScale(), 0, 0, CORE_Z, PotionScopeRules.LIT_COLOR);
        }
        context.draw();
        matrices.pop();
    }

    /**
     * Every stroke grown by {@code grow} px and clipped to the lens, one GUI quad each from {@code z} on, stepping
     * {@code step} farther per quad. / 每条线扩展 {@code grow} 像素并裁剪到镜片内后各画一个 GUI 四边形。
     */
    private static void fills(DrawContext context, Matrix4f matrix, PotionScopeRules.Reticle reticle, int grow,
                              double radius, float z, float step, int color) {
        VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        float depth = z;
        for (PotionScopeRules.Rect stroke : reticle.strokes()) {
            PotionScopeRules.Rect r = grow == 0 ? stroke : PotionScopeRules.clipToLens(stroke.x0() - grow,
                    stroke.y0() - grow, stroke.x1() + grow, stroke.y1() + grow, radius);
            if (r == null) {
                continue;
            }
            // DrawContext.fill's winding. / 与 DrawContext.fill 相同的绕序。
            buffer.vertex(matrix, r.x0(), r.y0(), depth).color(color);
            buffer.vertex(matrix, r.x0(), r.y1(), depth).color(color);
            buffer.vertex(matrix, r.x1(), r.y1(), depth).color(color);
            buffer.vertex(matrix, r.x1(), r.y0(), depth).color(color);
            depth -= step;
        }
        context.draw();
    }

    /** One label copy, {@code (dx, dy)} px off its place, at depth {@code z}, without the vanilla drop shadow. */
    private static void text(DrawContext context, TextRenderer font, Matrix4f matrix, PotionScopeRules.Label label,
                             int scale, int dx, int dy, float z, int color) {
        Matrix4f placed = new Matrix4f(matrix).translate(label.x() + dx, label.y() + dy, z).scale(scale, scale, 1.0F);
        font.draw(label.text(), 0.0F, 0.0F, color, false, placed, context.getVertexConsumers(),
                TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    /**
     * The loaded shell in GUI px with the game's text shadow (A_LIT_SPEC.md §6): the shell's name in its colour after
     * a swatch, or the empty text, right edge at {@link PotionScopeRules#SHELL_LINE_RIGHT_RHO} and top at
     * {@link PotionScopeRules#SHELL_LINE_TOP_RHO} lens radii from the centre; shrunk only when that is too narrow
     * ({@link PotionScopeRules#shellLineScale}).
     * 以 GUI 像素与游戏文字阴影绘制已装填弹种（规格第 6 节）：色块后接弹种名（弹种颜色），或未装填文字；右缘与顶部距中心
     * {@link PotionScopeRules#SHELL_LINE_RIGHT_RHO} / {@link PotionScopeRules#SHELL_LINE_TOP_RHO} 个镜片半径；仅在空间过窄时
     * 缩小（{@link PotionScopeRules#shellLineScale}）。
     */
    private static void renderLoadedShell(DrawContext context, TextRenderer font, ScopeFrame frame,
                                          Optional<PotionShellType> loaded) {
        Text line = loaded.<Text>map(type -> Text.translatable(PotionScopeRules.shellNameKey(type)))
                .orElseGet(() -> Text.translatable(PotionScopeRules.HUD_EMPTY_KEY));
        int color = loaded.map(PotionScopeRules::textColor).orElse(PotionScopeRules.EMPTY_COLOR);
        int textWidth = font.getWidth(line);
        int lineWidth = textWidth + 1 + (loaded.isPresent() ? SWATCH_ADVANCE : 0);
        double radius = frame.lensRadius();
        double scale = PotionScopeRules.shellLineScale(radius, lineWidth);
        if (!(scale > 0.0)) {
            return;
        }
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        // Local origin = the line's top-right corner. / 局部原点即装填行的右上角。
        matrices.translate(frame.lensCenterX() + PotionScopeRules.SHELL_LINE_RIGHT_RHO * radius,
                frame.lensCenterY() - PotionScopeRules.SHELL_LINE_TOP_RHO * radius, 0.0F);
        matrices.scale((float) scale, (float) scale, 1.0F);
        int x = -textWidth;
        if (loaded.isPresent()) {
            int swatch = 0xFF000000 | loaded.get().color();
            context.fill(x - 8, 2, x - 2, 8, SWATCH_SHADOW);
            context.fill(x - 9, 1, x - 3, 7, 0xFFFFFFFF);
            context.fill(x - 8, 2, x - 4, 6, swatch);
        }
        context.drawTextWithShadow(font, line, x, 0, color);
        matrices.pop();
    }

    /** What a layout depends on besides the font. / 布局所依赖的输入（字体除外）。 */
    private record LayoutKey(float pitch, double fov, double height, double radius, boolean distorted) {
    }
}
