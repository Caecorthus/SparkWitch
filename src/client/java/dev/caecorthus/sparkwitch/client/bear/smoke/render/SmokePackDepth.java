package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.resource.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Iris shader pack only: the full-resolution scene depth the smoke march and composite read after the pack's final
 * pass, merged from Iris's three depths by {@code pack_depth.fsh} into an {@code R32F} texture (same values as a depth
 * texture's {@code .r}, so the frozen march and composite shaders read it unchanged):
 * <ul>
 *   <li>the copy taken before translucents, so smoke behind stained glass and the hull's translucent panels is still
 *   marched, exactly like the vanilla path;</li>
 *   <li>0 (the near plane: no smoke) where the first-person hand is: where the solid hand changed the depth between
 *   Iris's pre-hand and pre-translucent copies, or where the final depth lies in Iris's compressed hand range
 *   ({@code HandRenderer.DEPTH} = 0.125 scales hand depth into [0.5, {@link #HAND_MAX_DEPTH}]), which also catches a
 *   translucent held item. Under a pack the hand is already in the image, so it must not be smoked over.</li>
 * </ul>
 * Render thread only; the caller saves and restores GL state.
 * 仅 Iris 光影包：光影包最终处理之后烟雾步进与合成读取的全分辨率场景深度，由 {@code pack_depth.fsh} 把 Iris 的三张深度合并为
 * {@code R32F} 纹理（数值与深度纹理的 {@code .r} 相同，冻结的步进与合成着色器无需改动即可读取）：取半透明之前的副本，使彩色
 * 玻璃与车厢半透明板后的烟照样步进，与原版路径一致；第一人称手部所在处取 0（近平面：无烟），判定为实心手部改变了 Iris
 * 手部前与半透明前两份副本之间的深度，或最终深度落在 Iris 压缩后的手部区间内（{@code HandRenderer.DEPTH} = 0.125 把手部深度
 * 压到 [0.5, {@link #HAND_MAX_DEPTH}]），后者也覆盖半透明的手持物品。光影包下手部已在画面中，不得被烟盖住。仅限渲染线程；
 * 调用方负责保存与恢复 GL 状态。
 */
final class SmokePackDepth {
    /**
     * Upper bound of Iris's hand depth: 0.5 + 0.5 * 0.125. Real geometry this close is nearer than 0.12 blocks.
     * Iris 手部深度的上界：0.5 + 0.5 * 0.125。真实几何体要到这个深度需近于 0.12 格。
     */
    static final float HAND_MAX_DEPTH = 0.5625F;
    private static final int UNIT_ALL = 0;
    private static final int UNIT_OPAQUE = 1;
    private static final int UNIT_BEFORE_HAND = 2;

    private static SmokeProgram program;
    private static int vertexArray;
    private static int framebuffer;
    private static int texture;
    private static int width;
    private static int height;

    private SmokePackDepth() {
    }

    /** Writes the merged depth and returns its texture id. / 写入合并深度并返回其纹理 id。 */
    static int merge(ResourceManager resources, int depthAll, int depthOpaque, int depthBeforeHand,
                     int fullWidth, int fullHeight) throws IOException {
        if (program == null) {
            program = SmokeProgram.load(resources, "fullscreen.vsh", "pack_depth.fsh", "OutDepth");
            GL20.glUseProgram(program.id());
            program.sampler("DepthAll", UNIT_ALL);
            program.sampler("DepthOpaque", UNIT_OPAQUE);
            program.sampler("DepthBeforeHand", UNIT_BEFORE_HAND);
            GL20.glUniform1f(program.uniform("HandMaxDepth"), HAND_MAX_DEPTH);
        }
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays();
        }
        ensureTarget(fullWidth, fullHeight);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, fullWidth, fullHeight);
        SmokeGlState.prepareFullscreenPass();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL20.glUseProgram(program.id());
        bind(UNIT_ALL, depthAll);
        bind(UNIT_OPAQUE, depthOpaque);
        bind(UNIT_BEFORE_HAND, depthBeforeHand);
        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        return texture;
    }

    static void release() {
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            texture = 0;
        }
        if (program != null) {
            program.delete();
            program = null;
        }
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        width = 0;
        height = 0;
    }

    private static void ensureTarget(int fullWidth, int fullHeight) {
        if (framebuffer != 0 && fullWidth == width && fullHeight == height) {
            return;
        }
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            texture = 0;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        texture = GlStateManager._genTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, fullWidth, fullHeight, 0, GL11.GL_RED, GL11.GL_FLOAT,
                (ByteBuffer) null);
        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("BEAR smoke pack depth framebuffer incomplete: 0x"
                    + Integer.toHexString(status));
        }
        width = fullWidth;
        height = fullHeight;
    }

    private static void bind(int unit, int id) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }
}
