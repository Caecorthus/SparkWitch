package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.caecorthus.sparkwitch.client.blind.render.BlindShaderPackCheck;
import dev.caecorthus.sparkwitch.client.render.entity.SecondaryWorldPass;
import dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.SmokeCloudSnapshot;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
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
import java.util.Map;

/**
 * The visible BEAR smoke: a GPU ray march of the {@link SmokeVolumes} density volumes, drawn by every
 * {@code WorldRenderer#render} call (the main view, Seeker camera views, and secondary passes such as the USEC scope
 * picture-in-picture) with that call's own camera, view rotation and projection; nothing is cached from the main view.
 * <ol>
 *   <li>{@link #beginWorldPass} at {@code WorldRenderEvents.START}: decides whether this world render has smoke work
 *   (zero cost when {@link SmokeClientState} is empty or {@link SmokeVolumes#prepare} returns nothing).</li>
 *   <li>The half-resolution march into a private RGBA16F target runs while the world target's depth holds opaque
 *   terrain and every entity but no translucent terrain, so smoke behind stained glass and the hull's translucent
 *   panels is still drawn: Fast/Fancy at the {@code "translucent"} profiler swap ({@link #beforeTranslucentTerrain},
 *   {@code SmokeFancyMarchMixin}), Fabulous! at the head of the particle draw ({@link #beforeParticles},
 *   {@code SmokeFabulousDepthMixin}). With Iris loaded (no pack) the march waits for {@code WorldRenderEvents.LAST},
 *   because Iris defers entity draws past the translucent swap.</li>
 *   <li>{@link #renderWorldPass} at {@code WorldRenderEvents.LAST}, after translucent terrain, particles, weather and
 *   the Fabulous! composite, before the first-person hand: a depth-aware, grain-removing upsample blended onto the
 *   world target's colour, compared against the final depth (main in Fast/Fancy, the translucent target in
 *   Fabulous!, whose composite clears main).</li>
 * </ol>
 * Cost controls that leave the result unchanged: the march is compiled per cloud-slot count
 * ({@link SmokeMarchRules#CLOUD_SLOT_VARIANTS}), so a one-cloud view does not loop over eight, and steps that provably
 * read no density skip their noise and density taps ({@link SmokeOccupancy}).
 * Depth textures are only ever sampled through framebuffers that do not attach them, so there is no feedback loop
 * and no depth copy. Under an Iris shader pack the pass is skipped (logged once). Every GL state the passes touch is
 * saved and restored ({@link SmokeGlState}). A failure logs once, frees everything and retries after
 * {@link #RETRY_NANOS}. Client-only presentation; reads {@link SmokeClientState} only.
 * BEAR 可见烟雾：对 {@link SmokeVolumes} 浓度体做 GPU 光线步进，由每一次 {@code WorldRenderer#render} 调用（主画面、
 * 搜寻者摄像头画面，以及 USEC 瞄具画中画等第二次世界渲染）各自用本次调用的相机、视角旋转与投影绘制，不缓存主画面的任何值。
 * 1）{@link #beginWorldPass} 于 {@code WorldRenderEvents.START}：判断本次世界渲染是否有烟要画（{@link SmokeClientState}
 * 为空或 {@link SmokeVolumes#prepare} 无结果时零开销）。2）半分辨率步进写入私有 RGBA16F 目标，时机选在世界目标深度已含
 * 不透明地形与全部实体、尚无半透明地形之时，因此彩色玻璃与车厢半透明板后面的烟照样画出：Fast/Fancy 在
 * {@code "translucent"} 分析器切换处（{@link #beforeTranslucentTerrain}，{@code SmokeFancyMarchMixin}），Fabulous! 在粒子
 * 绘制开头（{@link #beforeParticles}，{@code SmokeFabulousDepthMixin}）。装有 Iris（未开光影）时步进推迟到
 * {@code WorldRenderEvents.LAST}，因为 Iris 把实体绘制推迟到半透明切换之后。3）{@link #renderWorldPass} 于
 * {@code WorldRenderEvents.LAST}（半透明地形、粒子、天气与 Fabulous! 合成之后，第一人称手部之前）：按深度感知、去颗粒的方式
 * 放大并混合到世界目标颜色上，对比的是最终深度（Fast/Fancy 用主深度，Fabulous! 用半透明目标的深度，因为其合成会清掉主深度）。
 * 不改变结果的开销控制：步进按烟团槽数分别编译（{@link SmokeMarchRules#CLOUD_SLOT_VARIANTS}），只有一团烟的画面不必循环
 * 8 次；可证明读不到浓度的步省去噪声与浓度采样（{@link SmokeOccupancy}）。深度纹理只经不挂载它们的帧缓冲采样，因此没有
 * 反馈回路也无需拷贝深度。开着 Iris 光影包时跳过（只记录一次日志）。各步骤改动的 GL 状态全部保存并恢复
 * （{@link SmokeGlState}）。失败时记录一次日志、释放全部资源，{@link #RETRY_NANOS} 后重试。
 * 纯客户端展示；只读取 {@link SmokeClientState}。
 */
