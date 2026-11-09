package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Snapshot of every piece of GL state the smoke occlusion passes touch, restored exactly afterwards. Cached state
 * (blend, depth, cull, scissor, masks, active unit, 2D bindings, viewport) is set and restored through
 * {@link GlStateManager} so its cache never drifts from GL; program, VAO, framebuffers and 3D bindings have no vanilla
 * cache and are restored raw to the queried values, which keeps {@code ShaderProgram} and {@code BufferRenderer}'s
 * "already bound" shortcuts valid.
 * 烟雾遮挡各步骤会改动的全部 GL 状态快照，结束后原样恢复。带缓存的状态（混合、深度、剔除、裁剪、写掩码、活动纹理单元、
 * 2D 绑定、视口）一律经 {@link GlStateManager} 设置与恢复，缓存不会与 GL 脱节；程序、VAO、帧缓冲与 3D 绑定没有原版缓存，
 * 按查询值原样恢复，使 {@code ShaderProgram} 与 {@code BufferRenderer} 的“已绑定”捷径继续成立。
 */
final class SmokeGlState {
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int program;
    private final int vertexArray;
    private final int activeTexture;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final boolean blend;
    private final boolean depthTest;
    private final boolean cull;
    private final boolean scissor;
    private final boolean depthMask;
    private final boolean[] colorMask = new boolean[4];
    private final int units2d;
    private final int units3d;
    private final int[] bound2d;
    private final int[] bound3d;

    private SmokeGlState(int units2d, int units3d) {
        this.units2d = units2d;
        this.units3d = units3d;
        drawFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        program = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
        vertexArray = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        activeTexture = GlStateManager._getActiveTexture();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer four = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, four);
            four.get(viewport);
            four.clear();
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, four);
            four.get(scissorBox);
            var bytes = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, bytes);
            for (int i = 0; i < 4; i++) {
                colorMask[i] = bytes.get(i) != 0;
            }
        }
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int units = Math.max(units2d, units3d);
        bound2d = new int[units2d];
        bound3d = new int[units3d];
        for (int unit = 0; unit < units; unit++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            if (unit < units2d) {
                bound2d[unit] = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            if (unit < units3d) {
                bound3d[unit] = GlStateManager._getInteger(GL12.GL_TEXTURE_BINDING_3D);
            }
        }
        GlStateManager._activeTexture(activeTexture);
    }

    /** Captures the state, then sets the plain fullscreen-pass state. / 捕获状态，再设置普通全屏步骤所需状态。 */
    static SmokeGlState captureForFullscreenPass(int units2d, int units3d) {
        SmokeGlState state = new SmokeGlState(units2d, units3d);
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        GlStateManager._disableScissorTest();
        GlStateManager._depthMask(false);
        GlStateManager._colorMask(true, true, true, true);
        return state;
    }

    void restore() {
        int units = Math.max(units2d, units3d);
        for (int unit = 0; unit < units; unit++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            if (unit < units3d) {
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, bound3d[unit]);
            }
            if (unit < units2d) {
                GlStateManager._bindTexture(bound2d[unit]);
            }
        }
        GlStateManager._activeTexture(activeTexture);
        GL30.glBindVertexArray(vertexArray);
        GlStateManager._glUseProgram(program);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        set(blend, GlStateManager::_enableBlend, GlStateManager::_disableBlend);
        set(depthTest, GlStateManager::_enableDepthTest, GlStateManager::_disableDepthTest);
        set(cull, GlStateManager::_enableCull, GlStateManager::_disableCull);
        set(scissor, GlStateManager::_enableScissorTest, GlStateManager::_disableScissorTest);
        GlStateManager._depthMask(depthMask);
        GlStateManager._colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
    }

    /** Binds a 2D texture on a unit through the cache. / 经缓存在某单元上绑定 2D 纹理。 */
    static void bind2d(int unit, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(texture);
    }

    /** Binds a 3D texture on a unit (no vanilla cache). / 在某单元上绑定 3D 纹理（原版无缓存）。 */
    static void bind3d(int unit, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
    }

    /** Scissors subsequent draws to a rectangle {@code [x0, y0, x1, y1)}. / 把后续绘制裁剪到矩形内。 */
    static void scissor(int[] rect) {
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1]);
    }

    private static void set(boolean enabled, Runnable enable, Runnable disable) {
        if (enabled) {
            enable.run();
        } else {
            disable.run();
        }
    }
}
