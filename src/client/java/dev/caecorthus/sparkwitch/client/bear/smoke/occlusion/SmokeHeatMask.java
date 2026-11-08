package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeOcclusion;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * BEAR smoke inside another mod's heat image (BEAR WP5; SparkStrength's Undercover thermal, owner S4 "hot smoke blocks
 * thermal", owner Q6 "a bright white blob that also hides bodies"), behind {@code api.client.BearSmokeViewApi}. The caller
 * owns a heat framebuffer (a colour texture whose alpha, and colour, is heat, plus a depth texture holding the world
 * depth and, where it drew bodies, their depth) and calls this during a {@code WorldRenderer#render} pass, after it drew
 * its bodies. Per call (render thread only):
 * <ol>
 *   <li>Without smoke in the pass ({@link SmokeOutlineMask#passCoverage()} null) nothing is touched.</li>
 *   <li>The {@link SmokeOcclusion} checkpoint texture is marched over the whole smoke rectangle of the pass (the
 *   outline mask marches only outline regions), once per pass: the haze call reuses the mask call's march.</li>
 *   <li>{@code heat_mask.fsh} runs over the smoke rectangle scaled to the heat size, into a private framebuffer with
 *   the caller's colour texture attached (never its depth, which is sampled, so there is no feedback loop): the mask
 *   scales heat by the smoke visibility at each pixel's depth, the haze adds {@code haze * (1 - visibility)} with MAX
 *   blending.</li>
 * </ol>
 * Every GL state it touches is restored ({@link SmokeGlState} plus the blend function and equation), and the colour
 * texture is detached again. Fail closed: when the mask's GL path fails (logged once, then permanent) or the call
 * cannot know where the smoke is (outside a world render), the heat inside the smoke rectangle (the whole target when
 * unknown) is cleared where possible and the call returns false, so the caller hides it too ({@link #smokeRect}). A
 * failing haze only returns false. The heat target is taken to cover exactly the pass's view: {@code width} x
 * {@code height} is the whole texture, mapped to the whole screen of the current world render.
 * 另一模组热图中的 BEAR 烟雾（BEAR WP5；SparkStrength 卧底热成像，所有者 S4“热烟挡住热成像”，所有者 Q6“显示为一团亮白并遮住
 * 人体”），位于 {@code api.client.BearSmokeViewApi} 之后。调用方持有一个热图帧缓冲（颜色纹理的 alpha 与颜色即热值，深度纹理
 * 含世界深度以及其画人体之处的人体深度），在一次 {@code WorldRenderer#render} 中画完人体后调用本类。每次调用（仅渲染线程）：
 * 1. 本次渲染没有烟（{@link SmokeOutlineMask#passCoverage()} 为 null）时不做任何事。2. 对本次渲染的整个烟雾矩形步进
 * {@link SmokeOcclusion} 检查点纹理（描边遮罩只步进描边区域），每次渲染一次：热雾调用复用遮罩调用的步进。
 * 3. {@code heat_mask.fsh} 在缩放到热图尺寸的烟雾矩形内运行，写入挂上调用方颜色纹理的私有帧缓冲（从不挂其深度，深度只被
 * 采样，因此没有反馈回路）：遮罩按每个像素深度处的烟雾可见度缩放热值，热雾以 MAX 混合加上 {@code haze * (1 - 可见度)}。
 * 改动的 GL 状态全部恢复（{@link SmokeGlState} 加混合函数与方程），颜色纹理也会再次卸下。失败即遮挡：遮罩的 GL 路径失败（只
 * 记录一次，之后永久生效）或无法得知烟在哪里（不在世界渲染之中）时，尽可能清掉烟雾矩形内的热值（未知时为整个目标）并返回
 * false，调用方随即也把它藏起来（{@link #smokeRect}）。热雾失败只返回 false。热图被视为恰好覆盖本次渲染的视野：
 * {@code width} x {@code height} 是整张纹理，对应当前世界渲染的整个画面。
 */
public final class SmokeHeatMask {
    private static SmokeGlProgram maskProgram;
    private static SmokeGlProgram hazeProgram;
    private static int framebuffer;
    /**
     * A GL failure disables that pass for good: the mask then fails closed every frame; the haze is cosmetic, so its own
     * failure never touches the mask or the heat. / GL 失败会永久停用该步骤：遮罩随后每帧失败即遮挡；热雾只是外观，其自身
     * 失败从不影响遮罩或热值。
     */
    private static boolean maskBroken;
    private static boolean hazeBroken;

    private SmokeHeatMask() {
    }

    /**
     * Scales the heat behind smoke ({@code haze} &lt; 0) or adds the white-hot haze ({@code haze} in (0, 1]); see the
     * class comment. Returns true when the heat target is right (masked, or no smoke touches this pass).
     * 缩放烟后的热值（{@code haze} &lt; 0）或加上白热热雾（{@code haze} 属于 (0, 1]）；见类注释。热图正确（已遮罩，或烟不触及
     * 本次渲染）时返回 true。
     */
    public static boolean apply(int colorTexture, int depthTexture, int width, int height, float haze) {
        // NaN counts as a (bad) haze, never as the mask. / NaN 视为（无效的）热雾，绝不当作遮罩。
        boolean hazePass = !(haze < 0.0F);
        if (!SmokeHeatMath.validTarget(colorTexture, depthTexture, width, height)
                || hazePass && !SmokeHeatMath.validHaze(haze) || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        if (SmokeClientState.isEmpty()) {
            return true;
        }
        if (!SmokeOutlineMask.passOpen()) {
            // No pass: where the smoke is on screen is unknown, so the whole target counts. / 无渲染：烟的位置未知，按整个目标处理。
            if (!hazePass) {
                clearClosed(colorTexture, new int[]{0, 0, width, height});
            }
            return false;
        }
        SmokeOcclusionMarch.Coverage coverage = SmokeOutlineMask.passCoverage();
        if (coverage == null) {
            return true;
        }
        int passWidth = SmokeOutlineMask.passWidth();
        int passHeight = SmokeOutlineMask.passHeight();
        int[] rect = SmokeHeatMath.heatRect(coverage.pixelRect(), passWidth, passHeight, width, height);
        if (rect == null) {
            return true;
        }
        if ((hazePass ? hazeBroken : maskBroken)
                || !SmokeOcclusionMarch.prepareWhole(coverage, passWidth, passHeight)) {
            if (!hazePass) {
                clearClosed(colorTexture, rect);
            }
            return false;
        }
        SmokeGlState state = SmokeGlState.captureForFullscreenPass(2, 0);
        int[] blend = {
                GlStateManager._getInteger(GL14.GL_BLEND_SRC_RGB),
                GlStateManager._getInteger(GL14.GL_BLEND_DST_RGB),
                GlStateManager._getInteger(GL14.GL_BLEND_SRC_ALPHA),
                GlStateManager._getInteger(GL14.GL_BLEND_DST_ALPHA),
                GlStateManager._getInteger(GL20.GL_BLEND_EQUATION_RGB)};
        boolean drawn = false;
        try {
            SmokeGlProgram program = program(hazePass);
            attach(colorTexture);
            GlStateManager._viewport(0, 0, width, height);
            GlStateManager._enableBlend();
            if (hazePass) {
                // result = max(dst, src): the haze only ever adds heat. / 结果 = max(dst, src)：热雾只会增加热值。
                GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                GlStateManager._blendEquation(GL14.GL_MAX);
            } else {
                // result = dst * visibility on every channel. / 每个通道：结果 = dst * 可见度。
                GlStateManager._blendFuncSeparate(GL11.GL_ZERO, GL11.GL_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_SRC_ALPHA);
                GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
            }
            program.use();
            SmokeGlState.bind2d(0, depthTexture);
            SmokeGlState.bind2d(1, SmokeOcclusion.textureId());
            program.setMatrix("InverseViewProjection", SmokeOcclusionMarch.inverseViewProjection());
            program.setVec2("TargetSize", width, height);
            program.setVec2("OcclusionSize", SmokeOcclusion.width(), SmokeOcclusion.height());
            program.setFloatArray("Checkpoints", SmokeOcclusionMarch.CHECKPOINTS);
            program.setFloat("Never", SmokeOcclusion.NEVER);
            program.setFloat("Haze", hazePass ? haze : 0.0F);
            SmokeGlState.scissor(rect);
            SmokeGlProgram.drawFullscreenTriangle();
            drawn = true;
        } catch (RuntimeException exception) {
            if (hazePass) {
                hazeBroken = true;
                SparkWitch.LOGGER.error("Smoke heat haze failed; the thermal view shows no smoke haze", exception);
            } else {
                maskBroken = true;
                SparkWitch.LOGGER.error("Smoke heat mask failed; heat inside smoke is hidden instead", exception);
            }
        } finally {
            GlStateManager._blendFuncSeparate(blend[0], blend[1], blend[2], blend[3]);
            GlStateManager._blendEquation(blend[4]);
            detach();
            state.restore();
        }
        if (!drawn && !hazePass) {
            clearClosed(colorTexture, rect);
        }
        return drawn;
    }

    /**
     * Heat-target rectangle {@code [x0, y0, x1, y1)} that smoke can touch in the current pass, the whole target when
     * that is unknown (smoke exists but no world render is in progress), or null when no smoke can touch it.
     * 当前渲染中烟可能触及的热图矩形 {@code [x0, y0, x1, y1)}；无法得知时（有烟但不在世界渲染之中）为整个目标；烟不可能触及时
     * 为 null。
     */
    public static int[] smokeRect(int width, int height) {
        if (width <= 0 || height <= 0 || SmokeClientState.isEmpty()) {
            return null;
        }
        if (!SmokeOutlineMask.passOpen()) {
            return new int[]{0, 0, width, height};
        }
        SmokeOcclusionMarch.Coverage coverage = SmokeOutlineMask.passCoverage();
        return coverage == null ? null : SmokeHeatMath.heatRect(coverage.pixelRect(), SmokeOutlineMask.passWidth(),
                SmokeOutlineMask.passHeight(), width, height);
    }

    private static SmokeGlProgram program(boolean haze) {
        if (haze) {
            if (hazeProgram == null) {
                hazeProgram = load("#define HAZE\n");
            }
            return hazeProgram;
        }
        if (maskProgram == null) {
            maskProgram = load("");
        }
        return maskProgram;
    }

    private static SmokeGlProgram load(String defines) {
        SmokeGlProgram program = SmokeGlProgram.load("fullscreen.vsh", "heat_mask.fsh", defines);
        program.use();
        program.setInt("HeatDepth", 0);
        program.setInt("Occlusion", 1);
        return program;
    }

    /** Binds the private framebuffer with only the caller's colour attached. / 绑定只挂调用方颜色的私有帧缓冲。 */
    private static void attach(int colorTexture) {
        if (framebuffer == 0) {
            framebuffer = GlStateManager.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                colorTexture, 0);
        int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("smoke heat framebuffer incomplete: " + status);
        }
    }

    /** Leaves no reference to the caller's texture behind (the caller's binding is restored afterwards). / 不留引用。 */
    private static void detach() {
        if (framebuffer == 0) {
            return;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0,
                0);
    }

    /**
     * Fail closed: clears the heat inside {@code rect}, best effort (a texture that cannot be attached stays as it is,
     * and the caller's own fallback covers it). / 失败即遮挡：尽力清掉 {@code rect} 内的热值（无法挂载的纹理保持原样，由
     * 调用方自己的兜底处理）。
     */
    private static void clearClosed(int colorTexture, int[] rect) {
        SmokeGlState state = SmokeGlState.captureForFullscreenPass(0, 0);
        try {
            attach(colorTexture);
            SmokeGlState.scissor(rect);
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0.0F, 0.0F, 0.0F, 0.0F});
        } catch (RuntimeException ignored) {
            // The caller hides the rectangle itself (documented on the facade). / 调用方自行隐藏该矩形（见门面说明）。
        } finally {
            detach();
            state.restore();
        }
    }
}