public final class SmokeRenderer {
    static final long RETRY_NANOS = 30_000_000_000L;
    /** Offscreen targets are freed after this long without smoke. / 无烟持续这么久后释放离屏目标。 */
    static final long IDLE_RELEASE_NANOS = 10_000_000_000L;
    private static final int UNIT_DEPTH = 0;
    private static final int UNIT_NOISE = 1;
    private static final int UNIT_FIRST_VOLUME = 2;
    private static final int UNIT_FIRST_OCCUPANCY = UNIT_FIRST_VOLUME + SmokeMarchRules.MAX_CLOUDS;
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
    private static final float[] CLOUD_LIGHT = new float[4 * SmokeMarchRules.MAX_CLOUDS];
    private static final float[] OCCUPANCY_MIN = new float[3 * SmokeMarchRules.OCCUPANCY_CLOUDS];
    private static final float[] OCCUPANCY_INV_SIZE = new float[3 * SmokeMarchRules.OCCUPANCY_CLOUDS];
    private static final int[] OCCUPANCY_TEXTURES = new int[SmokeMarchRules.OCCUPANCY_CLOUDS];
    private static final float[] NOISE_OFFSETS = new float[9];
    private static final float[] SKY_LIGHT = new float[6];
    private static final float[] SKY_TINT = new float[3];
    private static final int[] RECT = new int[4];
    private static final int[] CLOUD_RECT = new int[4];
    private static final int[] LOW_RECT = new int[4];
    @Nullable
    private static FloatBuffer matrixBuffer;
    /** March programs per {@link SmokeMarchRules#CLOUD_SLOT_VARIANTS}, compiled on first use. / 各烟团槽数的步进程序，首次使用时编译。 */
    private static final SmokeProgram[] MARCH_VARIANTS = new SmokeProgram[SmokeMarchRules.CLOUD_SLOT_VARIANTS.length];
    /** The variant of the current march. / 本次步进所用的变体。 */
    @Nullable
    private static SmokeProgram march;
    @Nullable
    private static SmokeProgram composite;
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
    /** Where this world render marches. / 本次世界渲染在何处步进。 */
    private static MarchPoint marchPoint = MarchPoint.LAST;
    /** The march ran this world render; {@link #smokeOnScreen} says whether it drew anything. / 本次世界渲染已步进。 */
    private static boolean marched;
    private static boolean smokeOnScreen;
    @Nullable
    private static WorldRenderContext passContext;
    @Nullable
    private static Boolean irisLoaded;

    private enum MarchPoint {
        BEFORE_TRANSLUCENT,
        BEFORE_PARTICLES,
        LAST
    }

    private SmokeRenderer() {
    }

    /** {@code WorldRenderEvents.START}; render thread. / {@code WorldRenderEvents.START}；渲染线程。 */
    public static void beginWorldPass(WorldRenderContext context) {
        passActive = false;
        marched = false;
        smokeOnScreen = false;
        passContext = null;
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
        passContext = context;
        passSlot = SecondaryWorldPass.isActive() ? 1 : 0;
        lastActiveNanos = Util.getMeasuringTimeNano();
        if (context.worldRenderer().getTranslucentFramebuffer() != null) {
            marchPoint = MarchPoint.BEFORE_PARTICLES;
        } else {
            marchPoint = irisLoaded() ? MarchPoint.LAST : MarchPoint.BEFORE_TRANSLUCENT;
        }
    }

    /**
     * {@code WorldRenderer#render} at its {@code "translucent"} profiler swaps (both graphics branches); render thread.
     * Fast/Fancy marches here: main depth holds opaque terrain and the full entity flush, translucent terrain not yet.
     * {@code WorldRenderer#render} 的 {@code "translucent"} 分析器切换处（两个画质分支）；渲染线程。Fast/Fancy 在此步进：
     * 主深度已含不透明地形与实体全部刷新，尚无半透明地形。
     */
    public static void beforeTranslucentTerrain() {
        if (passActive && !marched && marchPoint == MarchPoint.BEFORE_TRANSLUCENT) {
            marchNow(MinecraftClient.getInstance().getFramebuffer().getDepthAttachment());
        }
    }

    /**
     * Head of {@code ParticleManager#renderParticles}; render thread. Fabulous! marches here: translucent terrain went
     * to its own target, so main depth is opaque terrain and every entity, and the composite clears it later.
     * {@code ParticleManager#renderParticles} 开头；渲染线程。Fabulous! 在此步进：半透明地形画在自己的目标里，主深度是
     * 不透明地形与全部实体，之后会被合成清掉。
     */
    public static void beforeParticles() {
        if (passActive && !marched && marchPoint == MarchPoint.BEFORE_PARTICLES) {
            marchNow(MinecraftClient.getInstance().getFramebuffer().getDepthAttachment());
        }
    }

