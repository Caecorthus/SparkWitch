package dev.caecorthus.sparkwitch.client.riftwalker.session;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.compat.IrisHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * The 100% grayscale view inside a Rift Gate (D10): a PRIVATE {@link PostEffectProcessor} on SparkWitch's own copy of
 * the SparkTraits depression shader ({@code sparkwitch:shaders/post/perception.json}) at full desaturation, never
 * {@code GameRenderer.postProcessor} (server camera writers close that one) and never the Black Raven / Wraith / Last
 * Escape compositor. Lifecycle copied from {@code SeekerViewFilter}: released on every inactive frame, reset on
 * disconnect, client stop and resource reload (on the render thread), rebuilt on resize, and marked failed on load
 * errors. After the grey pass the entity-outline framebuffer is composited again, so instinct outlines keep their
 * colours (D10). Full grey is order-independent with SparkTraits Depression and Black Raven (gray in, gray out). When
 * the shader cannot run (load failure or an Iris shader pack) {@link #fallbackActive()} tells the HUD to draw a grey
 * tint and vignette instead.
 * 裂隙门内 100% 黑白画面（D10）：基于 SparkWitch 自带的 SparkTraits 抑郁着色器副本（{@code sparkwitch:shaders/post/perception.json}）
 * 的私有 {@link PostEffectProcessor}，满去饱和；绝不使用 {@code GameRenderer.postProcessor}（服务端相机写入方会关闭它），也不使用
 * 黑羽鸦/怨灵/Last Escape 合成器。生命周期照抄 {@code SeekerViewFilter}：非激活帧释放，断线、客户端停止与资源重载时（在渲染线程）
 * 重置，窗口尺寸变化时重建，加载出错时标记失败。灰度处理后重新合成实体描边帧缓冲，使本能描边保留颜色（D10）。全灰与 SparkTraits
 * 抑郁、黑羽鸦的执行顺序无关（灰进灰出）。着色器无法运行时（加载失败或 Iris 光影包），{@link #fallbackActive()} 让 HUD 改画灰色
 * 叠层与暗角。
 */
public final class RiftGrayscaleFilter {
    static final Identifier SHADER = SparkWitch.id("shaders/post/perception.json");
    private static final Identifier RELOAD_LISTENER_ID = SparkWitch.id("rift_grayscale_filter");
    /** Full desaturation, unscaled BT.709 luma, no spread, neutral brightness. / 全去饱和、未缩放 BT.709 亮度、无扩散、亮度不变。 */
    static final float DESATURATION = 1.0F;
    static final float LUMINANCE_SCALE = 1.0F;
    static final float LUMA_RED = 0.2126F;
    static final float LUMA_GREEN = 0.7152F;
    static final float LUMA_BLUE = 0.0722F;
    static final float SPREAD = 0.0F;
    static final float BRIGHTNESS = 1.0F;
    private static boolean registered;
    @Nullable
    private static PostEffectProcessor processor;
    @Nullable
    private static Framebuffer processorTarget;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static volatile boolean failed;
    private static volatile boolean irisCheckBroken;

    private RiftGrayscaleFilter() {
    }

    public static synchronized void register() {
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
     * Releases the processor and allows a fresh load. Connection events may fire off the render thread, where closing
     * GL targets throws, so the reset is then deferred to the render thread.
     * 释放处理器并允许重新加载。连接事件可能不在渲染线程触发，在那里关闭 GL 目标会抛异常，因此此时推迟到渲染线程执行。
     */
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(RiftGrayscaleFilter::reset);
            return;
        }
        release();
        failed = false;
    }

    /** Closes the processor (no-op when none is open); called on every inactive frame. / 关闭处理器；非激活帧都会调用。 */
    public static void release() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorTarget = null;
        processorWidth = -1;
        processorHeight = -1;
    }

    /**
     * True when the post shader cannot draw the grey view, so the HUD must draw its tint fallback instead.
     * 后处理着色器无法绘制灰色画面时为 true，此时 HUD 改画灰色回退。
     */
    public static boolean fallbackActive() {
        return failed || shaderPackInUse();
    }

    /**
     * Renders the grey pass over the main framebuffer, render thread only, right before the GUI pass.
     * 在主帧缓冲上渲染灰度，仅限渲染线程，紧接在 GUI 渲染之前。
     *
     * @param frameDuration frame duration in ticks / 以 tick 计的帧时长
     */
    public static void render(float frameDuration) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || shaderPackInUse()) {
            release();
            return;
        }
        PostEffectProcessor active = ensureProcessor(client);
        if (active == null) {
            return;
        }
        active.setUniforms("DesaturateFactor", DESATURATION);
        active.setUniforms("LuminanceScale", LUMINANCE_SCALE);
        active.setUniforms("LumaRed", LUMA_RED);
        active.setUniforms("LumaGreen", LUMA_GREEN);
        active.setUniforms("LumaBlue", LUMA_BLUE);
        active.setUniforms("SpreadFactor", SPREAD);
        active.setUniforms("Brightness", BRIGHTNESS);
        // Same state vanilla sets before its own post pass. / 与原版后处理前设置的状态一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            active.render(frameDuration);
        } finally {
            client.getFramebuffer().beginWrite(false);
        }
        // Outlines were greyed with the world; composite the resolved outline framebuffer again so instinct colours
        // stay exact (D10, Black Raven / Seeker precedent).
        // 描边随世界一起被去色；重新合成已解析的描边帧缓冲，使本能颜色保持原样（D10，参照黑羽鸦/Seeker）。
        client.worldRenderer.drawEntityOutlinesFramebuffer();
    }

    @Nullable
    private static PostEffectProcessor ensureProcessor(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (failed || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            release();
            return null;
        }
        if (processor != null && processorTarget != framebuffer) {
            release();
        }
        if (processor != null) {
            if (processorWidth != framebuffer.textureWidth || processorHeight != framebuffer.textureHeight) {
                // Window resize: rebuild the intermediate targets in place. / 窗口尺寸变化：原地重建中间目标。
                processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
                processorWidth = framebuffer.textureWidth;
                processorHeight = framebuffer.textureHeight;
            }
            return processor;
        }
        try {
            processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), framebuffer,
                    SHADER);
            processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
            processorTarget = framebuffer;
            processorWidth = framebuffer.textureWidth;
            processorHeight = framebuffer.textureHeight;
            return processor;
        } catch (IOException | RuntimeException exception) {
            // JSON or GL compile errors from shader/resource packs: keep the view usable (HUD fallback) until reset.
            // 光影包或资源包导致的 JSON / 编译错误：在重置前保持画面可用（HUD 回退）。
            SparkWitch.LOGGER.warn("Unable to load the Riftwalker grayscale filter {}", SHADER, exception);
            release();
            failed = true;
            return null;
        }
    }

    /**
     * Wathe's public Iris facade; a broken Iris API is treated as "shader pack in use" (fail closed to the fallback).
     * Wathe 的公共 Iris 门面；Iris API 异常时视为「正在使用光影包」（失败时关闭到回退）。
     */
    private static boolean shaderPackInUse() {
        if (irisCheckBroken) {
            return true;
        }
        try {
            return IrisHelper.isIrisShaderPackInUse();
        } catch (LinkageError | RuntimeException exception) {
            irisCheckBroken = true;
            SparkWitch.LOGGER.warn("Unable to query Iris; the Riftwalker grey view uses its HUD fallback", exception);
            return true;
        }
    }
}
