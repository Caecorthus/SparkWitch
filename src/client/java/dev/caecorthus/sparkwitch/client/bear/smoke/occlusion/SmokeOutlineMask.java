package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeOcclusion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Hides every highlight in the vanilla entity-outline target behind BEAR smoke, pixel by pixel (owner 2026-11-05: no
 * faction sees a player through smoke; a body half behind smoke loses only that half's highlight). Instinct, role
 * x-ray, faction colours, corpses, dropped items, vanilla glowing and the M67 warning all draw into that one target,
 * so masking the target covers them all; the Blind's echolocation and server line-of-sight are untouched. Per
 * {@code WorldRenderer#render} call (main view and the scope lens alike):
 * <ol>
 *   <li>{@code render} HEAD, {@link #beginPass}: cheap CPU check whether smoke can touch this pass at all. Only then
 *   is the pass armed; without smoke nothing below runs, so outlines stay pixel-identical.</li>
 *   <li>While armed, outline layers draw with {@code LEQUAL} depth instead of vanilla's untested {@code ALWAYS}
 *   ({@link #drawWithSilhouetteDepth}), so the outline target's depth (cleared at the start of {@code render}, never
 *   holding world depth) ends up holding the NEAREST silhouette. Walls still do not occlude outlines; only where two
 *   outlined bodies overlap on screen does the nearer one's colour now win instead of the last drawn.</li>
 *   <li>Before the entity_outline post chain (which clears that depth): {@link SmokeOcclusion#prepare} marches the
 *   occlusion texture, and the silhouette depth inside the smoke rectangle is copied aside.</li>
 *   <li>After the chain: the post-processed outline colour is copied aside and {@code outline_mask.fsh} rewrites the
 *   smoke rectangle with alpha scaled by the smoke visibility at each outline pixel's silhouette distance: linear in the
 *   optical depth decoded from the checkpoint texel, so a thin veil fades a highlight in proportion.</li>
 * </ol>
 * Every composite of the target ({@code drawEntityOutlinesFramebuffer}: vanilla, the Black Raven, Seeker and Rift
 * filters, the scope lens) therefore reads the masked result. If the GL pipeline fails, the smoke rectangle of the
 * outline target is cleared instead (fail closed).
 * 逐像素擦掉原版实体描边目标中烟后的一切高亮（所有者 2026-11-05：任何阵营都不能透过烟看到玩家；身体一半在烟后只丢
 * 那一半的高亮）。本能、职业透视、阵营颜色、尸体、掉落物、原版发光与 M67 警示都画进这同一个目标，遮住它即覆盖全部；
 * 盲人回声定位与服务端视线判定不受影响。每次 {@code WorldRenderer#render} 调用（主画面与瞄具镜内相同）：
 * 1. {@code render} HEAD 的 {@link #beginPass}：廉价的 CPU 判断烟是否可能触及本次渲染，只有此时才进入就绪状态；无烟时以下
 * 步骤都不运行，描边逐像素不变。2. 就绪期间描边层改用 {@code LEQUAL} 深度测试而非原版不启用测试的 {@code ALWAYS}
 * （{@link #drawWithSilhouetteDepth}），使描边目标的深度（{@code render} 开头已清除，从不含世界深度）保存最近的剪影。墙体仍不
 * 遮挡描边；只有两个描边身体在屏幕上重叠时，颜色改为较近者而非最后绘制者。3. entity_outline 后处理链（会清除该深度）之前：
 * {@link SmokeOcclusion#prepare} 步进生成遮挡纹理，并把烟雾矩形内的剪影深度另存。4. 后处理之后：另存处理后的描边颜色，
 * 由 {@code outline_mask.fsh} 按每个描边像素所属剪影距离处的烟雾可见度缩放 alpha（与从检查点纹素解出的光学厚度成线性，
 * 薄烟按比例变淡），重写烟雾矩形。因此该目标的所有合成
 * （原版、黑羽鸦、探索者与隙行者滤镜、瞄具镜内的 {@code drawEntityOutlinesFramebuffer}）读到的都是遮罩后的结果。GL 管线
 * 失败时改为清空描边目标中的烟雾矩形（失败即遮挡）。
 */
public final class SmokeOutlineMask {
    private static boolean armed;
    private static boolean masking;
    private static Camera camera;
    private static final Matrix4f POSITION = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static int passWidth;
    private static int passHeight;
    private static int[] maskRect;
    private static SimpleFramebuffer scratch;
    private static SmokeGlProgram program;
    private static boolean broken;

    private SmokeOutlineMask() {
    }

    /**
     * {@code WorldRenderer#render} HEAD: remembers this pass's camera and matrices and arms the mask when smoke can
     * touch the pass. The pass size is {@code client.getFramebuffer()}, which the scope lens swaps for its own target.
     * {@code WorldRenderer#render} HEAD：记录本次渲染的相机与矩阵，烟可能触及本次渲染时进入就绪状态。渲染尺寸取
     * {@code client.getFramebuffer()}，瞄具镜内渲染会把它换成镜内目标。
     */
    public static void beginPass(Camera passCamera, Matrix4f positionMatrix, Matrix4f projection) {
        armed = false;
        masking = false;
        if (SmokeClientState.isEmpty()) {
            return;
        }
        Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
        passWidth = target.textureWidth;
        passHeight = target.textureHeight;
        camera = passCamera;
        POSITION.set(positionMatrix);
        PROJECTION.set(projection);
        armed = SmokeOcclusionMarch.coverage(passCamera, positionMatrix, projection, passWidth, passHeight) != null;
    }

    /** True while outline layers must keep the nearest silhouette depth. / 描边层需要保留最近剪影深度时为 true。 */
    public static boolean capturingSilhouetteDepth() {
        return armed;
    }

    /**
     * Runs one outline-layer draw with {@code LEQUAL} depth test and depth writes on, then restores the depth state
     * it found (vanilla's {@code ALWAYS} phase leaves it untouched).
     * 以 {@code LEQUAL} 深度测试并开启深度写入执行一次描边层绘制，然后恢复原有深度状态（原版 {@code ALWAYS} 阶段不改动它）。
     */
    public static void drawWithSilhouetteDepth(Runnable draw) {
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int depthFunc = GlStateManager._getInteger(GL11.GL_DEPTH_FUNC);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        try {
            draw.run();
        } finally {
            if (!depthTest) {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(depthMask);
        }
    }

    /**
     * Just before the entity_outline post chain: marches the occlusion texture and copies the silhouette depth that
     * the chain is about to clear.
     * entity_outline 后处理链之前：步进生成遮挡纹理，并复制后处理即将清除的剪影深度。
     */
    public static void beforeOutlinePost(Framebuffer outline) {
        masking = false;
        maskRect = null;
        if (!armed || outline == null) {
            return;
        }
        if (!SmokeOcclusion.prepare(camera, POSITION, PROJECTION, passWidth, passHeight)) {
            int[] failed = SmokeOcclusionMarch.failedRect();
            if (failed != null) {
                maskRect = failed;
            }
            return;
        }
        int width = Math.min(passWidth, outline.textureWidth);
        int height = Math.min(passHeight, outline.textureHeight);
        maskRect = SmokeOcclusionMath.grow(SmokeOcclusionMarch.pixelRect(), 2 * SmokeOcclusionMath.MARCH_DIVISOR,
                width, height);
        if (broken) {
            return;
        }
        SmokeGlState state = SmokeGlState.captureForFullscreenPass(0, 0);
        try {
            ensureScratch(outline);
            int[] depthRect = SmokeOcclusionMath.grow(maskRect, SmokeOcclusionMath.SILHOUETTE_SEARCH_RADIUS + 1,
                    width, height);
            blit(outline, depthRect, GL11.GL_DEPTH_BUFFER_BIT);
            masking = true;
        } catch (RuntimeException exception) {
            fail(exception);
        } finally {
            state.restore();
        }
    }

    /**
     * Right after the entity_outline post chain: rewrites the smoke rectangle of the outline target with masked
     * alpha, or clears it when the pipeline is failing.
     * entity_outline 后处理链之后：以遮罩后的 alpha 重写描边目标的烟雾矩形；管线失败时清空该矩形。
     */
    public static void afterOutlinePost(Framebuffer outline) {
        int[] rect = maskRect;
        boolean ok = masking;
        masking = false;
        maskRect = null;
        if (rect == null || outline == null || rect[2] <= rect[0] || rect[3] <= rect[1]) {
            return;
        }
        SmokeGlState state = SmokeGlState.captureForFullscreenPass(3, 0);
        try {
            if (ok && !broken) {
                try {
                    mask(outline, rect);
                    return;
                } catch (RuntimeException exception) {
                    fail(exception);
                }
            }
            clear(outline, rect);
        } finally {
            state.restore();
        }
    }

    /** {@code WorldRenderer#render} RETURN. / {@code WorldRenderer#render} RETURN。 */
    public static void endPass() {
        armed = false;
        masking = false;
        maskRect = null;
        camera = null;
    }

    private static void mask(Framebuffer outline, int[] rect) {
        if (program == null) {
            program = SmokeGlProgram.load("fullscreen.vsh", "outline_mask.fsh");
            program.use();
            program.setInt("OutlineColor", 0);
            program.setInt("SilhouetteDepth", 1);
            program.setInt("Occlusion", 2);
        }
        blit(outline, rect, GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outline.fbo);
        GlStateManager._viewport(0, 0, SmokeOcclusionMarch.passWidth(), SmokeOcclusionMarch.passHeight());
        SmokeGlState.scissor(rect);
        program.use();
        SmokeGlState.bind2d(0, scratch.getColorAttachment());
        SmokeGlState.bind2d(1, scratch.getDepthAttachment());
        SmokeGlState.bind2d(2, SmokeOcclusion.textureId());
        program.setMatrix("InverseViewProjection", SmokeOcclusionMarch.inverseViewProjection());
        program.setVec2("PassSize", SmokeOcclusionMarch.passWidth(), SmokeOcclusionMarch.passHeight());
        program.setVec2("OcclusionSize", SmokeOcclusion.width(), SmokeOcclusion.height());
        program.setFloatArray("Checkpoints", SmokeOcclusionMarch.CHECKPOINTS);
        program.setFloat("Never", SmokeOcclusion.NEVER);
        SmokeGlProgram.drawFullscreenTriangle();
    }

    /** Fail closed: no highlight survives inside the smoke rectangle. / 失败即遮挡：烟雾矩形内不留任何高亮。 */
    private static void clear(Framebuffer outline, int[] rect) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outline.fbo);
        SmokeGlState.scissor(rect);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0.0F, 0.0F, 0.0F, 0.0F});
    }

    private static void blit(Framebuffer outline, int[] rect, int mask) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, outline.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratch.fbo);
        GlStateManager._glBlitFrameBuffer(rect[0], rect[1], rect[2], rect[3], rect[0], rect[1], rect[2], rect[3],
                mask, GL11.GL_NEAREST);
    }

    /**
     * Same size and formats as the outline target (both vanilla framebuffers), so depth can be blitted across.
     * 与描边目标同尺寸同格式（都是原版帧缓冲），深度才能直接 blit。
     */
    private static void ensureScratch(Framebuffer outline) {
        if (scratch == null) {
            scratch = new SimpleFramebuffer(outline.textureWidth, outline.textureHeight, true,
                    MinecraftClient.IS_SYSTEM_MAC);
        } else if (scratch.textureWidth != outline.textureWidth || scratch.textureHeight != outline.textureHeight) {
            scratch.resize(outline.textureWidth, outline.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
        }
    }

    private static void fail(RuntimeException exception) {
        broken = true;
        masking = false;
        SparkWitch.LOGGER.error("Smoke outline mask failed; outlines inside smoke are hidden instead", exception);
    }
}