    /** {@code WorldRenderEvents.LAST}; render thread. / {@code WorldRenderEvents.LAST}；渲染线程。 */
    public static void renderWorldPass(WorldRenderContext context) {
        if (!passActive) {
            return;
        }
        Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
        Framebuffer translucent = context.worldRenderer().getTranslucentFramebuffer();
        // The final depth: Fabulous! composites over a cleared main depth, its translucent target keeps the scene's.
        // 最终深度：Fabulous! 合成会清掉主深度，其半透明目标保留场景深度。
        int finalDepth = translucent != null ? translucent.getDepthAttachment() : target.getDepthAttachment();
        if (!marched) {
            // Iris loaded, or the early hook never ran (a mod cancelled it). / 装有 Iris，或提前的钩子未运行。
            marchNow(finalDepth);
        }
        passActive = false;
        passContext = null;
        if (!smokeOnScreen) {
            return;
        }
        GL_STATE.save();
        try {
            compositePass(TARGETS[passSlot], target.getColorAttachment(), finalDepth,
                    target.textureWidth, target.textureHeight);
        } catch (RuntimeException exception) {
            fail(exception);
        } finally {
            GL_STATE.restore();
        }
    }

    private static void marchNow(int depthTexture) {
        marched = true;
        WorldRenderContext context = passContext;
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer target = client.getFramebuffer();
        int width = target.textureWidth;
        int height = target.textureHeight;
        ClientWorld world = context == null ? null : context.world();
        if (world == null || width <= 0 || height <= 0) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        // Camera-relative world -> clip, from THIS world render's matrices. / 相对相机的世界 -> 裁剪空间，取自本次世界渲染的矩阵。
        VIEW_PROJECTION.set(context.projectionMatrix()).mul(context.positionMatrix());
        if (!select(SmokeVolumes.prepare(world), camera, width, height)) {
            return;
        }
        GL_STATE.save();
        try {
            ensurePrograms(client.getResourceManager(), SmokeMarchRules.cloudSlotVariant(SELECTED.size()));
            SmokeViewTargets targets = TARGETS[passSlot];
            targets.ensureLow(width, height);
            targetsHeld = true;
            marchPass(context, world, camera, targets, depthTexture, width, height);
            smokeOnScreen = true;
        } catch (RuntimeException | IOException exception) {
            fail(exception);
        } finally {
            GL_STATE.restore();
        }
    }

