package dev.caecorthus.sparkwitch.client.specter;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenPerceptionScreenEffects;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.blind.render.BlindShaderPackCheck;
import dev.caecorthus.sparkwitch.client.compat.SparkTraitsDepressionVisionBridge;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.io.IOException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Otherworld screen filter: every living non-spectator player's saturation drops 10 points per second and contrast
 * rises 5 points per second for the first five seconds, then holds; screen edges turn red. When the window closes the
 * filter fades back to normal over five seconds ({@link #FADE_TICKS}); death, spectating, a stopped round, a disconnect
 * or a new connection drops it at once. Players with a known shader/filter (Wraith/Vendetta/Perception/Last Escape,
 * SparkTraits Depression grayscale, Blind, Rift Gate, Seeker camera, any active scope, Iris shader pack, vanilla post
 * effects) are never touched by this pass; if their effect ends while the Otherworld is still open, this one resumes at
 * the current world level. The GL pipeline lives only while the filter is on screen: it is released when the fade ends
 * or the filter drops, and on disconnect, login, client stop and resource reload (which also clears a load failure).
 * 里世界画面滤镜：所有存活的非旁观玩家前 5 秒每秒饱和度 -10、对比度 +5，然后保持；屏幕边缘泛红。窗口结束后滤镜在
 * 5 秒内（{@link #FADE_TICKS}）渐隐恢复；死亡、旁观、回合停止、断线或新连接时立即撤下。已知滤镜（冤魂/复仇/感知/脱险、
 * SparkTraits 抑郁灰阶、盲人、裂隙门、搜寻者相机、任何开镜状态、Iris 光影、原版后处理）激活时本 pass 完全不介入；
 * 若其效果在里世界仍开启时结束，本效果按当前世界进度恢复。GL 管线只在滤镜显示期间存在：渐隐结束或滤镜撤下时释放，
 * 断线、登录、客户端关闭与资源重载时也会释放（资源重载同时清除加载失败标记）。
 */
public final class SpecterOtherworldScreenEffects {
    private static final Identifier SHADER = SparkWitch.id("shaders/post/otherworld.json");
    private static final float RAMP_SECONDS = 5.0F;
    /** Window-end fade back to normal: five seconds. / 窗口结束后的渐隐恢复：5 秒。 */
    private static final float FADE_TICKS = 100.0F;
    private static final float SATURATION_STEP = 0.10F;
    private static final float CONTRAST_STEP = 0.05F;
    private static final float VIGNETTE_MAX = 0.55F;

    private static PostEffectProcessor processor;
    private static Framebuffer processorTarget;
    private static boolean loadFailed;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean hadEffect;
    private static boolean fadingOut;
    private static float fadeRemainingTicks;
    private static boolean registered;

    private SpecterOtherworldScreenEffects() {
    }

    /**
     * Lifecycle hooks, mirroring the Black Raven compositor and the scope filters: a new or closed connection, client
     * stop and resource reload all drop the fade state and release the pipeline, so no fade survives into the next
     * server and an F3+T retries a shader that failed to load.
     * 生命周期钩子，与黑羽鸦合成器及开镜滤镜一致：新连接、断开连接、客户端关闭和资源重载都会清除渐变状态并释放管线，
     * 因此渐变不会带到下一个服务器，F3+T 也会重新尝试加载曾失败的着色器。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> close());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return SparkWitch.id("specter_otherworld_filter");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        MinecraftClient.getInstance().execute(SpecterOtherworldScreenEffects::close);
                    }
                });
    }

    /** Render thread, from {@code SpecterOtherworldGameRendererMixin}. / 渲染线程，由该 mixin 调用。 */
    public static void render(ClientPlayerEntity player, float frameDurationTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            close();
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        if (!game.isRunning() || !GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()) {
            stop();
            return;
        }

        SpecterOtherworldComponent otherworld = SpecterOtherworldComponent.KEY.get(client.world);
        if (otherworld.isActive()) {
            fadingOut = false;
            float elapsedTicks = otherworld.getElapsedTicks()
                    + client.getRenderTickCounter().getTickDelta(false);
            float seconds = Math.min(RAMP_SECONDS, elapsedTicks / 20.0F);
            float saturation = Math.max(0.0F, 1.0F - SATURATION_STEP * seconds);
            float contrast = 1.0F + CONTRAST_STEP * seconds;
            float vignette = VIGNETTE_MAX * Math.min(1.0F, seconds / RAMP_SECONDS);
            if (anotherShaderActive(client, player)) {
                return;
            }
            hadEffect = true;
            renderFilter(client, saturation, contrast, vignette, frameDurationTicks);
            return;
        }

        // Window over. Only a player who actually saw the filter fades out of it; the fade always starts from the
        // fully ramped look, because a window only closes on its 30 s timer (a stopped round drops the filter above).
        // 窗口已结束。只有真正看到过滤镜的玩家才渐隐；渐隐总是从完全展开的效果开始，因为窗口只会在 30 秒计时结束时关闭
        // （回合停止的情况已在上方直接撤下滤镜）。
        if (!hadEffect) {
            stop();
            return;
        }
        if (!fadingOut) {
            fadingOut = true;
            fadeRemainingTicks = FADE_TICKS;
        }
        fadeRemainingTicks -= frameDurationTicks;
        if (fadeRemainingTicks <= 0.0F) {
            stop();
            return;
        }
        float k = fadeRemainingTicks / FADE_TICKS;
        float saturation = 1.0F - SATURATION_STEP * RAMP_SECONDS * k;
        float contrast = 1.0F + CONTRAST_STEP * RAMP_SECONDS * k;
        float vignette = VIGNETTE_MAX * k;
        if (anotherShaderActive(client, player)) {
            return;
        }
        renderFilter(client, saturation, contrast, vignette, frameDurationTicks);
    }

    private static void renderFilter(MinecraftClient client, float saturation, float contrast, float vignette,
                                     float frameDurationTicks) {
        PostEffectProcessor active = ensureProcessor(client);
        if (active == null) {
            return;
        }
        active.setUniforms("Saturation", saturation);
        active.setUniforms("Contrast", contrast);
        active.setUniforms("Vignette", vignette);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            active.render(frameDurationTicks);
        } finally {
            client.getFramebuffer().beginWrite(false);
        }
        // Keep entity outlines coloured after the desaturated world pass, matching the other SparkWitch filters. Safe
        // only because every scope (whose PiP lens pass re-fills the outline buffer) makes this pass yield.
        // 去饱和世界后重新合成实体描边，保持颜色，与其他 SparkWitch 滤镜一致。只有在任何开镜（其画中画镜内渲染会重写描边
        // 缓冲）都会让本 pass 让路的前提下才安全。
        client.worldRenderer.drawEntityOutlinesFramebuffer();
    }

    /**
     * Any known screen shader/filter is on screen this frame: leave the frame to it. The scope check covers every
     * scope profile, lens or reflex: {@code ScopeGameRendererMixin} composites the lens at this same hook before this
     * pass, and the PiP lens pass leaves its own outlines in the outline buffer, so filtering over it would desaturate
     * the lens and redraw lens-space outlines across the full screen.
     * 本帧已有任一已知画面滤镜：本帧交给它。开镜检查覆盖所有开镜配置（有镜片或反射式）：{@code ScopeGameRendererMixin}
     * 在同一钩子、本 pass 之前合成镜片，画中画镜内渲染会把自己的描边留在描边缓冲中，若在其上叠加滤镜，会把镜片去饱和并把
     * 镜内空间的描边重画到整个屏幕上。
     */
    private static boolean anotherShaderActive(MinecraftClient client, ClientPlayerEntity player) {
        return client.gameRenderer.getPostProcessor() != null
                || ScopeClient.isScoped()
                || RiftSessionClient.isActive()
                || SeekerRemoteViewClient.isActive()
                || BlindView.isActive(client)
                || BlindShaderPackCheck.isShaderPackInUse()
                || BlackRavenPerceptionScreenEffects.isActiveFor(player)
                || SparkTraitsDepressionVisionBridge.drawsDepressionGrayscale(player);
    }

    /** Drops the fade state and releases the pipeline; the next window loads it again. / 清空渐变状态并释放管线。 */
    private static void stop() {
        hadEffect = false;
        fadingOut = false;
        fadeRemainingTicks = 0.0F;
        closeProcessor();
    }

    /**
     * Disconnect, login, client stop, resource reload: {@link #stop()} plus a fresh load attempt next time (deferred to
     * the render thread when called elsewhere).
     * 断线、登录、客户端关闭、资源重载：执行 {@link #stop()}，并允许下次重新尝试加载（在其他线程调用时推迟到渲染线程）。
     */
    public static void close() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(SpecterOtherworldScreenEffects::close);
            return;
        }
        stop();
        loadFailed = false;
    }

    private static PostEffectProcessor ensureProcessor(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (loadFailed || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            return null;
        }
        if (processor != null && (processorTarget != framebuffer || processorWidth != framebuffer.textureWidth
                || processorHeight != framebuffer.textureHeight)) {
            closeProcessor();
        }
        if (processor != null) {
            return processor;
        }
        try {
            processor = new PostEffectProcessor(
                    client.getTextureManager(),
                    client.getResourceManager(),
                    framebuffer,
                    SHADER
            );
            processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
            processorWidth = framebuffer.textureWidth;
            processorHeight = framebuffer.textureHeight;
            processorTarget = framebuffer;
            return processor;
        } catch (IOException | JsonSyntaxException exception) {
            SparkWitch.LOGGER.warn("Unable to load Specter Otherworld shader", exception);
            closeProcessor();
            loadFailed = true;
            return null;
        }
    }

    private static void closeProcessor() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorWidth = -1;
        processorHeight = -1;
        processorTarget = null;
    }
}
