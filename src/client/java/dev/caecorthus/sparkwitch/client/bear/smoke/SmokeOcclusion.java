package dev.caecorthus.sparkwitch.client.bear.smoke;

import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeOcclusionMarch;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

/**
 * Frozen contract (render thread only): the screen-space smoke occlusion texture that hides outline highlights behind
 * smoke, pixel by pixel (owner 2026-11-05: no faction sees instinct through smoke; only the covered part of a body is
 * hidden; a thin veil fades a highlight in proportion). For every pixel it records how optical depth piles up along the
 * view ray, IGNORING world depth, because outlines are drawn through walls. Format {@code GL_RGBA16F}, one channel per
 * checkpoint {@code i} ({@link #checkpointOpticalDepth}: 0.3 / 0.7 / 1.1 / 1.5):
 * <ul>
 *   <li>the eye distance in blocks where the accumulated optical depth reaches checkpoint {@code i};</li>
 *   <li>if it never does but checkpoint {@code i - 1} was reached: MINUS the ray's total optical depth (a value in
 *   {@code (-checkpoint(i), -checkpoint(i - 1)]}), so a body behind a thin veil knows exactly how thin it is;</li>
 *   <li>otherwise {@link #NEVER}.</li>
 * </ul>
 * The optical depth at eye distance {@code d} is piecewise linear between reached checkpoints and equals the ray total
 * past the last one (an upper bound, so it fails closed); an outline fragment keeps alpha
 * {@code 1 - clamp((depth - FADE_START) / (HIDDEN - FADE_START), 0, 1)}. Texel {@code (x, y)} covers the same normalised
 * screen position as the main framebuffer; it may be lower resolution. {@link #prepare} must be called once per
 * {@code WorldRenderer#render} call (the scope picture-in-picture pass renders the world a second time with its own
 * camera and projection) before {@link #textureId()} is read. Implemented by {@code occlusion.SmokeOcclusionMarch};
 * only the rectangle of the screen the smoke can touch is marched, the rest is {@link #NEVER}.
 * 冻结契约（仅渲染线程）：屏幕空间烟雾遮挡纹理，用于逐像素擦掉烟后的描边高亮（所有者 2026-11-05：任何阵营都不能透过烟
 * 用本能透视；身体只被挡住的那部分消失；薄烟按比例变淡）。每个像素记录视线方向上光学厚度如何累积，且忽略世界深度，因为
 * 描边本就穿墙绘制。格式 {@code GL_RGBA16F}，每个检查点 {@code i}（{@link #checkpointOpticalDepth}：0.3 / 0.7 / 1.1 / 1.5）
 * 一个通道：累积光学厚度达到检查点 {@code i} 时的视距（格）；若始终达不到但达到了检查点 {@code i - 1}，则存整条视线
 * 总光学厚度的相反数（位于 {@code (-检查点(i), -检查点(i - 1)]}），使薄烟后的身体准确知道烟有多薄；否则为 {@link #NEVER}。
 * 视距 {@code d} 处的光学厚度在已达到的检查点之间分段线性，超过最后一个已达到的检查点后取视线总量（上界，失败即遮挡）；
 * 描边片元保留 alpha {@code 1 - clamp((厚度 - FADE_START) / (HIDDEN - FADE_START), 0, 1)}。纹素 {@code (x, y)} 与主帧缓冲
 * 同一归一化屏幕位置，可以更低分辨率。每次 {@code WorldRenderer#render} 调用都要先调用一次 {@link #prepare}（瞄具画中画会用
 * 自己的相机与投影再渲染一遍世界），然后才能读取 {@link #textureId()}。由 {@code occlusion.SmokeOcclusionMarch} 实现；只步进
 * 烟可能触及的屏幕矩形，其余为 {@link #NEVER}。
 */
public final class SmokeOcclusion {
    /** Optical depth where an outline starts to fade (checkpoint 0). / 描边开始变淡的光学厚度（检查点 0）。 */
    public static final float FADE_START_OPTICAL_DEPTH = 0.3F;
    /**
     * Optical depth where an outline is fully hidden (last checkpoint; about one block of full smoke).
     * 描边完全消失的光学厚度（最后一个检查点；约一格满浓度烟）。
     */
    public static final float HIDDEN_OPTICAL_DEPTH = 1.5F;
    /** Checkpoints per texel, evenly spaced from fade start to hidden. / 每个纹素的检查点数，从开始变淡到消失等距分布。 */
    public static final int CHECKPOINT_COUNT = 4;
    /** Distance value meaning "never reached". / 表示“永远达不到”的距离值。 */
    public static final float NEVER = 65000.0F;

    private SmokeOcclusion() {
    }

    /** Optical depth of checkpoint {@code index} (0.3, 0.7, 1.1, 1.5). / 第 {@code index} 个检查点的光学厚度。 */
    public static float checkpointOpticalDepth(int index) {
        return FADE_START_OPTICAL_DEPTH
                + (HIDDEN_OPTICAL_DEPTH - FADE_START_OPTICAL_DEPTH) * index / (CHECKPOINT_COUNT - 1);
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
