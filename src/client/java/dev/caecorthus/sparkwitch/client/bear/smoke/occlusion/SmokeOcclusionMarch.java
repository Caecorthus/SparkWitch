package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeOcclusion;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.doctor4t.wathe.compat.IrisHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of {@link SmokeOcclusion} (render thread only): picks the smoke volumes in view, finds the screen
 * rectangle they can touch, and ray-marches only that rectangle into a {@code GL_RGBA16F} checkpoint texture at
 * 1/{@link SmokeOcclusionMath#MARCH_DIVISOR} resolution; every other texel is {@link SmokeOcclusion#NEVER}. The nearest
 * {@link SmokeOcclusionMath#MAX_VOLUMES} clouds in view are sampled; any further ones are passed as sealed boxes that
 * hide everything from their entry on (fail closed, a few uniforms). Zero GPU work without smoke in view. Under an Iris
 * shader pack it stands down (logged once): a pack draws glowing entities through its own G-buffers, which a later
 * package handles. A GL failure is logged once and reported through {@link #failedRect()} so the outline mask can fail
 * closed.
 * {@link SmokeOcclusion} 的实现（仅渲染线程）：挑出视野内的烟雾浓度体，求出它们可能触及的屏幕矩形，只在该矩形内以
 * 1/{@link SmokeOcclusionMath#MARCH_DIVISOR} 分辨率步进写入 {@code GL_RGBA16F} 检查点纹理，其余纹素为
 * {@link SmokeOcclusion#NEVER}。采样视野内最近的 {@link SmokeOcclusionMath#MAX_VOLUMES} 团烟；其余的作为封口盒传入，从其入口
 * 起遮住一切（失败即遮挡，只需几个 uniform）。视野内无烟时不做任何 GPU 工作。使用 Iris 光影包时停用（只记录一次日志）：
 * 光影包用自己的 G 缓冲绘制发光实体，由后续工作包处理。GL 失败只记录一次，并通过 {@link #failedRect()} 报告，使描边遮罩
 * 失败即遮挡。
 */
public final class SmokeOcclusionMarch {
    /** One volume picked for a pass, camera-relative. / 本次渲染选中的一个浓度体（相机相对坐标）。 */
    record Picked(SmokeVolumes.Volume volume, double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ, double distanceSquared) {
    }

    /**
     * Sampled volumes and sealed boxes in view plus the pass-pixel rectangle they can touch.
     * 视野内的采样浓度体与封口盒，以及它们可能触及的像素矩形。
     */
    record Coverage(List<Picked> volumes, List<Picked> sealed, int[] pixelRect, Matrix4f viewProjection) {
    }

    private static SmokeGlProgram program;
    private static boolean broken;
    private static boolean irisLogged;
    private static boolean irisBroken;
    /**
     * Two targets, so the main view and the scope lens (different sizes, same frame) never reallocate each other's.
     * 两个目标，使主画面与瞄具镜内（同一帧、尺寸不同）不会互相触发重新分配。
     */
    private static final Target[] TARGETS = {new Target(), new Target()};
    private static Target current;
    private static long useCounter;

    private static boolean ready;
    private static int[] failedRect;
    private static int[] pixelRect;
    private static final Matrix4f INVERSE_VIEW_PROJECTION = new Matrix4f();
    /** Checkpoint optical depths, uploaded to both programs. / 检查点光学厚度，上传给两个程序。 */
    static final float[] CHECKPOINTS = checkpoints();
    private static int passWidth;
    private static int passHeight;

    private SmokeOcclusionMarch() {
    }

    private static float[] checkpoints() {
        float[] depths = new float[SmokeOcclusion.CHECKPOINT_COUNT];
        for (int i = 0; i < depths.length; i++) {
            depths[i] = SmokeOcclusion.checkpointOpticalDepth(i);
        }
        return depths;
    }

    /**
     * CPU-only: which volumes are in view of this pass and where on screen, or null when none (also under an Iris
     * shader pack or without a world). Refreshes {@link SmokeVolumes} for the tick.
     * 仅 CPU：本次渲染视野内有哪些浓度体、在屏幕何处；没有时（或使用 Iris 光影包、没有世界时）返回 null。会为本 tick
     * 刷新 {@link SmokeVolumes}。
     */
    static Coverage coverage(Camera camera, Matrix4f positionMatrix, Matrix4f projection, int width, int height) {
        if (SmokeClientState.isEmpty() || width <= 0 || height <= 0) {
            return null;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || shaderPackInUse()) {
            return null;
        }
        List<SmokeVolumes.Volume> active = SmokeVolumes.prepare(world);
        if (active.isEmpty()) {
            return null;
        }
        Vec3d eye = camera.getPos();
        List<Picked> picked = new ArrayList<>(active.size());
        for (SmokeVolumes.Volume v : active) {
            double minX = v.minX() - eye.x;
            double minY = v.minY() - eye.y;
            double minZ = v.minZ() - eye.z;
            double maxX = v.maxX() - eye.x;
            double maxY = v.maxY() - eye.y;
            double maxZ = v.maxZ() - eye.z;
            double dx = Math.max(0.0, Math.max(minX, -maxX));
            double dy = Math.max(0.0, Math.max(minY, -maxY));
            double dz = Math.max(0.0, Math.max(minZ, -maxZ));
            picked.add(new Picked(v, minX, minY, minZ, maxX, maxY, maxZ, dx * dx + dy * dy + dz * dz));
        }
        picked.sort(Comparator.comparingDouble(Picked::distanceSquared));
        Matrix4f viewProjection = new Matrix4f(projection).mul(positionMatrix);
        double[] boxes = new double[6];
        List<Picked> inView = new ArrayList<>();
        List<Picked> sealed = new ArrayList<>();
        int[] rect = null;
        for (Picked p : picked) {
            boxes[0] = p.minX();
            boxes[1] = p.minY();
            boxes[2] = p.minZ();
            boxes[3] = p.maxX();
            boxes[4] = p.maxY();
            boxes[5] = p.maxZ();
            int[] one = SmokeOcclusionMath.screenRect(viewProjection, boxes, 1, width, height, 1);
            if (one == null) {
                continue;
            }
            if (inView.size() < SmokeOcclusionMath.MAX_VOLUMES) {
                inView.add(p);
            } else if (sealed.size() < SmokeOcclusionMath.MAX_SEALED_BOXES) {
                sealed.add(p);
            } else {
                // Past the uniform budget the box only grows, so the extra clouds stay sealed.
                // 超出 uniform 预算后只扩大盒子，多出的烟团仍然封口。
                Picked last = sealed.remove(sealed.size() - 1);
                sealed.add(new Picked(last.volume(), Math.min(last.minX(), p.minX()), Math.min(last.minY(), p.minY()),
                        Math.min(last.minZ(), p.minZ()), Math.max(last.maxX(), p.maxX()),
                        Math.max(last.maxY(), p.maxY()), Math.max(last.maxZ(), p.maxZ()), last.distanceSquared()));
            }
            rect = rect == null ? one : new int[]{Math.min(rect[0], one[0]), Math.min(rect[1], one[1]),
                    Math.max(rect[2], one[2]), Math.max(rect[3], one[3])};
        }
        if (rect == null || rect[2] <= rect[0] || rect[3] <= rect[1]) {
            return null;
        }
        return new Coverage(inView, sealed, rect, viewProjection);
    }

    /** Body of {@link SmokeOcclusion#prepare}. / {@link SmokeOcclusion#prepare} 的实现。 */
    public static boolean prepare(Camera camera, Matrix4f positionMatrix, Matrix4f projection, int width, int height) {
        RenderSystem.assertOnRenderThread();
        ready = false;
        failedRect = null;
        Coverage coverage = coverage(camera, positionMatrix, projection, width, height);
        if (coverage == null) {
            return false;
        }
        if (broken) {
            failedRect = coverage.pixelRect();
            return false;
        }
        try {
            march(coverage, width, height);
        } catch (RuntimeException exception) {
            broken = true;
            failedRect = coverage.pixelRect();
            SparkWitch.LOGGER.error("Smoke outline occlusion failed; outlines inside smoke are hidden instead",
                    exception);
            return false;
        }
        pixelRect = coverage.pixelRect();
        passWidth = width;
        passHeight = height;
        coverage.viewProjection().invert(INVERSE_VIEW_PROJECTION);
        ready = true;
        return true;
    }

    private static void march(Coverage coverage, int width, int height) {
        int marchWidth = SmokeOcclusionMath.marchSize(width);
        int marchHeight = SmokeOcclusionMath.marchSize(height);
        List<Picked> volumes = coverage.volumes();
        SmokeGlState state = SmokeGlState.captureForFullscreenPass(1, SmokeOcclusionMath.MAX_VOLUMES);
        try {
            if (program == null) {
                program = SmokeGlProgram.load("fullscreen.vsh", "occlusion_march.fsh");
                program.use();
                for (int i = 0; i < SmokeOcclusionMath.MAX_VOLUMES; i++) {
                    program.setInt("Volume" + i, i);
                }
            }
            current = target(marchWidth, marchHeight);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, current.framebuffer);
            GlStateManager._viewport(0, 0, marchWidth, marchHeight);
            float never = SmokeOcclusion.NEVER;
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{never, never, never, never});
            SmokeGlState.scissor(SmokeOcclusionMath.toMarchRect(coverage.pixelRect(), marchWidth, marchHeight));

            program.use();
            float[] mins = new float[SmokeOcclusionMath.MAX_VOLUMES * 3];
            float[] sizes = new float[SmokeOcclusionMath.MAX_VOLUMES * 3];
            for (int i = 0; i < SmokeOcclusionMath.MAX_VOLUMES; i++) {
                if (i < volumes.size()) {
                    Picked p = volumes.get(i);
                    mins[i * 3] = (float) p.minX();
                    mins[i * 3 + 1] = (float) p.minY();
                    mins[i * 3 + 2] = (float) p.minZ();
                    sizes[i * 3] = (float) (p.maxX() - p.minX());
                    sizes[i * 3 + 1] = (float) (p.maxY() - p.minY());
                    sizes[i * 3 + 2] = (float) (p.maxZ() - p.minZ());
                    SmokeGlState.bind3d(i, p.volume().texture());
                } else {
                    sizes[i * 3] = sizes[i * 3 + 1] = sizes[i * 3 + 2] = 1.0F;
                    SmokeGlState.bind3d(i, 0);
                }
            }
            program.setInt("VolumeCount", volumes.size());
            program.setVec3Array("VolumeMin", mins);
            program.setVec3Array("VolumeSize", sizes);
            program.setMatrix("InverseViewProjection", new Matrix4f(coverage.viewProjection()).invert());
            program.setVec2("TargetSize", marchWidth, marchHeight);
            program.setFloat("Extinction", SmokeVolumes.EXTINCTION_PER_BLOCK);
            program.setFloat("StepLength", SmokeOcclusionMath.STEP_LENGTH);
            List<Picked> sealed = coverage.sealed();
            float[] sealedMin = new float[SmokeOcclusionMath.MAX_SEALED_BOXES * 3];
            float[] sealedMax = new float[SmokeOcclusionMath.MAX_SEALED_BOXES * 3];
            for (int i = 0; i < sealed.size(); i++) {
                Picked p = sealed.get(i);
                sealedMin[i * 3] = (float) p.minX();
                sealedMin[i * 3 + 1] = (float) p.minY();
                sealedMin[i * 3 + 2] = (float) p.minZ();
                sealedMax[i * 3] = (float) p.maxX();
                sealedMax[i * 3 + 1] = (float) p.maxY();
                sealedMax[i * 3 + 2] = (float) p.maxZ();
            }
            program.setInt("SealedCount", sealed.size());
            program.setVec3Array("SealedMin", sealedMin);
            program.setVec3Array("SealedMax", sealedMax);
            program.setFloatArray("Checkpoints", CHECKPOINTS);
            program.setFloat("Never", SmokeOcclusion.NEVER);
            SmokeGlProgram.drawFullscreenTriangle();
        } finally {
            state.restore();
        }
    }

    /** One occlusion texture and its framebuffer. / 一张遮挡纹理及其帧缓冲。 */
    private static final class Target {
        int texture;
        int framebuffer;
        int width;
        int height;
        long lastUse;
    }

    private static Target target(int width, int height) {
        Target chosen = null;
        for (Target t : TARGETS) {
            if (t.texture != 0 && t.width == width && t.height == height) {
                chosen = t;
                break;
            }
        }
        if (chosen == null) {
            chosen = TARGETS[0].lastUse <= TARGETS[1].lastUse ? TARGETS[0] : TARGETS[1];
            allocate(chosen, width, height);
        }
        chosen.lastUse = ++useCounter;
        return chosen;
    }

    private static void allocate(Target t, int width, int height) {
        if (t.texture == 0) {
            t.texture = GlStateManager._genTexture();
            t.framebuffer = GlStateManager.glGenFramebuffers();
        }
        // Unit 0's binding is restored by the caller's snapshot. / 0 号单元的绑定由调用方快照恢复。
        SmokeGlState.bind2d(0, t.texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                (java.nio.FloatBuffer) null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, t.framebuffer);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                t.texture, 0);
        int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("smoke occlusion framebuffer incomplete: " + status);
        }
        t.width = width;
        t.height = height;
    }

    /** True when the last {@link #prepare} produced a texture. / 最近一次 {@link #prepare} 生成了纹理时为 true。 */
    public static boolean ready() {
        return ready;
    }

    /**
     * Pixel rectangle smoke covered while the occlusion pipeline was failing, or null.
     * 遮挡管线失败期间烟所覆盖的像素矩形；否则为 null。
     */
    public static int[] failedRect() {
        return failedRect;
    }

    /** Pass-pixel rectangle the prepared texture covers. / 已生成纹理覆盖的渲染目标像素矩形。 */
    public static int[] pixelRect() {
        return pixelRect;
    }

    /** Inverse of {@code projection * view} of the prepared pass (camera-relative). / 已准备渲染的逆视图投影矩阵。 */
    public static Matrix4f inverseViewProjection() {
        return INVERSE_VIEW_PROJECTION;
    }

    public static int passWidth() {
        return passWidth;
    }

    public static int passHeight() {
        return passHeight;
    }

    public static int textureId() {
        return ready ? current.texture : 0;
    }

    public static int width() {
        return ready ? current.width : 0;
    }

    public static int height() {
        return ready ? current.height : 0;
    }

    /**
     * Wathe's public Iris facade; a broken Iris API counts as a pack in use (stand down). Logged once per state.
     * Wathe 的公共 Iris 门面；Iris API 异常视为正在使用光影包（停用）。每种状态只记录一次。
     */
    private static boolean shaderPackInUse() {
        boolean inUse;
        if (irisBroken) {
            inUse = true;
        } else {
            try {
                inUse = IrisHelper.isIrisShaderPackInUse();
            } catch (LinkageError | RuntimeException exception) {
                irisBroken = true;
                inUse = true;
            }
        }
        if (inUse && !irisLogged) {
            irisLogged = true;
            SparkWitch.LOGGER.warn("Iris shader pack in use: BEAR smoke does not mask entity outlines yet");
        } else if (!inUse) {
            irisLogged = false;
        }
        return inUse;
    }
}
