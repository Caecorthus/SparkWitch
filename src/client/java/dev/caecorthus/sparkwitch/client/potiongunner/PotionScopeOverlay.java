package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.client.scope.ScopeFrame;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * The launcher reticle, drawn by {@link PotionScopeProfile#drawReticle} after {@code client/scope} has drawn the lens
 * and its rim. INTERIM: it ports the old scope's content (a thin crosshair to the lens edge whose centre is labelled
 * 0-50 for flat flight, the 60/70/80/90/100-block drop ticks below it with alternating labels, and the loaded shell)
 * onto the {@link ScopeFrame} geometry; a later design pass replaces only {@link #draw}'s body. Everything is placed
 * around the frame's lens centre and stays inside its lens radius; nothing outside the lens is painted. Tick placement
 * uses the frame's real projection FOV and the exact shell ballistics at the current pitch
 * ({@link PotionScopeRules#tickOffset}). Presentation only; the server decides every shot.
 * 炮筒分划，由 {@link PotionScopeProfile#drawReticle} 在 {@code client/scope} 画好镜片与镜框后绘制。过渡版本：把旧瞄准镜的
 * 内容（延伸到镜片边缘、中心标注 0-50 平飞的细十字线，其下方左右交替标注的 60/70/80/90/100 格下坠刻度，以及已装填弹种）
 * 移植到 {@link ScopeFrame} 几何上；之后的设计只替换 {@link #draw} 的方法体。全部内容围绕帧的镜片中心放置并保持在镜片半径
 * 以内，镜片外不绘制任何东西。刻度位置使用帧的真实投影视场角与当前俯仰角下的精确弹道（{@link PotionScopeRules#tickOffset}）。
 * 仅负责展示，每次发射都由服务端决定。
 */
public final class PotionScopeOverlay {
    private static final int LINE = 0xE6F2F2F2;
    private static final int LINE_SHADOW = 0x99000000;
    private static final int CENTER_GAP = 3;
    private static final int TICK_HALF_WIDTH = 4;
    private static final float LABEL_SCALE = 0.75F;
    /** Swatch (6 px) plus its gap before the shell name. / 色块（6 像素）及其与弹种名之间的间隙。 */
    private static final int SWATCH_ADVANCE = 9;

    private PotionScopeOverlay() {
    }

    public static void draw(DrawContext context, ScopeFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || frame == null || !(frame.lensRadius() > 0.0F)) {
            return;
        }
        double radius = frame.lensRadius();
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(frame.lensCenterX(), frame.lensCenterY(), 0.0F);

        int arm = PotionScopeRules.crosshairArm(radius);
        line(context, -arm, 0, -CENTER_GAP, 1);
        line(context, CENTER_GAP + 1, 0, arm, 1);
        line(context, 0, -arm, 1, -CENTER_GAP);
        line(context, 0, CENTER_GAP + 1, 1, arm);
        line(context, 0, 0, 1, 1);

        // The lens centre is the exact framebuffer centre, so its y is also the projection's half height (GUI px).
        // 镜片中心即帧缓冲精确中心，因此其 y 也是投影的半高（GUI 像素）。
        renderRangeTicks(context, client.textRenderer, player.getPitch(frame.tickDelta()),
                frame.projectionFovDegrees(), frame.lensCenterY(), radius);
        renderLoadedShell(context, client.textRenderer, PotionScopeClient.loaded(player.getActiveItem()), radius);

        matrices.pop();
    }

    private static void renderRangeTicks(DrawContext context, TextRenderer text, float pitch, double fov,
                                         double halfHeight, double radius) {
        double labelHeight = text.fontHeight * LABEL_SCALE;
        // Flat flight: the centre is the aim point for the whole 0-50 range; the shell passes only the 0.1-block launch
        // offset below it. / 平飞：整个 0-50 格内准星中心即瞄准点；炮弹只比它低 0.1 格的发射偏移。
        label(context, text, PotionScopeRules.CENTER_LABEL, CENTER_GAP + 3.0, -labelHeight - 1.0, radius);
        double tickRadius = radius * PotionScopeRules.TICK_RADIUS_SHARE;
        for (int index = 0; index < PotionScopeRules.RANGE_TICKS.size(); index++) {
            int distance = PotionScopeRules.RANGE_TICKS.get(index);
            double offset = PotionScopeRules.tickOffset(pitch, distance, fov, halfHeight, tickRadius);
            if (Double.isNaN(offset)) {
                continue;
            }
            int y = (int) Math.round(offset);
            // The bar and its shadow span x -4..6, y..y+2. / 刻度线连同阴影占 x -4..6、y..y+2。
            if (!PotionScopeRules.insideLens(-TICK_HALF_WIDTH, y, TICK_HALF_WIDTH + 2, y + 2, radius)) {
                continue;
            }
            line(context, -TICK_HALF_WIDTH, y, TICK_HALF_WIDTH + 1, y + 1);
            String value = Integer.toString(distance);
            double labelX = PotionScopeRules.labelOnRight(index)
                    ? TICK_HALF_WIDTH + 4.0
                    : -TICK_HALF_WIDTH - 3.0 - text.getWidth(value) * LABEL_SCALE;
            label(context, text, value, labelX, PotionScopeRules.labelTop(offset, labelHeight), radius);
        }
    }

    /** A small label, skipped when any part of it (shadow included) would leave the lens. / 小标签；任何部分（含阴影）超出镜片时跳过。 */
    private static void label(DrawContext context, TextRenderer text, String value, double x, double y,
                              double radius) {
        double width = (text.getWidth(value) + 1) * LABEL_SCALE;
        double height = (text.fontHeight + 1) * LABEL_SCALE;
        if (!PotionScopeRules.insideLens(x, y, x + width, y + height, radius)) {
            return;
        }
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        context.drawTextWithShadow(text, value, 0, 0, LINE);
        matrices.pop();
    }

    /**
     * The loaded shell (swatch and name), right-aligned to the lens's upper-right 45-degree point and shrunk only when
     * that quadrant is too narrow ({@link PotionScopeRules#shellLineScale}).
     * 已装填弹种（色块与名称），右对齐到镜片右上 45 度点，仅在该象限过窄时缩小（{@link PotionScopeRules#shellLineScale}）。
     */
    private static void renderLoadedShell(DrawContext context, TextRenderer text, Optional<PotionShellType> loaded,
                                          double radius) {
        Text line = PotionGunnerHud.loadedText(loaded);
        int textWidth = text.getWidth(line);
        int lineWidth = textWidth + 1 + (loaded.isPresent() ? SWATCH_ADVANCE : 0);
        double scale = PotionScopeRules.shellLineScale(radius, lineWidth);
        if (!(scale > 0.0)) {
            return;
        }
        double corner = PotionScopeRules.shellLineCorner(radius);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        // Local origin = the line's top-right corner. / 局部原点即装填行的右上角。
        matrices.translate(corner, -corner, 0.0F);
        matrices.scale((float) scale, (float) scale, 1.0F);
        int x = -textWidth - 1;
        if (loaded.isPresent()) {
            int swatch = 0xFF000000 | loaded.get().color();
            context.fill(x - 9, 1, x - 3, 7, 0xFFFFFFFF);
            context.fill(x - 8, 2, x - 4, 6, swatch);
        }
        context.drawTextWithShadow(text, line, x, 0, PotionGunnerHud.baseColor(loaded));
        matrices.pop();
    }

    /** A filled rectangle with a one-pixel dark shadow, readable on bright and dark scenes. / 带一像素暗影的填充矩形，明暗场景都可读。 */
    private static void line(DrawContext context, int x1, int y1, int x2, int y2) {
        context.fill(x1 + 1, y1 + 1, x2 + 1, y2 + 1, LINE_SHADOW);
        context.fill(x1, y1, x2, y2, LINE);
    }
}
