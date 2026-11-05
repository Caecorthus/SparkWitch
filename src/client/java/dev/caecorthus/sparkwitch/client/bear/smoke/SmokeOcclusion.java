package dev.caecorthus.sparkwitch.client.bear.smoke;

import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeOcclusionMarch;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

/**
 * Frozen contract (render thread only): the screen-space smoke occlusion texture that hides outline highlights behind
 * smoke, pixel by pixel (owner 2026-11-05: no faction sees instinct through smoke; only the covered part of a body is
 * hidden). For every pixel it stores how far along the view ray smoke has piled up, IGNORING world depth, because
 * outlines are drawn through walls. Texture format {@code GL_RG16F} (or {@code GL_RG32F}): R = eye distance in blocks
 * at which the accumulated optical depth reaches {@link #FADE_START_OPTICAL_DEPTH}, G = distance at which it reaches
 * {@link #HIDDEN_OPTICAL_DEPTH}; both are {@link #NEVER} when the ray never gets there. An outline fragment at eye
 * distance {@code d} keeps alpha {@code 1 - smoothstep(R, G, d)}. Texel {@code (x, y)} covers the same normalised
 * screen position as the main framebuffer; it may be lower resolution. {@link #prepare} must be called once per
 * {@code WorldRenderer#render} call (the scope picture-in-picture pass renders the world a second time with its own
 * camera and projection) before {@link #textureId()} is read. Implemented by {@code occlusion.SmokeOcclusionMarch};
 * only the rectangle of the screen the smoke can touch is marched, the rest is {@link #NEVER}.
 * 冻结契约（仅渲染线程）：屏幕空间烟雾遮挡纹理，用于逐像素擦掉烟后的描边高亮（所有者 2026-11-05：任何阵营都不能
 * 透过烟用本能透视；身体只被挡住的那部分消失）。每个像素记录视线方向上烟累积到多远，且忽略世界深度，因为描边本就
 * 穿墙绘制。纹理格式 {@code GL_RG16F}（或 {@code GL_RG32F}）：R = 累积光学厚度达到 {@link #FADE_START_OPTICAL_DEPTH}
 * 时的视距（格），G = 达到 {@link #HIDDEN_OPTICAL_DEPTH} 时的视距；永远达不到时为 {@link #NEVER}。视距 {@code d} 处的
 * 描边片元保留 alpha {@code 1 - smoothstep(R, G, d)}。纹素 {@code (x, y)} 与主帧缓冲同一归一化屏幕位置，可以更低分辨率。
 * 每次 {@code WorldRenderer#render} 调用都要先调用一次 {@link #prepare}（瞄具画中画会用自己的相机与投影再渲染一遍世界），
 * 然后才能读取 {@link #textureId()}。由 {@code occlusion.SmokeOcclusionMarch} 实现；只步进烟可能触及的屏幕矩形，其余为 {@link #NEVER}。
 */
public final class SmokeOcclusion {
    /** Optical depth where an outline starts to fade. / 描边开始变淡的光学厚度。 */
    public static final float FADE_START_OPTICAL_DEPTH = 0.3F;
    /** Optical depth where an outline is fully hidden (about one block of full smoke). / 描边完全消失的光学厚度（约一格满浓度烟）。 */
    public static final float HIDDEN_OPTICAL_DEPTH = 1.5F;
    /** Distance value meaning "never reached". / 表示“永远达不到”的距离值。 */
    public static final float NEVER = 65000.0F;

    private SmokeOcclusion() {
    }

    /**
     * Builds the texture for one world render. Returns false when no smoke is on screen; callers then skip masking.
     * 为一次世界渲染生成纹理。屏幕上没有烟时返回 false，调用方随即跳过遮罩。
     *
     * @param positionMatrix the view rotation matrix passed to {@code WorldRenderer#render}
     * @param projection     the projection matrix passed to {@code WorldRenderer#render}
     */
    public static boolean prepare(Camera camera, Matrix4f positionMatrix, Matrix4f projection,
                                  int framebufferWidth, int framebufferHeight) {
        return SmokeOcclusionMarch.prepare(camera, positionMatrix, projection, framebufferWidth, framebufferHeight);
    }

    /** GL texture id, valid after {@link #prepare} returned true. / GL 纹理 id，{@link #prepare} 返回 true 后有效。 */
    public static int textureId() {
        return SmokeOcclusionMarch.textureId();
    }

    public static int width() {
        return SmokeOcclusionMarch.width();
    }

    public static int height() {
        return SmokeOcclusionMarch.height();
    }
}
