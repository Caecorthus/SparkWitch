package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Offscreen GL objects of one view (the main view, or a secondary world pass such as the scope lens, which has its own
 * size): the half-resolution march target (RGBA16F premultiplied colour + transmittance, R32F eye distance) and a
 * composite framebuffer that wraps ONLY the world target's colour texture (so the composite may sample that target's
 * depth texture without a feedback loop). The march target is sized lazily and rebuilt on resize; the composite
 * framebuffer takes the world colour texture anew on every bind ({@link #bindComposite}). Freed by {@link #release()}.
 * Render thread only.
 * 一个视图（主画面，或瞄具镜内等自带尺寸的第二次世界渲染）的离屏 GL 对象：半分辨率步进目标（RGBA16F 预乘颜色 + 透射率，
 * R32F 视距），以及只包装世界目标颜色纹理的合成帧缓冲（因此合成时可采样该目标的深度纹理而不形成反馈回路）。步进目标按需分配、
 * 尺寸变化时重建；合成帧缓冲每次绑定都重新挂接世界颜色纹理（{@link #bindComposite}）。由 {@link #release()} 释放。仅限渲染线程。
 */
final class SmokeViewTargets {
    private int lowFramebuffer;
    private int lowColor;
    private int lowDepth;
    private int lowWidth;
    private int lowHeight;
    private int compositeFramebuffer;

    /** Ensures the half-resolution march target for a full-resolution size. / 确保与全分辨率尺寸对应的半分辨率步进目标。 */
    void ensureLow(int fullWidth, int fullHeight) {
        int width = SmokeMarchRules.halfSize(fullWidth);
        int height = SmokeMarchRules.halfSize(fullHeight);
        if (lowFramebuffer != 0 && width == lowWidth && height == lowHeight) {
            return;
        }
        releaseLow();
        lowColor = texture(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, width, height, GL11.GL_LINEAR);
        // Linear: the composite's flatness test reads it bilinearly; edge taps use texelFetch.
        // 线性过滤：合成的平坦判定以双线性读取它；边缘采样使用 texelFetch。
        lowDepth = texture(GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, width, height, GL11.GL_LINEAR);
        lowFramebuffer = framebuffer(lowColor, lowDepth);
        lowWidth = width;
        lowHeight = height;
    }

    /**
     * Binds (as {@code GL_FRAMEBUFFER}) the framebuffer whose single attachment is {@code colorTexture} (the world
     * target's colour), attaching that texture again on EVERY call. A resize (window, F11) makes the target delete its
     * colour texture and create a new one, which GL usually hands the SAME name: a framebuffer kept by texture name
     * would stay attached to the deleted texture, still complete, and the composite would draw nowhere (no visible
     * smoke while the outline mask keeps hiding outlines). The caller has saved the framebuffer bindings.
     * 绑定（作为 {@code GL_FRAMEBUFFER}）唯一附件为 {@code colorTexture}（世界目标的颜色纹理）的帧缓冲，并且每次调用都重新挂接该
     * 纹理。尺寸变化（窗口、F11）会让目标删除颜色纹理并新建一张，而 GL 通常分配同一个名字：按纹理名缓存的帧缓冲会继续挂着已删除的
     * 纹理且仍然完整，合成便画不到任何地方（看不见烟，而描边遮罩照常隐藏描边）。调用方已保存帧缓冲绑定。
     */
    void bindComposite(int colorTexture) {
        if (compositeFramebuffer == 0) {
            compositeFramebuffer = framebuffer(colorTexture, 0);
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, compositeFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture,
                0);
    }

    int lowFramebuffer() {
        return lowFramebuffer;
    }

    int lowColor() {
        return lowColor;
    }

    int lowDepth() {
        return lowDepth;
    }

    int lowWidth() {
        return lowWidth;
    }

    int lowHeight() {
        return lowHeight;
    }

    void release() {
        releaseLow();
        if (compositeFramebuffer != 0) {
            GL30.glDeleteFramebuffers(compositeFramebuffer);
            compositeFramebuffer = 0;
        }
    }

    private void releaseLow() {
        if (lowFramebuffer != 0) {
            GL30.glDeleteFramebuffers(lowFramebuffer);
            lowFramebuffer = 0;
        }
        if (lowColor != 0) {
            GlStateManager._deleteTexture(lowColor);
            lowColor = 0;
        }
        if (lowDepth != 0) {
            GlStateManager._deleteTexture(lowDepth);
            lowDepth = 0;
        }
        lowWidth = 0;
        lowHeight = 0;
    }

    /** Called with the caller's GL state saved; restores the unit-0 2D binding itself. / 调用方已保存 GL 状态；本方法自行恢复 0 号单元的 2D 绑定。 */
    private static int texture(int internalFormat, int format, int type, int width, int height, int filter) {
        int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int id = GlStateManager._genTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        GL13.glActiveTexture(previousUnit);
        return id;
    }

    private static int framebuffer(int color0, int color1) {
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int id = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color0, 0);
        if (color1 != 0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, color1, 0);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1});
        } else {
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        }
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glDeleteFramebuffers(id);
            throw new IllegalStateException("BEAR smoke framebuffer incomplete: 0x" + Integer.toHexString(status));
        }
        return id;
    }
}
