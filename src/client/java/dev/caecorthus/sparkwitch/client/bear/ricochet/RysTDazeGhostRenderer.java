package dev.caecorthus.sparkwitch.client.bear.ricochet;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Client only: the D5 double vision, drawn from {@code RicochetDazeHudMixin} at {@code InGameHud.render} HEAD, i.e.
 * after the world, the hand and every default-priority HUD-HEAD overlay (the Rys-T visor) but before any HUD layer. The
 * finished frame is blitted into a private copy, then drawn back over the main target twice (shifted along the
 * {@link RysTDazeCurve.Kick} ghost direction and half as far the other way) with fading alpha. Idle frames return
 * before touching GL; the copy is freed when the daze ends or on disconnect. Every GL value touched is read back first
 * and restored; any exception (an Iris pipeline, a failed framebuffer) disables the ghost for the rest of the game,
 * the jolt keeps working.
 * 仅客户端：D5 重影，由 {@code RicochetDazeHudMixin} 在 {@code InGameHud.render} HEAD 绘制，即世界、手部及所有默认优先级的
 * HUD HEAD 叠加层（Rys-T 面罩）之后、任何 HUD 层之前。把已完成的画面 blit 到私有副本，再以渐隐透明度两次画回主目标（沿
 * {@link RysTDazeCurve.Kick} 的重影方向偏移，另一次反向偏移一半）。空闲帧在触及 GL 之前返回；眩晕结束或断线时释放副本。
 * 改动的每个 GL 值都先读回再恢复；任何异常（Iris 管线、帧缓冲创建失败）都会在本次游戏余下时间里禁用重影，震动照常。
 */
public final class RysTDazeGhostRenderer {
    /** The mirrored second ghost: offset and opacity relative to the first. / 反向的第二道重影：相对第一道的偏移与不透明度。 */
    static final float SECOND_GHOST_OFFSET = -0.5F;
    static final float SECOND_GHOST_ALPHA = 0.5F;
    /** Below this opacity or pixel shift the ghost is invisible and skipped. / 低于此不透明度或像素偏移时重影不可见，跳过。 */
    static final float MIN_ALPHA = 0.004F;
    static final float MIN_SHIFT_PIXELS = 0.25F;

    private static SimpleFramebuffer copy;
    private static boolean broken;

    private RysTDazeGhostRenderer() {
    }

    /** Render thread, {@code InGameHud.render} HEAD. / 渲染线程，{@code InGameHud.render} HEAD。 */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (broken || !RysTDazeClient.active()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getCameraEntity() != client.player) {
            return;
        }
        RysTDazeCurve.Pose pose = RysTDazeClient.pose(tickCounter.getTickDelta(false));
        Framebuffer main = client.getFramebuffer();
        int width = main.textureWidth;
        int height = main.textureHeight;
        float shiftPixels = (float) Math.hypot(pose.ghostX(), pose.ghostY()) * height;
        if (pose.ghostAlpha() < MIN_ALPHA || shiftPixels < MIN_SHIFT_PIXELS || width <= 0 || height <= 0) {
            return;
        }
        // Flush overlays batched before us (the visor) so the copy holds them. / 先冲刷之前批量提交的叠加层（面罩），使副本包含它们。
        context.draw();
        GhostGlState saved = GhostGlState.save();
        try {
            ensureCopy(width, height);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
            GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST);
            main.beginWrite(false);
            GlStateManager._viewport(saved.viewport[0], saved.viewport[1], saved.viewport[2], saved.viewport[3]);
            drawGhosts(context, pose);
        } catch (RuntimeException exception) {
            broken = true;
            SparkWitch.LOGGER.error("Rys-T double vision failed; the daze keeps its view jolt only", exception);
        } finally {
            saved.restore();
        }
        if (broken) {
            release();
        }
    }

    /** Frees the copy (render thread, outside any pass). / 释放副本（渲染线程，不在任何绘制步骤中）。 */
    static void release() {
        if (copy == null) {
            return;
        }
        int deleted = copy.fbo;
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        // Framebuffer#delete unbinds every framebuffer; put back whatever was bound. / Framebuffer#delete 会解绑全部帧缓冲；恢复原绑定。
        copy.delete();
        copy = null;
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw == deleted ? 0 : draw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read == deleted ? 0 : read);
    }

    private static void ensureCopy(int width, int height) {
        if (copy == null) {
            copy = new SimpleFramebuffer(width, height, false, MinecraftClient.IS_SYSTEM_MAC);
            copy.setTexFilter(GL11.GL_LINEAR);
        } else if (copy.textureWidth != width || copy.textureHeight != height) {
            copy.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
            copy.setTexFilter(GL11.GL_LINEAR);
        }
    }

    private static void drawGhosts(DrawContext context, RysTDazeCurve.Pose pose) {
        float screenWidth = context.getScaledWindowWidth();
        float screenHeight = context.getScaledWindowHeight();
        float offsetX = pose.ghostX() * screenHeight;
        float offsetY = pose.ghostY() * screenHeight;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // Keep the destination alpha: only colour is ghosted. / 保留目标 alpha：只给颜色加重影。
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, copy.getColorAttachment());
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR);
        quad(buffer, matrix, screenWidth, screenHeight, offsetX, offsetY, pose.ghostAlpha());
        quad(buffer, matrix, screenWidth, screenHeight, offsetX * SECOND_GHOST_OFFSET, offsetY * SECOND_GHOST_OFFSET,
                pose.ghostAlpha() * SECOND_GHOST_ALPHA);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /**
     * A full-screen quad whose image is shifted by {@code (dx, dy)} GUI units (y down): the UVs move, not the quad, so
     * the clamped edge fills the strip the shift uncovers. Framebuffer textures are bottom-up, hence the flipped v.
     * 一个全屏四边形，其图像偏移 {@code (dx, dy)} 个 GUI 单位（y 向下）：移动的是 UV 而非四边形，夹取的边缘会填满偏移露出的
     * 条带。帧缓冲纹理自下而上存储，因此 v 翻转。
     */
    private static void quad(BufferBuilder buffer, Matrix4f matrix, float width, float height, float dx, float dy,
                             float alpha) {
        float u0 = -dx / width;
        float u1 = 1.0F - dx / width;
        float vTop = 1.0F + dy / height;
        float vBottom = dy / height;
        buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).texture(u0, vTop).color(1.0F, 1.0F, 1.0F, alpha);
        buffer.vertex(matrix, 0.0F, height, 0.0F).texture(u0, vBottom).color(1.0F, 1.0F, 1.0F, alpha);
        buffer.vertex(matrix, width, height, 0.0F).texture(u1, vBottom).color(1.0F, 1.0F, 1.0F, alpha);
        buffer.vertex(matrix, width, 0.0F, 0.0F).texture(u1, vTop).color(1.0F, 1.0F, 1.0F, alpha);
    }

    /**
     * The GL state the ghost pass touches, read from GL and restored through {@code GlStateManager} /
     * {@code RenderSystem} so their caches stay true. / 重影步骤改动的 GL 状态：从 GL 读回，并经 {@code GlStateManager}/{@code RenderSystem}
     * 恢复，使其缓存保持正确。
     */
    private static final class GhostGlState {
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int[] viewport = new int[4];
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final float[] shaderColor = RenderSystem.getShaderColor().clone();

        private GhostGlState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        }

        static GhostGlState save() {
            return new GhostGlState();
        }

        void restore() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(depthMask);
            RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        }
    }
}