    private static boolean irisLoaded() {
        if (irisLoaded == null) {
            irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
        }
        return irisLoaded;
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
        for (int i = 0; i < MARCH_VARIANTS.length; i++) {
            if (MARCH_VARIANTS[i] != null) {
                MARCH_VARIANTS[i].delete();
                MARCH_VARIANTS[i] = null;
            }
        }
        march = null;
        if (composite != null) {
            composite.delete();
            composite = null;
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
        SmokeOccupancy.release();
        SmokeCloudLighting.clear();
        passActive = false;
        passContext = null;
        marched = false;
        smokeOnScreen = false;
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
            if (i < SmokeMarchRules.OCCUPANCY_CLOUDS) {
                occupancyBox(volume, camera, i);
            }
            SmokeCloudLighting.write(world, context.lightmapTextureManager(), volume.cloudId(), CLOUD_LIGHT, i * 4);
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
        int occupancyMask = 0;
        for (int i = 0; i < SmokeMarchRules.OCCUPANCY_CLOUDS; i++) {
            int texture = i < count ? OCCUPANCY_TEXTURES[i] : 0;
            bind(UNIT_FIRST_OCCUPANCY + i, GL12.GL_TEXTURE_3D, texture);
            if (texture != 0) {
                occupancyMask |= 1 << i;
            }
        }

        INVERSE_VIEW_PROJECTION.set(VIEW_PROJECTION).invert();
        matrix(program.uniform("InvViewProj"), INVERSE_VIEW_PROJECTION);
        GL20.glUniform2f(program.uniform("FullSize"), width, height);
        GL20.glUniform1i(program.uniform("CloudCount"), count);
        GL20.glUniform3fv(program.uniform("BoxMin"), BOX_MIN);
        GL20.glUniform3fv(program.uniform("BoxMax"), BOX_MAX);
        GL20.glUniform3fv(program.uniform("BoxInvSize"), BOX_INV_SIZE);
        GL20.glUniform4fv(program.uniform("CloudLight"), CLOUD_LIGHT);
        GL20.glUniform1i(program.uniform("OccupancyMask"), occupancyMask);
        GL20.glUniform3fv(program.uniform("OccupancyMin"), OCCUPANCY_MIN);
        GL20.glUniform3fv(program.uniform("OccupancyInvSize"), OCCUPANCY_INV_SIZE);
        float tickDelta = context.tickCounter().getTickDelta(false);
        SmokeSkyLight.compute(world.getSkyAngle(tickDelta), world.getRainGradient(tickDelta), world.getMoonSize(),
                SKY_LIGHT);
        GL20.glUniform3f(program.uniform("LightDir"), SKY_LIGHT[0], SKY_LIGHT[1], SKY_LIGHT[2]);
        GL20.glUniform3f(program.uniform("LightColor"), SKY_LIGHT[3], SKY_LIGHT[4], SKY_LIGHT[5]);
        Vec3d sky = world.getSkyColor(camera, tickDelta);
        SmokeSkyLight.tint(sky.x, sky.y, sky.z, SKY_TINT);
        GL20.glUniform3f(program.uniform("SkyTint"), SKY_TINT[0], SKY_TINT[1], SKY_TINT[2]);

        double seconds = (world.getTime() + tickDelta) / 20.0;
        SmokeMotion.noiseOffsets(camera.x, camera.y, camera.z, seconds, NOISE_OFFSETS);
        double period = SmokeMarchRules.NOISE_PERIOD_BLOCKS;
        GL20.glUniform3f(program.uniform("NoiseOffset"), NOISE_OFFSETS[0], NOISE_OFFSETS[1], NOISE_OFFSETS[2]);
        GL20.glUniform3f(program.uniform("DetailOffset"), NOISE_OFFSETS[3], NOISE_OFFSETS[4], NOISE_OFFSETS[5]);
        GL20.glUniform3f(program.uniform("BigOffset"), NOISE_OFFSETS[6], NOISE_OFFSETS[7], NOISE_OFFSETS[8]);
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

    /**
     * Empty-space texture of selected cloud {@code slot} and its camera-relative box (the volume's, widened by
     * {@link SmokeOccupancy#EXTENSION} cells). / 选中烟团 {@code slot} 的空区纹理及其相对相机的盒子（浓度体盒子外扩
     * {@link SmokeOccupancy#EXTENSION} 格）。
     */
    private static void occupancyBox(SmokeVolumes.Volume volume, Vec3d camera, int slot) {
        OCCUPANCY_TEXTURES[slot] = SmokeOccupancy.texture(volume);
        double pad = SmokeOccupancy.EXTENSION * (double) SmokeCloudSnapshot.CELL_SIZE;
        double minX = volume.minX() - pad;
        double minY = volume.minY() - pad;
        double minZ = volume.minZ() - pad;
        OCCUPANCY_MIN[slot * 3] = (float) (minX - camera.x);
        OCCUPANCY_MIN[slot * 3 + 1] = (float) (minY - camera.y);
        OCCUPANCY_MIN[slot * 3 + 2] = (float) (minZ - camera.z);
        OCCUPANCY_INV_SIZE[slot * 3] = (float) (1.0 / (volume.maxX() + pad - minX));
        OCCUPANCY_INV_SIZE[slot * 3 + 1] = (float) (1.0 / (volume.maxY() + pad - minY));
        OCCUPANCY_INV_SIZE[slot * 3 + 2] = (float) (1.0 / (volume.maxZ() + pad - minZ));
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

    private static void ensurePrograms(ResourceManager resources, int variant) throws IOException {
        march = MARCH_VARIANTS[variant];
        if (march == null) {
            march = SmokeProgram.load(resources, "fullscreen.vsh", "march.fsh",
                    Map.of("CLOUD_SLOTS", Integer.toString(SmokeMarchRules.CLOUD_SLOT_VARIANTS[variant])),
                    "OutSmoke", "OutDepth");
            MARCH_VARIANTS[variant] = march;
            GL20.glUseProgram(march.id());
            march.sampler("SceneDepth", UNIT_DEPTH);
            march.sampler("Noise", UNIT_NOISE);
            for (int i = 0; i < SmokeMarchRules.MAX_CLOUDS; i++) {
                march.sampler("Volume" + i, UNIT_FIRST_VOLUME + i);
            }
            for (int i = 0; i < SmokeMarchRules.OCCUPANCY_CLOUDS; i++) {
                march.sampler("Occupancy" + i, UNIT_FIRST_OCCUPANCY + i);
            }
        }
        if (composite == null) {
            composite = SmokeProgram.load(resources, "fullscreen.vsh", "composite.fsh", "OutColor");
            GL20.glUseProgram(composite.id());
            composite.sampler("SceneDepth", UNIT_DEPTH);
            composite.sampler("SmokeColor", UNIT_SMOKE_COLOR);
            composite.sampler("SmokeDepth", UNIT_SMOKE_DEPTH);
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
