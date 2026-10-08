package dev.caecorthus.sparkwitch.client.scope;

import dev.caecorthus.sparkwitch.client.mixin.scope.ScopeGameRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

/**
 * Client only. What {@code ScopeInGameHudMixin} draws on the crosshair layer instead of the crosshair while scoped:
 * under a shader pack or a failing lens pipeline, a semi-transparent dark periphery with a darker tube rim (never
 * fully black); then the crisp lens rim ring; then the weapon's {@link ScopeProfile#drawReticle} with this frame's
 * {@link ScopeFrame}. Lens geometry comes from {@link ScopeLensGeometry}, centred on the exact framebuffer centre like
 * the lens shader.
 * 仅客户端。开镜时 {@code ScopeInGameHudMixin} 在准星层代替准星绘制的内容：开光影包或镜片管线失败时，先画半透明的暗色
 * 镜外区域与更暗的镜筒边（绝不全黑）；然后画清晰的镜框环；最后用本帧 {@link ScopeFrame} 调用武器的
 * {@link ScopeProfile#drawReticle}。镜片几何来自 {@link ScopeLensGeometry}，与镜片着色器一样以帧缓冲精确中心为圆心。
 */
public final class ScopeHud {
    private ScopeHud() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter, ScopeProfile profile) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        Window window = client.getWindow();
        double scale = window.getScaleFactor();
        int framebufferWidth = window.getFramebufferWidth();
        int framebufferHeight = window.getFramebufferHeight();
        float centerX = (float) (framebufferWidth / scale / 2.0);
        float centerY = (float) (framebufferHeight / scale / 2.0);
        float radius = (float) (ScopeLensGeometry.lensRadius(framebufferWidth, framebufferHeight) / scale);
        float pixel = (float) (1.0 / scale);

        ScopeMode mode = ScopeClient.effectiveMode();
        // PiP draws its own lens; the ring only stands in for a missing ZOOM_BLUR filter.
        // 画中画自己绘制镜片；回退环只替代缺失的全画面放大滤镜。
        if (mode == ScopeMode.ZOOM_BLUR && ScopeLensFilter.fallbackActive()) {
            float rimOuter = radius * ScopeLensGeometry.FALLBACK_RIM_OUTER;
            float cover = (float) ScopeLensGeometry.coverRadius(context.getScaledWindowWidth(),
                    context.getScaledWindowHeight(), centerX, centerY) + 2.0F;
            annulus(context, centerX, centerY, radius, rimOuter, ScopeLensGeometry.FALLBACK_RIM_INNER_COLOR,
                    ScopeLensGeometry.FALLBACK_RIM_OUTER_COLOR, scale);
            annulus(context, centerX, centerY, rimOuter, Math.max(cover, rimOuter + pixel),
                    ScopeLensGeometry.FALLBACK_RIM_OUTER_COLOR, ScopeLensGeometry.FALLBACK_PERIPHERY_COLOR, scale);
        }
        annulus(context, centerX, centerY, radius - ScopeLensGeometry.RIM_RING_INSIDE_PIXELS * pixel,
                radius + ScopeLensGeometry.RIM_RING_OUTSIDE_PIXELS * pixel, ScopeLensGeometry.RIM_RING_INNER_COLOR,
                ScopeLensGeometry.RIM_RING_OUTER_COLOR, scale);

        float tickDelta = tickCounter.getTickDelta(true);
        // PiP: the FOV ZOOM_BLUR would need to show the lens at its on-screen scale, so reticle angles match the lens.
        // 画中画：全画面放大以同样屏幕比例显示镜内所需的视场角，使分划角度与镜内画面一致。
        double fov = mode == ScopeMode.PICTURE_IN_PICTURE
                ? ScopePictureInPicture.equivalentFovDegrees()
                : ((ScopeGameRendererInvoker) client.gameRenderer)
                        .sparkwitch$scopeFov(client.gameRenderer.getCamera(), tickDelta, true);
        profile.drawReticle(context, new ScopeFrame(context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                centerX, centerY, radius, fov, tickDelta, ScopeRuntime.shadowX(), ScopeRuntime.shadowY(), mode));
    }

    /**
     * A filled ring with a radial colour gradient, one batched GUI draw. Quads use {@code DrawContext.fill}'s winding.
     * 带径向渐变的实心环，一次批量 GUI 绘制。四边形使用与 {@code DrawContext.fill} 相同的绕序。
     */
    private static void annulus(DrawContext context, float centerX, float centerY, float inner, float outer,
                                int innerColor, int outerColor, double scale) {
        float from = Math.max(0.0F, inner);
        if (!(outer > from)) {
            return;
        }
        int segments = ScopeLensGeometry.ringSegments(outer * scale);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        double step = 2.0 * Math.PI / segments;
        float cos0 = 1.0F;
        float sin0 = 0.0F;
        for (int i = 1; i <= segments; i++) {
            float cos1 = (float) Math.cos(step * i);
            float sin1 = (float) Math.sin(step * i);
            buffer.vertex(matrix, centerX + from * cos0, centerY + from * sin0, 0.0F).color(innerColor);
            buffer.vertex(matrix, centerX + from * cos1, centerY + from * sin1, 0.0F).color(innerColor);
            buffer.vertex(matrix, centerX + outer * cos1, centerY + outer * sin1, 0.0F).color(outerColor);
            buffer.vertex(matrix, centerX + outer * cos0, centerY + outer * sin0, 0.0F).color(outerColor);
            cos0 = cos1;
            sin0 = sin1;
        }
        context.draw();
    }
}
