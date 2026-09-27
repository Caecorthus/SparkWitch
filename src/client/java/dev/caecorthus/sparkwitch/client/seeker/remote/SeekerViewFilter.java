package dev.caecorthus.sparkwitch.client.seeker.remote;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Client-only screen filter for the Seeker's remote view: a PRIVATE {@link PostEffectProcessor} (Black Raven pattern),
 * never {@code GameRenderer.postProcessor}, because {@code MinecraftClient#setCameraEntity} closes that one on every
 * camera switch. Car = warm/green night-vision tint, camera = cold grey CCTV; both add scanlines, vignette and grain.
 * It runs from {@code SeekerRemoteGameRendererMixin} after world rendering, follows framebuffer resizes, is closed on
 * exit, disconnect ({@link #reset()}) and resource reload, and degrades to "no filter" (the HUD overlay still works)
 * when the shader cannot load, e.g. under an Iris shader pack.
 * 搜寻者遥控视角的纯客户端屏幕滤镜：私有的 {@link PostEffectProcessor}（黑鸦模式），绝不使用
 * {@code GameRenderer.postProcessor}，因为 {@code MinecraftClient#setCameraEntity} 每次切换相机都会关闭它。
 * 小车为暖绿夜视色调，摄像头为冷灰监控色调；两者都带扫描线、暗角与噪点。由 {@code SeekerRemoteGameRendererMixin}
 * 在世界渲染后调用，跟随帧缓冲尺寸变化，在退出、断线（{@link #reset()}）与资源重载时关闭；着色器无法加载时
 * （例如 Iris 光影包）退化为无滤镜（HUD 叠加层照常工作）。
 */
public final class SeekerViewFilter {
    private static final Identifier RELOAD_LISTENER_ID = SparkWitch.id("seeker_view_filter");
    private static final Set<SeekerSessionMode> FAILED = EnumSet.noneOf(SeekerSessionMode.class);
    private static boolean registered;
    @Nullable
    private static PostEffectProcessor processor;
    @Nullable
    private static Framebuffer processorTarget;
    private static SeekerSessionMode processorMode = SeekerSessionMode.NONE;
    private static int processorWidth = -1;
    private static int processorHeight = -1;

    private SeekerViewFilter() {
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

    /** Releases the processor on disconnect or resource reload and allows a fresh load. / 断线或资源重载时释放处理器，并允许重新加载。 */
    public static void reset() {
        release();
        FAILED.clear();
    }

    /** Closes the processor (no-op when none is open); called on every inactive frame. / 关闭处理器（未打开时无操作）；非激活帧都会调用。 */
    public static void release() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorTarget = null;
        processorMode = SeekerSessionMode.NONE;
        processorWidth = -1;
        processorHeight = -1;
    }

    /**
     * Renders the filter over the main framebuffer, render thread only, right before the GUI pass.
     * 在主帧缓冲上渲染滤镜，仅限渲染线程，紧接在 GUI 渲染之前。
     *
     * @param frameDuration frame duration in ticks, which animates the grain / 以 tick 计的帧时长，用于驱动噪点动画
     */
    public static void render(float frameDuration) {
        MinecraftClient client = MinecraftClient.getInstance();
        SeekerSessionMode mode = SeekerRemoteViewClient.mode();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || SeekerCctvRules.postEffect(mode) == null) {
            release();
            return;
        }
        PostEffectProcessor active = ensureProcessor(client, mode);
        if (active == null) {
            return;
        }
        boolean signalLost = SeekerCctvOverlay.isSignalLost(player);
        active.setUniforms("Interference", interference(player, mode));
        active.setUniforms("SignalLost", signalLost ? 1.0F : 0.0F);
        // Same state vanilla sets before its own post pass. / 与原版后处理前设置的状态一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            active.render(frameDuration);
        } finally {
            client.getFramebuffer().beginWrite(false);
        }
        if (!signalLost) {
            // Outlines (the owner's mark) were tinted with the world; composite them again so their colours stay
            // exact. Skipped while the signal is lost, so nothing leaks through the static.
            // 描边（拥有者的标记）随世界一起被染色；重新合成一次以保持原色。信号丢失时跳过，避免透过噪声泄露信息。
            client.worldRenderer.drawEntityOutlinesFramebuffer();
        }
    }

    private static float interference(ClientPlayerEntity player, SeekerSessionMode mode) {
        Entity focus = SeekerRemoteViewClient.focus();
        double distance = focus == null ? 0.0 : SeekerCctvOverlay.horizontalDistance(player, focus);
        return SeekerCctvRules.interference(mode, SeekerClientState.carBattery(), distance,
                SeekerClientState.effectiveRadius());
    }

    @Nullable
    private static PostEffectProcessor ensureProcessor(MinecraftClient client, SeekerSessionMode mode) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (FAILED.contains(mode) || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            release();
            return null;
        }
        if (processor != null && (processorMode != mode || processorTarget != framebuffer)) {
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
        Identifier effect = SeekerCctvRules.postEffect(mode);
        if (effect == null) {
            return null;
        }
        try {
            processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), framebuffer,
                    effect);
            processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
            processorTarget = framebuffer;
            processorMode = mode;
            processorWidth = framebuffer.textureWidth;
            processorHeight = framebuffer.textureHeight;
            return processor;
        } catch (IOException | RuntimeException exception) {
            // Includes JsonSyntaxException and GL compile errors from shader packs or broken resource packs: keep the
            // view usable without the filter until reset.
            // 包括 JsonSyntaxException 以及光影包或损坏资源包导致的编译错误：在重置前保持无滤镜的可用画面。
            SparkWitch.LOGGER.warn("Unable to load the Seeker {} view filter {}", mode, effect, exception);
            release();
            FAILED.add(mode);
            return null;
        }
    }
}
