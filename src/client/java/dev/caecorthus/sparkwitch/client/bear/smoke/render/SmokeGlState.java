package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Snapshot of every piece of raw GL state the smoke passes touch, read back from GL and restored to exactly those
 * values. Vanilla's {@code GlStateManager} and {@code BufferRenderer} cache program, VAO, blend, depth, cull, scissor,
 * colour mask, viewport, active unit and 2D bindings; restoring the GL values they saw keeps those caches true.
 * Render thread only; not reentrant (one instance per pass).
 * 烟雾各步骤改动的全部原生 GL 状态的快照：从 GL 读回，并原样恢复。原版 {@code GlStateManager} 与 {@code BufferRenderer}
 * 缓存了程序、VAO、混合、深度、剔除、裁剪、颜色掩码、视口、活动纹理单元与 2D 绑定；恢复它们所见的 GL 值即可保持这些
 * 缓存正确。仅限渲染线程；不可重入（每个步骤一个实例）。
 */
final class SmokeGlState {
    /** Texture units the smoke programs bind (0 .. UNITS-1). / 烟雾程序绑定的纹理单元（0 .. UNITS-1）。 */
    static final int UNITS = 10;

    private int program;
    private int vertexArray;
    private int drawFramebuffer;
    private int readFramebuffer;
    private int activeTexture;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private boolean blend;
    private boolean depthTest;
    private boolean cullFace;
    private boolean scissorTest;
    private boolean stencilTest;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEquationRgb;
    private int blendEquationAlpha;
    private final boolean[] colorMask = new boolean[4];
    private final int[] texture2d = new int[UNITS];
    private final int[] texture3d = new int[UNITS];

    void save() {
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) {
                colorMask[i] = mask.get(i) != 0;
            }
        }
        for (int unit = 0; unit < UNITS; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            texture2d[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            texture3d[unit] = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        }
        GL13.glActiveTexture(activeTexture);
    }

    /** Puts the state every smoke pass starts from. / 设置每个烟雾步骤的起始状态。 */
    static void prepareFullscreenPass() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
    }

    void restore() {
        for (int unit = UNITS - 1; unit >= 0; unit--) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture3d[unit]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d[unit]);
        }
        GL13.glActiveTexture(activeTexture);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        set(GL11.GL_BLEND, blend);
        set(GL11.GL_DEPTH_TEST, depthTest);
        set(GL11.GL_CULL_FACE, cullFace);
        set(GL11.GL_SCISSOR_TEST, scissorTest);
        set(GL11.GL_STENCIL_TEST, stencilTest);
        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
    }

    private static void set(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
