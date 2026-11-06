package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeOcclusion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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
 *   ({@link #drawWithSilhouetteDepth}) into a private depth texture attached to the outline target for the pass
 *   (cleared on the first outline draw, never holding world depth), which ends up holding the NEAREST silhouette.
 *   Walls still do not occlude outlines; only where two outlined bodies overlap on screen does the nearer one's colour
 *   now win instead of the last drawn. Each of those draws is also bounded on screen ({@link #noteOutlineDraw},
 *   {@link SmokeOutlineRegions}).</li>
 *   <li>Before the entity_outline post chain: the target's own depth is put back (the chain clears that one, so the
 *   silhouettes need no copy), and the occlusion texture is marched only where an outline region meets the smoke
 *   rectangle (the whole smoke rectangle when the outlines could not be bounded); with no outline in the smoke nothing
 *   below runs.</li>
 *   <li>After the chain: {@code outline_mask.fsh} scales the alpha of every outline pixel in those regions by the
 *   smoke visibility at its silhouette distance: linear in the optical depth decoded from the checkpoint texel, so a
 *   thin veil fades a highlight in proportion. Tracked regions are blended in place; the whole-rectangle fallback
 *   copies the colour aside first.</li>
 * </ol>
 * Every composite of the target ({@code drawEntityOutlinesFramebuffer}: vanilla, the Black Raven, Seeker and Rift
 * filters, the scope lens) therefore reads the masked result. If the GL pipeline fails, the smoke rectangle of the
 * outline target is cleared instead (fail closed).
 * 逐像素擦掉原版实体描边目标中烟后的一切高亮（所有者 2026-11-05：任何阵营都不能透过烟看到玩家；身体一半在烟后只丢
 * 那一半的高亮）。本能、职业透视、阵营颜色、尸体、掉落物、原版发光与 M67 警示都画进这同一个目标，遮住它即覆盖全部；
 * 盲人回声定位与服务端视线判定不受影响。每次 {@code WorldRenderer#render} 调用（主画面与瞄具镜内相同）：
 * 1. {@code render} HEAD 的 {@link #beginPass}：廉价的 CPU 判断烟是否可能触及本次渲染，只有此时才进入就绪状态；无烟时以下
 * 步骤都不运行，描边逐像素不变。2. 就绪期间描边层改用 {@code LEQUAL} 深度测试而非原版不启用测试的 {@code ALWAYS}
 * （{@link #drawWithSilhouetteDepth}），写入本次渲染挂到描边目标上的私有深度纹理（第一次描边绘制时清除，从不含世界深度），
 * 使其保存最近的剪影。墙体仍不遮挡描边；只有两个描边身体在屏幕上重叠时，颜色改为较近者而非最后绘制者。这些绘制同时在屏幕
 * 上求出边界（{@link #noteOutlineDraw}，{@link SmokeOutlineRegions}）。3. entity_outline 后处理链之前：换回目标自己的深度
 * （后处理清除的是它，因此剪影无需拷贝），只在描边区域与烟雾矩形相交处步进遮挡纹理（描边无法求界时为整个烟雾矩形）；
 * 烟中没有描边时以下步骤都不运行。4. 后处理之后：{@code outline_mask.fsh} 按这些区域内每个描边像素所属剪影距离处的烟雾
 * 可见度缩放其 alpha（与从检查点纹素解出的光学厚度成线性，薄烟按比例变淡）。已跟踪的区域原地混合；整个烟雾矩形的退路
 * 先另存颜色。因此该目标的所有合成
 * （原版、黑羽鸦、探索者与隙行者滤镜、瞄具镜内的 {@code drawEntityOutlinesFramebuffer}）读到的都是遮罩后的结果。GL 管线
 * 失败时改为清空描边目标中的烟雾矩形（失败即遮挡）。
 */
public final class SmokeOutlineMask {
    private static boolean armed;
    private static boolean masking;
    /**
     * Between {@link #beginPass} and {@link #endPass}: a world render is in progress, so {@link #coverage} tells
     * whether smoke can touch it ({@link SmokeHeatMask} relies on it). / 处于 {@link #beginPass} 与 {@link #endPass} 之间：
     * 正在进行一次世界渲染，因此 {@link #coverage} 说明烟是否可能触及它（{@link SmokeHeatMask} 依赖此值）。
     */
    private static boolean passOpen;
    private static Camera camera;
    private static final Matrix4f POSITION = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static int passWidth;
    private static int passHeight;
    /** Smoke seen at {@link #beginPass}, reused by the march of the same pass. / 开始时的烟，供同一渲染的步进复用。 */
    private static SmokeOcclusionMarch.Coverage coverage;
    private static final SmokeOutlineRegions OUTLINES = new SmokeOutlineRegions();
    private static final Matrix4f CLIP_FROM_VERTEX = new Matrix4f();
    /** Pass-pixel rectangles rewritten after the chain (masked, or cleared when failing). / 后处理之后要重写的像素矩形。 */
    private static List<int[]> maskRegions = List.of();
    private static SimpleFramebuffer scratch;
    /**
     * Outline framebuffer whose depth attachment is {@link #scratch}'s depth for the rest of this pass (0 when not
     * swapped), and its own depth texture to put back.
     * 本次渲染剩余期间深度附件换成 {@link #scratch} 深度的描边帧缓冲（未交换时为 0），及其原本要换回的深度纹理。
     */
    private static int swappedFbo;
    private static int swappedDepth;
    private static SmokeGlProgram program;
    /**
     * {@code outline_mask.fsh} with {@code IN_PLACE}: blends the visibility into the target, no colour copy.
     * 原地版本：混合可见度，无需颜色拷贝。
     */
    private static SmokeGlProgram inPlaceProgram;
    /**
     * This pass masks tracked outline regions in place rather than the whole smoke rectangle.
     * 本次渲染原地遮罩已跟踪的描边区域，而非整个烟雾矩形。
     */
    private static boolean maskInPlace;
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
        restoreOutlineDepth();
        armed = false;
        masking = false;
        coverage = null;
        passOpen = true;
        OUTLINES.reset();
        if (SmokeClientState.isEmpty()) {
            return;
        }
        Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
        passWidth = target.textureWidth;
        passHeight = target.textureHeight;
        camera = passCamera;
        POSITION.set(positionMatrix);
        PROJECTION.set(projection);
        coverage = SmokeOcclusionMarch.coverage(passCamera, positionMatrix, projection, passWidth, passHeight);
        armed = coverage != null;
    }

    /** True while outline layers must keep the nearest silhouette depth. / 描边层需要保留最近剪影深度时为 true。 */
    public static boolean capturingSilhouetteDepth() {
        return armed;
    }

    /**
     * One outline-layer draw while armed ({@code SmokeOutlineDepthMixin}, before the upload): bounds its vertices on
     * screen under the draw-time {@code ProjMat * ModelViewMat}. A buffer without a float position, or vertex data
     * shorter than its draw parameters, makes the pass unbounded (whole smoke rectangle, fail closed).
     * 就绪期间的一次描边层绘制（{@code SmokeOutlineDepthMixin}，上传之前）：按绘制时的 {@code ProjMat * ModelViewMat}
     * 求出其顶点在屏幕上的边界。缓冲没有浮点位置、或顶点数据短于绘制参数时，本次渲染变为无界（整个烟雾矩形，失败即遮挡）。
     */
    public static void noteOutlineDraw(BuiltBuffer buffer) {
        if (!armed) {
            return;
        }
        BuiltBuffer.DrawParameters parameters = buffer.getDrawParameters();
        VertexFormat format = parameters.format();
        ByteBuffer vertices = buffer.getBuffer();
        int stride = format.getVertexSizeByte();
        int count = parameters.vertexCount();
        if (!format.has(VertexFormatElement.POSITION) || stride <= 0 || (long) count * stride > vertices.remaining()) {
            OUTLINES.markUnbounded();
            return;
        }
        CLIP_FROM_VERTEX.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        OUTLINES.addDraw(CLIP_FROM_VERTEX, MemoryUtil.memAddress(vertices), stride,
                format.getOffset(VertexFormatElement.POSITION), count, passWidth, passHeight);
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
        attachSilhouetteDepth();
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
     * First outline draw of an armed pass, with the outline target bound by the layer: points its depth attachment at
     * {@link #scratch}'s depth (cleared here) until {@link #restoreOutlineDepth}, so the silhouettes are drawn where
     * the mask reads them and need no copy before the post chain clears the target. The target's own depth, cleared
     * at the start of {@code render}, is put back untouched. Any other binding keeps the copy path.
     * 已就绪渲染中的第一次描边绘制（描边目标已由图层绑定）：把其深度附件换成 {@link #scratch} 的深度（在此清除），直到
     * {@link #restoreOutlineDepth}，使剪影直接画到遮罩读取之处，后处理链清除目标之前无需拷贝。目标自己的深度（
     * {@code render} 开头已清除）原样换回。其他绑定情况仍走拷贝路径。
     */
    private static void attachSilhouetteDepth() {
        if (swappedFbo != 0 || broken) {
            return;
        }
        Framebuffer outline = MinecraftClient.getInstance().worldRenderer.getEntityOutlinesFramebuffer();
        if (outline == null || GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != outline.fbo) {
            return;
        }
        try {
            // Creating or resizing the scratch target rebinds framebuffers. / 创建或调整暂存目标会改动帧缓冲绑定。
            ensureScratch(outline);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outline.fbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
                    scratch.getDepthAttachment(), 0);
            swappedFbo = outline.fbo;
            swappedDepth = outline.getDepthAttachment();
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            GlStateManager._disableScissorTest();
            GlStateManager._clear(GL11.GL_DEPTH_BUFFER_BIT, false);
            if (scissor) {
                GlStateManager._enableScissorTest();
            }
        } catch (RuntimeException exception) {
            restoreOutlineDepth();
            fail(exception);
        }
    }

    /** Puts the outline target's own depth back (keeps the current binding). / 换回描边目标自己的深度（保持当前绑定）。 */
    private static void restoreOutlineDepth() {
        if (swappedFbo == 0) {
            return;
        }
        int bound = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, swappedFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
                swappedDepth, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, bound);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        swappedFbo = 0;
        swappedDepth = 0;
    }

    /**
     * Just before the entity_outline post chain: puts the target's own depth back, then marches the occlusion texture
     * where the outline regions meet the smoke (copying the silhouette depth only if it was not drawn into the scratch
     * depth).
     * entity_outline 后处理链之前：换回目标自己的深度，再在描边区域与烟相交处步进遮挡纹理（剪影深度未直接画进暂存深度时
     * 才复制它）。
     */
    public static void beforeOutlinePost(Framebuffer outline) {
        masking = false;
        maskRegions = List.of();
        boolean silhouettesInScratch = outline != null && swappedFbo == outline.fbo;
        restoreOutlineDepth();
        if (!armed || outline == null) {
            return;
        }
        int width = Math.min(passWidth, outline.textureWidth);
        int height = Math.min(passHeight, outline.textureHeight);
        // Outline pixels a mask texel can affect lie within 2 march texels of the smoke rectangle.
        // 遮罩纹素可能影响的描边像素位于烟雾矩形外 2 个步进纹素以内。
        int[] smokeArea = SmokeOcclusionMath.grow(coverage.pixelRect(), 2 * SmokeOcclusionMath.MARCH_DIVISOR,
                width, height);
        List<int[]> regions = new ArrayList<>(SmokeOutlineRegions.MAX_REGIONS);
        maskInPlace = false;
        if (OUTLINES.unbounded()) {
            regions.add(smokeArea);
        } else {
            for (int[] region : OUTLINES.regions()) {
                int[] inSmoke = SmokeOutlineRegions.intersect(region, smokeArea);
                if (inSmoke != null) {
                    regions.add(inSmoke);
                }
            }
            if (regions.isEmpty()) {
                return;
            }
            // Tight regions: evaluating every pixel costs less than the blit of a colour copy.
            // 区域很紧：逐像素计算比为颜色拷贝做一次 blit 更省。
            maskInPlace = true;
        }
        boolean prepared = SmokeOcclusionMarch.prepare(coverage, passWidth, passHeight, regions);
        if (!prepared) {
            int[] failed = SmokeOcclusionMarch.failedRect();
            if (failed != null) {
                maskRegions = List.of(failed);
            }
            return;
        }
        maskRegions = regions;
        if (broken) {
            return;
        }
        SmokeGlState state = SmokeGlState.captureForFullscreenPass(0, 0);
        try {
            ensureScratch(outline);
            for (int[] region : silhouettesInScratch ? List.<int[]>of() : regions) {
                blit(outline, SmokeOcclusionMath.grow(region, SmokeOcclusionMath.SILHOUETTE_SEARCH_RADIUS + 1,
                        width, height), GL11.GL_DEPTH_BUFFER_BIT);
            }
            masking = true;
        } catch (RuntimeException exception) {
            fail(exception);
        } finally {
            state.restore();
        }
    }

    /**
     * Right after the entity_outline post chain: rewrites the mask regions of the outline target with masked alpha,
     * or clears them when the pipeline is failing.
     * entity_outline 后处理链之后：以遮罩后的 alpha 重写描边目标的遮罩区域；管线失败时清空这些区域。
     */
    public static void afterOutlinePost(Framebuffer outline) {
        List<int[]> regions = maskRegions;
        boolean ok = masking;
        masking = false;
        maskRegions = List.of();
        if (regions.isEmpty() || outline == null) {
            return;
        }
        SmokeGlState state = SmokeGlState.captureForFullscreenPass(3, 0);
        try {
            if (ok && !broken) {
                try {
                    mask(outline, regions);
                    return;
                } catch (RuntimeException exception) {
                    fail(exception);
                }
            }
            clear(outline, regions);
        } finally {
            state.restore();
        }
    }

    /** {@code WorldRenderer#render} RETURN. / {@code WorldRenderer#render} RETURN。 */
    public static void endPass() {
        restoreOutlineDepth();
        armed = false;
        masking = false;
        passOpen = false;
        maskRegions = List.of();
        coverage = null;
        camera = null;
    }

    /** A world render is between {@link #beginPass} and {@link #endPass}. / 正处于一次世界渲染之中。 */
    static boolean passOpen() {
        return passOpen;
    }

    /**
     * Smoke in view of the current pass (computed at {@link #beginPass}), or null when none can touch it.
     * 当前渲染视野内的烟（在 {@link #beginPass} 求出）；烟不可能触及时为 null。
     */
    static SmokeOcclusionMarch.Coverage passCoverage() {
        return coverage;
    }

    /** Size of the current pass's target ({@code client.getFramebuffer()} at its HEAD). / 当前渲染目标的尺寸。 */
    static int passWidth() {
        return passWidth;
    }

    static int passHeight() {
        return passHeight;
    }

    private static void mask(Framebuffer outline, List<int[]> regions) {
        mask(outline, regions, maskInPlace);
    }

    /**
     * Rewrites {@code regions} of the outline target. In place, the visibility is blended into the target's alpha
     * ({@code IN_PLACE}); otherwise the colour is copied aside first and rewritten (any region size).
     * 重写描边目标的 {@code regions}。原地模式把可见度混合进目标 alpha（{@code IN_PLACE}）；否则先另存颜色再重写（任意区域大小）。
     */
    private static void mask(Framebuffer outline, List<int[]> regions, boolean inPlace) {
        SmokeGlProgram mask;
        if (inPlace) {
            if (inPlaceProgram == null) {
                inPlaceProgram = SmokeGlProgram.load("fullscreen.vsh", "outline_mask.fsh", "#define IN_PLACE\n");
                inPlaceProgram.use();
                inPlaceProgram.setInt("SilhouetteDepth", 1);
                inPlaceProgram.setInt("Occlusion", 2);
            }
            mask = inPlaceProgram;
        } else {
            if (program == null) {
                program = SmokeGlProgram.load("fullscreen.vsh", "outline_mask.fsh");
                program.use();
                program.setInt("OutlineColor", 0);
                program.setInt("SilhouetteDepth", 1);
                program.setInt("Occlusion", 2);
            }
            mask = program;
            // One copy of the regions' bounding box: a blit costs more per call than per pixel.
            // 一次拷贝各区域的外接矩形：每次 blit 调用的开销大于逐像素开销。
            blit(outline, bounds(regions), GL11.GL_COLOR_BUFFER_BIT);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outline.fbo);
        GlStateManager._viewport(0, 0, SmokeOcclusionMarch.passWidth(), SmokeOcclusionMarch.passHeight());
        int[] blend = null;
        if (inPlace) {
            // result.rgb = dst.rgb, result.a = dst.a * visibility. / 结果 rgb 保持，alpha 乘以可见度。
            blend = new int[]{
                    GlStateManager._getInteger(GL14.GL_BLEND_SRC_RGB),
                    GlStateManager._getInteger(GL14.GL_BLEND_DST_RGB),
                    GlStateManager._getInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GlStateManager._getInteger(GL14.GL_BLEND_DST_ALPHA),
                    GlStateManager._getInteger(GL20.GL_BLEND_EQUATION_RGB)};
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_SRC_ALPHA);
            GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
        }
        mask.use();
        if (!inPlace) {
            SmokeGlState.bind2d(0, scratch.getColorAttachment());
        }
        SmokeGlState.bind2d(1, scratch.getDepthAttachment());
        SmokeGlState.bind2d(2, SmokeOcclusion.textureId());
        mask.setMatrix("InverseViewProjection", SmokeOcclusionMarch.inverseViewProjection());
        mask.setVec2("PassSize", SmokeOcclusionMarch.passWidth(), SmokeOcclusionMarch.passHeight());
        mask.setVec2("OcclusionSize", SmokeOcclusion.width(), SmokeOcclusion.height());
        mask.setFloatArray("Checkpoints", SmokeOcclusionMarch.CHECKPOINTS);
        mask.setFloat("Never", SmokeOcclusion.NEVER);
        for (int[] rect : regions) {
            SmokeGlState.scissor(rect);
            SmokeGlProgram.drawFullscreenTriangle();
        }
        if (blend != null) {
            // Blending itself is restored by the caller's state snapshot. / 混合开关由调用方的状态快照恢复。
            GlStateManager._blendFuncSeparate(blend[0], blend[1], blend[2], blend[3]);
            GlStateManager._blendEquation(blend[4]);
        }
    }

    private static int[] bounds(List<int[]> regions) {
        int[] box = regions.get(0).clone();
        for (int[] rect : regions) {
            box[0] = Math.min(box[0], rect[0]);
            box[1] = Math.min(box[1], rect[1]);
            box[2] = Math.max(box[2], rect[2]);
            box[3] = Math.max(box[3], rect[3]);
        }
        return box;
    }

    /** Fail closed: no highlight survives inside the rectangles. / 失败即遮挡：这些矩形内不留任何高亮。 */
    private static void clear(Framebuffer outline, List<int[]> regions) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outline.fbo);
        for (int[] rect : regions) {
            SmokeGlState.scissor(rect);
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{0.0F, 0.0F, 0.0F, 0.0F});
        }
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
