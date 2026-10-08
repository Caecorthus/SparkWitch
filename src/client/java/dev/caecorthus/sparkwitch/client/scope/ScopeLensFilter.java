package dev.caecorthus.sparkwitch.client.scope;

import com.mojang.blaze3d.platform.GlDebugInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.mixin.PostEffectProcessorAccessor;
import dev.doctor4t.wathe.compat.IrisHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.JsonEffectShaderProgram;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Client only. The ZOOM_BLUR lens picture: a PRIVATE {@link PostEffectProcessor} on
 * {@code sparkwitch:shaders/post/scope_lens.json}, never {@code GameRenderer.postProcessor} (camera switches close that
 * one). Four passes per frame: a separable Gaussian of {@code main} at half resolution (two passes), one composite
 * (sharp lens with barrel distortion, edge transmittance, coating tint, faint smudges, a reflection streak and the
 * scope-shadow crescent; blurred periphery darkening toward a dark, never black, tube rim), and a copy back into
 * {@code main}. It runs from {@code ScopeGameRendererMixin} before the GUI binds the main framebuffer, so the HUD and
 * the reticle stay sharp.
 * <p>
 * Lifecycle (Seeker / Blind pattern): built on first use, rebuilt on framebuffer resize (the two blur targets are then
 * re-halved), closed after {@link ScopeRules#RELEASE_AFTER_IDLE_NANOS} unscoped, on disconnect and on resource reload.
 * A load or render failure closes it and retries after a growing backoff; meanwhile, and whenever Iris reports a
 * shader pack, {@link #fallbackActive()} makes the HUD draw its semi-transparent ring instead.
 * 仅客户端。全画面放大档的镜片画面：基于 {@code sparkwitch:shaders/post/scope_lens.json} 的私有 {@link PostEffectProcessor}，
 * 绝不使用 {@code GameRenderer.postProcessor}（切换相机会关闭它）。每帧四个 pass：半分辨率的可分离高斯模糊（两个 pass）、
 * 一次合成（清晰镜片带桶形畸变、边缘透光衰减、镀膜色调、淡淡的污渍、反光条纹与镜内阴影月牙；模糊的镜外区域向暗色但
 * 绝不全黑的镜筒边逐渐变暗），以及拷回 {@code main}。由 {@code ScopeGameRendererMixin} 在 GUI 绑定主帧缓冲之前运行，
 * 因此 HUD 与分划保持清晰。
 * <p>
 * 生命周期（参照搜寻者 / 盲人）：首次使用时创建，帧缓冲尺寸变化时重建（两个模糊目标随后重新减半），退出开镜
 * {@link ScopeRules#RELEASE_AFTER_IDLE_NANOS} 后、断线与资源重载时关闭。加载或渲染失败时关闭并在逐渐变长的退避后重试；
 * 在此期间以及 Iris 报告光影包时，{@link #fallbackActive()} 让 HUD 改画半透明环。
 */
public final class ScopeLensFilter {
    public static final Identifier EFFECT = SparkWitch.id("shaders/post/scope_lens.json");
    /** The composite program; its pass receives the per-frame uniforms. / 合成程序；其 pass 接收逐帧 uniform。 */
    public static final String LENS_PROGRAM = "sparkwitch_scope_lens";
    /** Blur targets kept at half the framebuffer size. / 保持为帧缓冲一半尺寸的模糊目标。 */
    public static final List<String> HALF_RESOLUTION_TARGETS = List.of("scope_blur_a", "scope_blur_b");
    private static final Identifier RELOAD_LISTENER_ID = SparkWitch.id("scope_lens_filter");
    private static boolean registered;
    @Nullable
    private static PostEffectProcessor processor;
    @Nullable
    private static Framebuffer processorTarget;
    @Nullable
    private static PostEffectPass lensPass;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean failed;
    private static int failures;
    private static long retryAtNanos;
    private static volatile boolean irisCheckBroken;

    private ScopeLensFilter() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Reload listeners apply on the render thread, where GL objects may be released.
        // 重载监听器在渲染线程执行，可在此安全释放 GL 对象。
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return RELOAD_LISTENER_ID;
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        reset();
                    }
                });
    }

    /**
     * Releases the processor and clears the failure backoff; deferred to the render thread when called elsewhere.
     * 释放处理器并清除失败退避；在其他线程调用时推迟到渲染线程。
     */
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ScopeLensFilter::reset);
            return;
        }
        release();
        failed = false;
        failures = 0;
        retryAtNanos = 0L;
    }

    /** Closes the processor (no-op when none is open). / 关闭处理器（未打开时无操作）。 */
    public static void release() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorTarget = null;
        lensPass = null;
        processorWidth = -1;
        processorHeight = -1;
    }

    /**
     * True when the HUD must draw the fallback ring: a shader pack is in use or the lens pipeline is failing.
     * HUD 需要绘制回退环时为 true：正在使用光影包，或镜片管线失败。
     */
    public static boolean fallbackActive() {
        return failed || shaderPackInUse();
    }

    /**
     * Wathe's public Iris facade ({@code IrisApi.isShaderPackInUse()}); a broken Iris API counts as a pack in use, so
     * the scope fails closed to the HUD fallback.
     * Wathe 的公共 Iris 门面（{@code IrisApi.isShaderPackInUse()}）；Iris API 异常时视为正在使用光影包，开镜失败时关闭到
     * HUD 回退。
     */
    public static boolean shaderPackInUse() {
        if (irisCheckBroken) {
            return true;
        }
        try {
            return IrisHelper.isIrisShaderPackInUse();
        } catch (LinkageError | RuntimeException exception) {
            irisCheckBroken = true;
            SparkWitch.LOGGER.warn("Unable to query Iris; the scope uses its HUD fallback", exception);
            return true;
        }
    }

    /** Unscoped frame: close the processor once it has idled long enough. / 未开镜帧：空闲足够久后关闭处理器。 */
    static void idle(long idleNanos) {
        if (processor != null && ScopeRules.releasesProcessor(idleNanos)) {
            release();
        }
    }

    /**
     * Renders the lens over the main framebuffer; render thread only, right before the GUI pass.
     * 在主帧缓冲上渲染镜片；仅渲染线程，紧接在 GUI 之前。
     *
     * @param fade          lens fade-in, 0..1 / 镜片淡入，0..1
     * @param shadowX       scope-shadow offset, lens radii, GUI +x right / 镜内阴影偏移（镜片半径，GUI +x 向右）
     * @param shadowY       scope-shadow offset, lens radii, GUI +y down / 镜内阴影偏移（镜片半径，GUI +y 向下）
     * @param frameDuration frame duration in ticks (the processor's clock) / 以 tick 计的帧时长（处理器时钟）
     */
    static void render(MinecraftClient client, float fade, float shadowX, float shadowY, float frameDuration) {
        if (shaderPackInUse()) {
            // Iris owns the frame; drop our GL targets until the pack is turned off (HUD fallback meanwhile).
            // Iris 接管画面；在关闭光影包前释放我们的 GL 目标（期间使用 HUD 回退）。
            release();
            return;
        }
        PostEffectProcessor active = ensureProcessor(client);
        PostEffectPass lens = lensPass;
        if (active == null || lens == null) {
            return;
        }
        JsonEffectShaderProgram program = lens.getProgram();
        program.getUniformByNameOrDummy("LensCenter").set(0.5F, 0.5F);
        program.getUniformByNameOrDummy("LensRadius").set(ScopeLensGeometry.LENS_RADIUS_SHARE);
        // GL texture space has +y up; the GUI-axis shadow has +y down. / GL 纹理空间 +y 向上，GUI 阴影 +y 向下。
        program.getUniformByNameOrDummy("ShadowOffset").set(shadowX, -shadowY);
        program.getUniformByNameOrDummy("Fade").set(fade);
        // Same state vanilla sets before its own post pass. / 与原版后处理前设置的状态一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            active.render(frameDuration);
        } catch (RuntimeException exception) {
            fail(exception);
        } finally {
            client.getFramebuffer().beginWrite(false);
        }
    }

    @Nullable
    private static PostEffectProcessor ensureProcessor(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            return null;
        }
        if (failed) {
            if (Util.getMeasuringTimeNano() - retryAtNanos < 0L) {
                return null;
            }
            // Backoff elapsed: rebuild from scratch. / 退避结束：从头重建。
            failed = false;
        }
        if (processor != null && processorTarget != framebuffer) {
            release();
        }
        try {
            if (processor == null) {
                processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(),
                        framebuffer, EFFECT);
                processorTarget = framebuffer;
                processorWidth = -1;
                processorHeight = -1;
                lensPass = findLensPass(processor);
            }
            if (processorWidth != framebuffer.textureWidth || processorHeight != framebuffer.textureHeight) {
                // First use or window resize: full-size targets follow the framebuffer, then the blur targets are
                // halved again (the scope vertex shader maps each pass to its own output, whatever its size).
                // 首次使用或窗口尺寸变化：全尺寸目标跟随帧缓冲，随后再把模糊目标减半（开镜顶点着色器按各 pass 自己的
                // 输出尺寸映射，与尺寸无关）。
                processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
                int halfWidth = Math.max(1, framebuffer.textureWidth / 2);
                int halfHeight = Math.max(1, framebuffer.textureHeight / 2);
                for (String name : HALF_RESOLUTION_TARGETS) {
                    Framebuffer target = processor.getSecondaryTarget(name);
                    if (target == null) {
                        throw new IllegalStateException("scope_lens.json lacks the target " + name);
                    }
                    target.resize(halfWidth, halfHeight, MinecraftClient.IS_SYSTEM_MAC);
                }
                processorWidth = framebuffer.textureWidth;
                processorHeight = framebuffer.textureHeight;
            }
            return processor;
        } catch (IOException | RuntimeException exception) {
            // JsonSyntaxException, GL compile errors and resize failures: HUD fallback until the retry.
            // JsonSyntaxException、GL 编译错误与尺寸重建失败：重试前使用 HUD 回退。
            fail(exception);
            return null;
        }
    }

    private static PostEffectPass findLensPass(PostEffectProcessor processor) {
        for (PostEffectPass pass : ((PostEffectProcessorAccessor) processor).sparkwitch$getPasses()) {
            if (LENS_PROGRAM.equals(pass.getName())) {
                return pass;
            }
        }
        throw new IllegalStateException("scope_lens.json lacks the " + LENS_PROGRAM + " pass");
    }

    private static void fail(Exception exception) {
        failures++;
        int retrySeconds = ScopeRules.retryDelaySeconds(failures);
        if (failures == 1) {
            SparkWitch.LOGGER.warn("The scope lens filter failed on {} / {} / OpenGL {}; using the HUD fallback, "
                            + "retrying in {} s", GlDebugInfo.getRenderer(), GlDebugInfo.getVendor(),
                    GlDebugInfo.getVersion(), retrySeconds, exception);
        } else {
            SparkWitch.LOGGER.warn("The scope lens filter failed again (#{}): {}; retrying in {} s", failures,
                    exception.toString(), retrySeconds);
        }
        release();
        failed = true;
        retryAtNanos = Util.getMeasuringTimeNano() + retrySeconds * 1_000_000_000L;
    }
}
