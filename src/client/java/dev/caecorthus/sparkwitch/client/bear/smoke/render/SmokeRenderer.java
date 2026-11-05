package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.caecorthus.sparkwitch.client.blind.render.BlindShaderPackCheck;
import dev.caecorthus.sparkwitch.client.render.entity.SecondaryWorldPass;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The visible BEAR smoke: a GPU ray march of the {@link SmokeVolumes} density volumes, drawn by every
 * {@code WorldRenderer#render} call (the main view, Seeker camera views, and secondary passes such as the USEC scope
 * picture-in-picture) with that call's own camera, view rotation and projection; nothing is cached from the main view.
 * <ol>
 *   <li>{@link #beginWorldPass} at {@code WorldRenderEvents.START}: decides whether this world render has smoke work
 *   (zero cost when {@link SmokeClientState} is empty or {@link SmokeVolumes#prepare} returns nothing).</li>
 *   <li>Fabulous! only, {@link #captureFabulousDepth} at the head of {@code ParticleManager#renderParticles}: copies
 *   the main depth (opaque terrain and entities, translucent terrain excluded) before the Fabulous! composite clears
 *   it.</li>
 *   <li>{@link #renderWorldPass} at {@code WorldRenderEvents.LAST}, after translucent terrain, particles, weather and
 *   the Fabulous! composite, before the first-person hand: a half-resolution march into a private RGBA16F target,
 *   then a depth-aware upsample blended onto the world target's colour.</li>
 * </ol>
 * The scene depth in Fast/Fancy is the world target's own depth texture (translucent terrain and particles included),
 * sampled through a framebuffer that wraps only the colour texture, so there is no feedback loop and no copy.
 * Under an Iris shader pack the pass is skipped (logged once). Every GL state the passes touch is saved and restored
 * ({@link SmokeGlState}). A failure logs once, frees everything and retries after {@link #RETRY_NANOS}.
 * Client-only presentation; reads {@link SmokeClientState} only.
 * BEAR 可见烟雾：对 {@link SmokeVolumes} 浓度体做 GPU 光线步进，由每一次 {@code WorldRenderer#render} 调用（主画面、
 * 搜寻者摄像头画面，以及 USEC 瞄具画中画等第二次世界渲染）各自用本次调用的相机、视角旋转与投影绘制，不缓存主画面的任何值。
 * 1）{@link #beginWorldPass} 于 {@code WorldRenderEvents.START}：判断本次世界渲染是否有烟要画（{@link SmokeClientState}
 * 为空或 {@link SmokeVolumes#prepare} 无结果时零开销）。2）仅 Fabulous!：{@link #captureFabulousDepth} 位于
 * {@code ParticleManager#renderParticles} 开头，在 Fabulous! 合成清掉主深度之前拷贝它（不透明地形与实体，不含半透明地形）。
 * 3）{@link #renderWorldPass} 于 {@code WorldRenderEvents.LAST}，位于半透明地形、粒子、天气与 Fabulous! 合成之后、第一人称
 * 手部之前：先半分辨率步进到私有 RGBA16F 目标，再按深度感知放大混合到世界目标的颜色上。Fast/Fancy 下场景深度就是世界目标
 * 自己的深度纹理（含半透明地形与粒子），经只包装颜色纹理的帧缓冲采样，因此既无反馈回路也无需拷贝。开着 Iris 光影包时跳过
 * （只记录一次日志）。各步骤改动的 GL 状态全部保存并恢复（{@link SmokeGlState}）。失败时记录一次日志、释放全部资源，
 * {@link #RETRY_NANOS} 后重试。纯客户端展示；只读取 {@link SmokeClientState}。
 */
public final class SmokeRenderer {
    static final long RETRY_NANOS = 30_000_000_000L;
    /** Offscreen targets are freed after this long without smoke. / 无烟持续这么久后释放离屏目标。 */
    static final long IDLE_RELEASE_NANOS = 10_000_000_000L;
    private static final int UNIT_DEPTH = 0;
    private static final int UNIT_NOISE = 1;
    private static final int UNIT_FIRST_VOLUME = 2;
    private static final int UNIT_SMOKE_COLOR = 1;
    private static final int UNIT_SMOKE_DEPTH = 2;

    private static final SmokeViewTargets[] TARGETS = {new SmokeViewTargets(), new SmokeViewTargets()};
    private static final SmokeGlState GL_STATE = new SmokeGlState();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE_VIEW_PROJECTION = new Matrix4f();
    private static final List<SmokeVolumes.Volume> CANDIDATES = new ArrayList<>();
    private static final List<SmokeVolumes.Volume> SELECTED = new ArrayList<>(SmokeMarchRules.MAX_CLOUDS);
    private static final float[] BOX_MIN = new float[3 * SmokeMarchRules.MAX_CLOUDS];
    private static final float[] BOX_MAX = new float[3 * SmokeMarchRules.MAX_CLOUDS];
    private static final float[] BOX_INV_SIZE = new float[3 * SmokeMarchRules.MAX_CLOUDS];
    private static final float[] CLOUD_LIGHT = new float[3 * SmokeMarchRules.MAX_CLOUDS];
    private static final float[] NOISE_OFFSETS = new float[6];
    private static final int[] RECT = new int[4];
    private static final int[] CLOUD_RECT = new int[4];
    private static final int[] LOW_RECT = new int[4];
    @Nullable
    private static FloatBuffer matrixBuffer;
    @Nullable
    private static SmokeProgram march;
    @Nullable
    private static SmokeProgram composite;
    @Nullable
    private static SmokeProgram depthCopy;
    private static int vertexArray;
    private static boolean failed;
    private static long retryAtNanos;
    private static boolean irisLogged;
    private static long lastActiveNanos;
    private static boolean targetsHeld;
    private static int frame;
    /** START found smoke work for the current world render. / START 判定本次世界渲染有烟要画。 */
    private static boolean passActive;
    private static int passSlot;
    private static boolean fabulousCapturePending;
    private static boolean fabulousCaptured;

    private SmokeRenderer() {
    }

    /** {@code WorldRenderEvents.START}; render thread. / {@code WorldRenderEvents.START}；渲染线程。 */
    public static void beginWorldPass(WorldRenderContext context) {
        passActive = false;
        fabulousCapturePending = false;
        fabulousCaptured = false;
        ClientWorld world = context.world();
        if (world == null || SmokeClientState.isEmpty()) {
            idle();
            return;
        }
        if (SmokeVolumes.prepare(world).isEmpty()) {
            idle();
            return;
        }
        if (BlindShaderPackCheck.isShaderPackInUse()) {
            if (!irisLogged) {
                irisLogged = true;
                SparkWitch.LOGGER.info("BEAR smoke: an Iris shader pack is in use; the volumetric smoke is skipped");
            }
            return;
        }
        if (failed) {
            if (Util.getMeasuringTimeNano() - retryAtNanos < 0L) {
                return;
            }
            failed = false;
        }
        passActive = true;
        passSlot = SecondaryWorldPass.isActive() ? 1 : 0;
        lastActiveNanos = Util.getMeasuringTimeNano();
        fabulousCapturePending = context.worldRenderer().getTranslucentFramebuffer() != null;
    }

    /**
     * Head of {@code ParticleManager#renderParticles}; render thread. In Fabulous! the main depth still holds opaque
     * terrain and every entity here (translucent terrain went to its own target) and is cleared by the composite
     * later, so it is copied now; a no-op unless {@link #beginWorldPass} asked for it.
     * {@code ParticleManager#renderParticles} 开头；渲染线程。Fabulous! 下此时主深度仍包含不透明地形与所有实体（半透明地形
     * 画在自己的目标里），之后会被合成清掉，因此此刻拷贝；除非 {@link #beginWorldPass} 要求，否则不做任何事。
     */
    public static void captureFabulousDepth() {
        if (!fabulousCapturePending) {
            return;
        }
        fabulousCapturePending = false;
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        GL_STATE.save();
        try {
            ensurePrograms(MinecraftClient.getInstance().getResourceManager());
            copyDepth(main.getDepthAttachment(), main.textureWidth, main.textureHeight);
            fabulousCaptured = true;
        } catch (RuntimeException | IOException exception) {
            fail(exception);
        } finally {
            GL_STATE.restore();
        }
    }

    /** {@code WorldRenderEvents.LAST}; render thread. / {@code WorldRenderEvents.LAST}；渲染线程。 */
    public static void renderWorldPass(WorldRenderContext context) {
        if (!passActive) {
            return;
        }
        passActive = false;
        fabulousCapturePending = false;
        ClientWorld world = context.world();
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer target = client.getFramebuffer();
        int width = target.textureWidth;
        int height = target.textureHeight;
        if (world == null || width <= 0 || height <= 0) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        // Camera-relative world -> clip, from THIS world render's matrices. / 相对相机的世界 -> 裁剪空间，取自本次世界渲染的矩阵。
        VIEW_PROJECTION.set(context.projectionMatrix()).mul(context.positionMatrix());
        if (!select(SmokeVolumes.prepare(world), camera, width, height)) {
            return;
        }
        boolean fabulous = context.worldRenderer().getTranslucentFramebuffer() != null;
        GL_STATE.save();
        try {
            ensurePrograms(client.getResourceManager());
            SmokeViewTargets targets = TARGETS[passSlot];
            int depthTexture;
            if (fabulous) {
                if (!fabulousCaptured || !targets.hasCopy(width, height)) {
                    // The particle draw never ran (or was redirected): fall back to the translucent target's depth,
                    // which still holds opaque terrain, entities and translucent terrain.
                    // 粒子绘制未运行（或被重定向）：退回半透明目标的深度，它仍包含不透明地形、实体与半透明地形。
                    Framebuffer translucent = context.worldRenderer().getTranslucentFramebuffer();
                    copyDepth(translucent.getDepthAttachment(), width, height);
                }
                depthTexture = targets.copyTexture();
            } else {
                depthTexture = target.getDepthAttachment();
            }
            targets.ensureLow(width, height);
            targetsHeld = true;
            marchPass(context, world, camera, targets, depthTexture, width, height);
            compositePass(targets, target.getColorAttachment(), depthTexture, width, height);
        } catch (RuntimeException | IOException exception) {
            fail(exception);
        } finally {
            GL_STATE.restore();
            fabulousCaptured = false;
        }
    }

    /** Frees every GL object and clears the failure backoff (disconnect, resource reload). / 释放全部 GL 对象并清除失败退避（断线、资源重载）。 */
    public static void reset() {
        RenderSystem.assertOnRenderThread();
        releaseAll();
        failed = false;
        irisLogged = false;
    }

    private static void releaseAll() {
        releaseTargets();
        if (march != null) {
            march.delete();
            march = null;
        }
        if (composite != null) {
            composite.delete();
            composite = null;
        }
        if (depthCopy != null) {
            depthCopy.delete();
            depthCopy = null;
        }
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (matrixBuffer != null) {
            MemoryUtil.memFree(matrixBuffer);
            matrixBuffer = null;
        }
        SmokeNoiseTexture.release();
        SmokeCloudLighting.clear();
        passActive = false;
        fabulousCapturePending = false;
        fabulousCaptured = false;
    }

    /**
     * Picks up to {@link SmokeMarchRules#MAX_CLOUDS} on-screen clouds, nearest first, and the union of their screen
     * rectangles. / 选出至多 {@link SmokeMarchRules#MAX_CLOUDS} 个在屏幕上的烟团（由近到远）及其屏幕矩形的并集。
     */
    private static boolean select(List<SmokeVolumes.Volume> volumes, Vec3d camera, int width, int height) {
        CANDIDATES.clear();
        SELECTED.clear();
        for (SmokeVolumes.Volume volume : volumes) {
            if (SmokeMarchRules.screenRect(VIEW_PROJECTION,
                    (float) (volume.minX() - camera.x), (float) (volume.minY() - camera.y),
                    (float) (volume.minZ() - camera.z), (float) (volume.maxX() - camera.x),
                    (float) (volume.maxY() - camera.y), (float) (volume.maxZ() - camera.z),
                    width, height, CLOUD_RECT)) {
                CANDIDATES.add(volume);
            }
        }
        if (CANDIDATES.isEmpty()) {
            return false;
        }
        CANDIDATES.sort((a, b) -> Double.compare(distanceSquared(a, camera), distanceSquared(b, camera)));
        RECT[0] = width;
        RECT[1] = height;
        RECT[2] = 0;
        RECT[3] = 0;
        for (int i = 0; i < CANDIDATES.size() && SELECTED.size() < SmokeMarchRules.MAX_CLOUDS; i++) {
            SmokeVolumes.Volume volume = CANDIDATES.get(i);
            SmokeMarchRules.screenRect(VIEW_PROJECTION,
                    (float) (volume.minX() - camera.x), (float) (volume.minY() - camera.y),
                    (float) (volume.minZ() - camera.z), (float) (volume.maxX() - camera.x),
                    (float) (volume.maxY() - camera.y), (float) (volume.maxZ() - camera.z),
                    width, height, CLOUD_RECT);
            RECT[0] = Math.min(RECT[0], CLOUD_RECT[0]);
            RECT[1] = Math.min(RECT[1], CLOUD_RECT[1]);
            RECT[2] = Math.max(RECT[2], CLOUD_RECT[2]);
            RECT[3] = Math.max(RECT[3], CLOUD_RECT[3]);
            SELECTED.add(volume);
        }
        return true;
    }

    private static double distanceSquared(SmokeVolumes.Volume v, Vec3d camera) {
        return SmokeMarchRules.distanceSquaredToBox(camera.x, camera.y, camera.z,
                v.minX(), v.minY(), v.minZ(), v.maxX(), v.maxY(), v.maxZ());
    }

    private static void marchPass(WorldRenderContext context, ClientWorld world, Vec3d camera,
                                  SmokeViewTargets targets, int depthTexture, int width, int height) {
        SmokeProgram program = march;
        int count = SELECTED.size();
        for (int i = 0; i < count; i++) {
            SmokeVolumes.Volume volume = SELECTED.get(i);
            float minX = (float) (volume.minX() - camera.x);
            float minY = (float) (volume.minY() - camera.y);
            float minZ = (float) (volume.minZ() - camera.z);
            float maxX = (float) (volume.maxX() - camera.x);
            float maxY = (float) (volume.maxY() - camera.y);
            float maxZ = (float) (volume.maxZ() - camera.z);
            BOX_MIN[i * 3] = minX;
            BOX_MIN[i * 3 + 1] = minY;
            BOX_MIN[i * 3 + 2] = minZ;
            BOX_MAX[i * 3] = maxX;
            BOX_MAX[i * 3 + 1] = maxY;
            BOX_MAX[i * 3 + 2] = maxZ;
            BOX_INV_SIZE[i * 3] = 1.0F / (maxX - minX);
            BOX_INV_SIZE[i * 3 + 1] = 1.0F / (maxY - minY);
            BOX_INV_SIZE[i * 3 + 2] = 1.0F / (maxZ - minZ);
            SmokeCloudLighting.write(world, context.lightmapTextureManager(), volume.cloudId(), CLOUD_LIGHT, i * 3);
        }

        int lowWidth = targets.lowWidth();
        int lowHeight = targets.lowHeight();
        SmokeMarchRules.lowRect(RECT, lowWidth, lowHeight, LOW_RECT);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targets.lowFramebuffer());
        GL11.glViewport(0, 0, lowWidth, lowHeight);
        SmokeGlState.prepareFullscreenPass();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(LOW_RECT[0], LOW_RECT[1], LOW_RECT[2] - LOW_RECT[0], LOW_RECT[3] - LOW_RECT[1]);
        GL20.glUseProgram(program.id());

        int noise = SmokeNoiseTexture.textureId();
        bind(UNIT_DEPTH, GL11.GL_TEXTURE_2D, depthTexture);
        bind(UNIT_NOISE, GL12.GL_TEXTURE_3D, noise);
        for (int i = 0; i < SmokeMarchRules.MAX_CLOUDS; i++) {
            bind(UNIT_FIRST_VOLUME + i, GL12.GL_TEXTURE_3D, i < count ? SELECTED.get(i).texture() : 0);
        }

        INVERSE_VIEW_PROJECTION.set(VIEW_PROJECTION).invert();
        matrix(program.uniform("InvViewProj"), INVERSE_VIEW_PROJECTION);
        GL20.glUniform2f(program.uniform("FullSize"), width, height);
        GL20.glUniform1i(program.uniform("CloudCount"), count);
        GL20.glUniform3fv(program.uniform("BoxMin"), BOX_MIN);
        GL20.glUniform3fv(program.uniform("BoxMax"), BOX_MAX);
        GL20.glUniform3fv(program.uniform("BoxInvSize"), BOX_INV_SIZE);
        GL20.glUniform3fv(program.uniform("CloudLight"), CLOUD_LIGHT);

        double seconds = (world.getTime() + context.tickCounter().getTickDelta(false)) / 20.0;
        SmokeMotion.noiseOffsets(camera.x, camera.y, camera.z, seconds, NOISE_OFFSETS);
        double period = SmokeMarchRules.NOISE_PERIOD_BLOCKS;
        GL20.glUniform3f(program.uniform("NoiseOffset"), NOISE_OFFSETS[0], NOISE_OFFSETS[1], NOISE_OFFSETS[2]);
        GL20.glUniform3f(program.uniform("DetailOffset"), NOISE_OFFSETS[3], NOISE_OFFSETS[4], NOISE_OFFSETS[5]);
        GL20.glUniform1f(program.uniform("BreathePhase"), SmokeMotion.breathePhase(seconds));
        GL20.glUniform1f(program.uniform("NoiseFrequency"), (float) (1.0 / period));
        // Half-resolution pixel footprint per block of distance, in noise texels (explicit LOD inside the loop).
        // 每格视距下半分辨率像素的覆盖范围，以噪声纹素计（循环内使用显式 LOD）。
        float pixelAngle = 2.0F / (Math.abs(context.projectionMatrix().m11()) * lowHeight);
        GL20.glUniform1f(program.uniform("NoiseLodScale"),
                (float) (pixelAngle / (period / SmokeNoise.SIZE)));
        GL20.glUniform1f(program.uniform("NoiseStrength"), noise != 0 ? 1.0F : 0.0F);
        GL20.glUniform1f(program.uniform("Jitter"), (frame++ & 63) * SmokeMarchRules.JITTER_FRAME_OFFSET);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(program.uniform("FogColor"), fog[0], fog[1], fog[2], fog[3]);
        GL20.glUniform1f(program.uniform("FogStart"), RenderSystem.getShaderFogStart());
        GL20.glUniform1f(program.uniform("FogEnd"), RenderSystem.getShaderFogEnd());
        GL20.glUniform1i(program.uniform("FogShape"), RenderSystem.getShaderFogShape().getId());

        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void compositePass(SmokeViewTargets targets, int colorTexture, int depthTexture,
                                      int width, int height) {
        SmokeProgram program = composite;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targets.compositeFramebuffer(colorTexture));
        GL11.glViewport(0, 0, width, height);
        SmokeGlState.prepareFullscreenPass();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(RECT[0], RECT[1], RECT[2] - RECT[0], RECT[3] - RECT[1]);
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        // result = smoke + scene * transmittance; destination alpha untouched. / 结果 = 烟 + 场景 * 透射率；目标 alpha 不变。
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        GL20.glUseProgram(program.id());
        bind(UNIT_DEPTH, GL11.GL_TEXTURE_2D, depthTexture);
        bind(UNIT_SMOKE_COLOR, GL11.GL_TEXTURE_2D, targets.lowColor());
        bind(UNIT_SMOKE_DEPTH, GL11.GL_TEXTURE_2D, targets.lowDepth());
        matrix(program.uniform("InvViewProj"), INVERSE_VIEW_PROJECTION);
        GL20.glUniform2f(program.uniform("FullSize"), width, height);
        GL20.glUniform2f(program.uniform("LowSize"), targets.lowWidth(), targets.lowHeight());
        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /** Shader copy of a depth texture into this view's R32F copy. / 把深度纹理以着色器方式拷到本视图的 R32F 副本。 */
    private static void copyDepth(int sourceDepth, int width, int height) {
        SmokeViewTargets targets = TARGETS[passSlot];
        targets.ensureCopy(width, height);
        targetsHeld = true;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targets.copyFramebuffer());
        GL11.glViewport(0, 0, width, height);
        SmokeGlState.prepareFullscreenPass();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL20.glUseProgram(depthCopy.id());
        bind(UNIT_DEPTH, GL11.GL_TEXTURE_2D, sourceDepth);
        GL30.glBindVertexArray(vertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void ensurePrograms(ResourceManager resources) throws IOException {
        if (march != null && composite != null && depthCopy != null) {
            return;
        }
        if (march == null) {
            march = SmokeProgram.load(resources, "fullscreen.vsh", "march.fsh", "OutSmoke", "OutDepth");
            GL20.glUseProgram(march.id());
            march.sampler("SceneDepth", UNIT_DEPTH);
            march.sampler("Noise", UNIT_NOISE);
            for (int i = 0; i < SmokeMarchRules.MAX_CLOUDS; i++) {
                march.sampler("Volume" + i, UNIT_FIRST_VOLUME + i);
            }
        }
        if (composite == null) {
            composite = SmokeProgram.load(resources, "fullscreen.vsh", "composite.fsh", "OutColor");
            GL20.glUseProgram(composite.id());
            composite.sampler("SceneDepth", UNIT_DEPTH);
            composite.sampler("SmokeColor", UNIT_SMOKE_COLOR);
            composite.sampler("SmokeDepth", UNIT_SMOKE_DEPTH);
        }
        if (depthCopy == null) {
            depthCopy = SmokeProgram.load(resources, "fullscreen.vsh", "depth_copy.fsh", "OutDepth");
            GL20.glUseProgram(depthCopy.id());
            depthCopy.sampler("SourceDepth", UNIT_DEPTH);
        }
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays();
        }
    }

    private static void bind(int unit, int target, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(target, texture);
    }

    private static void matrix(int location, Matrix4f matrix) {
        if (matrixBuffer == null) {
            matrixBuffer = MemoryUtil.memAllocFloat(16);
        }
        matrix.get(matrixBuffer);
        GL20.glUniformMatrix4fv(location, false, matrixBuffer);
    }

    private static void idle() {
        if (targetsHeld && Util.getMeasuringTimeNano() - lastActiveNanos > IDLE_RELEASE_NANOS) {
            releaseTargets();
        }
    }

    private static void releaseTargets() {
        for (SmokeViewTargets targets : TARGETS) {
            targets.release();
        }
        targetsHeld = false;
    }

    private static void fail(Exception exception) {
        if (!failed) {
            SparkWitch.LOGGER.error("BEAR smoke rendering failed; retrying in {} s",
                    RETRY_NANOS / 1_000_000_000L, exception);
        }
        // Called with the GL state saved; the caller restores it after the objects are gone.
        // 调用时 GL 状态已保存；对象删除后由调用方恢复。
        releaseAll();
        failed = true;
        retryAtNanos = Util.getMeasuringTimeNano() + RETRY_NANOS;
    }
}
