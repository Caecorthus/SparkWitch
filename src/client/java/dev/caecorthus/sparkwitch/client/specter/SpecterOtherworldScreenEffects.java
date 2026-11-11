package dev.caecorthus.sparkwitch.client.specter;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenPerceptionScreenEffects;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.blind.render.BlindShaderPackCheck;
import dev.caecorthus.sparkwitch.client.compat.SparkTraitsDepressionVisionBridge;
import dev.caecorthus.sparkwitch.client.render.entity.SecondaryWorldPass;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.io.IOException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Otherworld screen filter, the "Yin realm" look (owner pick 2026-11-11, replacing the saturation/contrast ramp with a
 * red vignette): for every living non-spectator player the world turns ghost-fire teal with only vivid reds kept,
 * distance sinks into teal-black fog, and the screen edge ghosts and breathes (scaled by vanilla's Distortion Effects
 * option). The switch is a wave that sweeps outward through depth in the first ~1.3 s of the window, so the filter
 * needs the world depth: vanilla clears the main depth before drawing the hand (and Fabulous! overwrites it even
 * earlier), so {@link #captureDepth} copies it during the world pass into the chain's {@code otherworld_depth} target;
 * a frame without that copy draws the look with no wave and no fog. When the window closes the filter fades back to normal over five seconds
 * ({@link #FADE_TICKS}); death, spectating, a stopped round, a disconnect or a new connection drops it at once.
 * Players with a known shader/filter (Wraith/Vendetta/Perception/Last Escape, SparkTraits Depression grayscale, Blind,
 * Rift Gate, Seeker camera, any active scope, Iris shader pack, vanilla post effects) are never touched by this pass;
 * if their effect ends while the Otherworld is still open, this one resumes at the current world progress. The GL
 * pipeline lives only while the filter is on screen: it is released when the fade ends or the filter drops, and on
 * disconnect, login, client stop and resource reload (which also clears a load failure).
 * 里世界画面滤镜「阴间」（所有者 2026-11-11 选定，取代原先的饱和度/对比度渐变与红色暗角）：所有存活的非旁观玩家看到世界
 * 转为鬼火青绿、只保留鲜红，远处沉入青黑雾气，屏幕边缘重影并缓慢呼吸（按原版“扭曲效果”选项缩放）。切换由窗口最初约
 * 1.3 秒内沿纵深向外扫过的波纹完成，因此滤镜需要世界深度：原版在画手部前会清空主深度（Fabulous! 画质下更早就被覆盖），
 * 所以 {@link #captureDepth} 在世界 pass 期间把它拷贝到管线的 {@code otherworld_depth} 目标；没有该拷贝的帧只画色调，
 * 没有波纹和雾。
 * 窗口结束后滤镜在 5 秒内（{@link #FADE_TICKS}）渐隐恢复；死亡、旁观、回合停止、断线或新连接时立即撤下。已知滤镜
 * （冤魂/复仇/感知/脱险、SparkTraits 抑郁灰阶、盲人、裂隙门、搜寻者相机、任何开镜状态、Iris 光影、原版后处理）激活时
 * 本 pass 完全不介入；若其效果在里世界仍开启时结束，本效果按当前世界进度恢复。GL 管线只在滤镜显示期间存在：渐隐结束或
 * 滤镜撤下时释放，断线、登录、客户端关闭与资源重载时也会释放（资源重载同时清除加载失败标记）。
 */
public final class SpecterOtherworldScreenEffects {
    private static final Identifier SHADER = SparkWitch.id("shaders/post/otherworld.json");
    private static final String DEPTH_TARGET = "otherworld_depth";
    /** Window-end fade back to normal: five seconds. / 窗口结束后的渐隐恢复：5 秒。 */
    private static final float FADE_TICKS = 100.0F;
    /** "Opened long ago" for the shader, so a closing fade never replays the opening wave. / 传给着色器的“早已开启”，使结束渐隐不会重播开场波纹。 */
    private static final float SETTLED_OPEN_SECONDS = 1000.0F;
    /** Vanilla's near plane ({@code GameRenderer#getBasicProjectionMatrix}). / 原版近裁剪面。 */
    private static final float NEAR_PLANE = 0.05F;
    /** The shader's free-running clock wraps here. / 着色器自由时钟的循环周期。 */
    private static final long LOOK_TIME_WRAP_MS = 600_000L;

    private static PostEffectProcessor processor;
    private static Framebuffer processorTarget;
    private static boolean loadFailed;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean hadEffect;
    private static boolean fadingOut;
    private static float fadeRemainingTicks;
    /** {@link #captureDepth} copied this frame's world depth. / {@link #captureDepth} 已拷贝本帧的世界深度。 */
    private static boolean depthCaptured;
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
        WorldRenderEvents.START.register(context -> {
            if (!SecondaryWorldPass.isActive()) {
                depthCaptured = false;
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> captureDepth(false));
        WorldRenderEvents.LAST.register(context -> captureDepth(true));
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

    /**
     * {@code WorldRenderEvents.AFTER_TRANSLUCENT}, and {@code LAST} as a fallback for a frame where the first did not
     * copy; render thread. On a frame that will draw the filter, copies the world depth (particles included, so a near
     * particle is not fogged like the wall behind it) into {@code otherworld_depth} once and restores the framebuffer
     * binding the caller expects. Never in a {@link SecondaryWorldPass}.
     * {@code WorldRenderEvents.AFTER_TRANSLUCENT}，并以 {@code LAST} 为前者未拷贝的帧兜底；渲染线程。在将要绘制滤镜的帧，
     * 把世界深度（含粒子，使近处粒子不会像它背后的墙一样被雾化）拷贝到 {@code otherworld_depth} 一次，并恢复调用方所需的
     * 帧缓冲绑定。{@link SecondaryWorldPass} 中从不执行。
     */
    private static void captureDepth(boolean lastChance) {
        if (depthCaptured) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || SecondaryWorldPass.isActive() || !showsFilter(client, player)) {
            return;
        }
        int bound = GlStateManager.getBoundFramebuffer();
        Framebuffer source = depthSource(client, lastChance, bound);
        if (source == null) {
            return;
        }
        PostEffectProcessor active = ensureProcessor(client);
        Framebuffer depth = active == null ? null : active.getSecondaryTarget(DEPTH_TARGET);
        if (depth == null) {
            return;
        }
        try {
            depth.copyDepthFrom(source);
            depthCaptured = true;
        } finally {
            // copyDepthFrom leaves FBO 0 bound; the world pass keeps drawing into whichever target was bound.
            // copyDepthFrom 结束时绑定的是 FBO 0；世界 pass 还要继续画进原先绑定的目标。
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, bound);
        }
    }

    /**
     * Where this frame's world depth lives. Fabulous! keeps particles in their own target, whose depth started as a
     * copy of main's opaque depth, and its transparency composite then overwrites the main depth with a constant
     * before {@code LAST} (live probe 2026-11-11), so that mode is copied at {@code AFTER_TRANSLUCENT} only. Every
     * other mode draws everything into main; main is used only while it is the bound target, so a foreign second world
     * render never supplies the depth.
     * 本帧世界深度所在位置。Fabulous! 把粒子画进独立目标（其深度起初是主目标不透明深度的拷贝），随后的半透明合成会在
     * {@code LAST} 之前把主深度覆盖为常量（2026-11-11 实机探针），所以该画质只在 {@code AFTER_TRANSLUCENT} 拷贝。其他画质
     * 全部画进主目标；只有主目标是当前绑定目标时才使用它，因此外来的第二次世界渲染不会提供深度。
     */
    @Nullable
    private static Framebuffer depthSource(MinecraftClient client, boolean lastChance, int boundFramebuffer) {
        Framebuffer particles = client.worldRenderer.getParticlesFramebuffer();
        if (particles != null) {
            return lastChance ? null : particles;
        }
        Framebuffer main = client.getFramebuffer();
        return boundFramebuffer == main.fbo ? main : null;
    }

    /**
     * Read-only: this frame's {@link #render} would draw the filter (window open, or a fade this player is still in).
     * 只读：本帧的 {@link #render} 是否会绘制滤镜（窗口开启，或该玩家仍在渐隐中）。
     */
    private static boolean showsFilter(MinecraftClient client, ClientPlayerEntity player) {
        if (!GameWorldComponent.KEY.get(client.world).isRunning() || !GameFunctions.isPlayerPlayingAndAlive(player)
                || player.isSpectator()) {
            return false;
        }
        boolean windowOpen = SpecterOtherworldComponent.KEY.get(client.world).isActive();
        return (windowOpen || hadEffect) && !anotherShaderActive(client, player);
    }

    /** Render thread, from {@code SpecterOtherworldGameRendererMixin}. / 渲染线程，由该 mixin 调用。 */
    public static void render(ClientPlayerEntity player, float frameDurationTicks) {
        boolean hasDepth = depthCaptured;
        depthCaptured = false;
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
            float openSeconds = (otherworld.getElapsedTicks()
                    + client.getRenderTickCounter().getTickDelta(false)) / 20.0F;
            if (anotherShaderActive(client, player)) {
                return;
            }
            hadEffect = true;
            renderFilter(client, 1.0F, openSeconds, hasDepth, frameDurationTicks);
            return;
        }

        // Window over. Only a player who actually saw the filter fades out of it; the fade always starts from the full
        // look, because a window only closes on its 30 s timer (a stopped round drops the filter above).
        // 窗口已结束。只有真正看到过滤镜的玩家才渐隐；渐隐总是从完整效果开始，因为窗口只会在 30 秒计时结束时关闭
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
        if (anotherShaderActive(client, player)) {
            return;
        }
        renderFilter(client, fadeRemainingTicks / FADE_TICKS, SETTLED_OPEN_SECONDS, hasDepth, frameDurationTicks);
    }

    private static void renderFilter(MinecraftClient client, float intensity, float openSeconds, boolean hasDepth,
                                     float frameDurationTicks) {
        PostEffectProcessor active = ensureProcessor(client);
        if (active == null) {
            return;
        }
        active.setUniforms("Intensity", intensity);
        active.setUniforms("OpenSeconds", openSeconds);
        active.setUniforms("LookTime", (Util.getMeasuringTimeMs() % LOOK_TIME_WRAP_MS) / 1000.0F);
        active.setUniforms("Near", NEAR_PLANE);
        active.setUniforms("Far", client.gameRenderer.getFarPlaneDistance());
        active.setUniforms("HasDepth", hasDepth ? 1.0F : 0.0F);
        active.setUniforms("Distortion", client.options.getDistortionEffectScale().getValue().floatValue());
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            active.render(frameDurationTicks);
        } finally {
            client.getFramebuffer().beginWrite(false);
        }
        // Keep entity outlines coloured after the graded world pass, matching the other SparkWitch filters. Safe only
        // because every scope (whose PiP lens pass re-fills the outline buffer) makes this pass yield.
        // 世界调色后重新合成实体描边，保持颜色，与其他 SparkWitch 滤镜一致。只有在任何开镜（其画中画镜内渲染会重写描边
        // 缓冲）都会让本 pass 让路的前提下才安全。
        client.worldRenderer.drawEntityOutlinesFramebuffer();
    }

    /**
     * Any known screen shader/filter is on screen this frame: leave the frame to it. The scope check covers every
     * scope profile, lens or reflex: {@code ScopeGameRendererMixin} composites the lens at this same hook before this
     * pass, and the PiP lens pass leaves its own outlines in the outline buffer, so filtering over it would regrade
     * the lens and redraw lens-space outlines across the full screen.
     * 本帧已有任一已知画面滤镜：本帧交给它。开镜检查覆盖所有开镜配置（有镜片或反射式）：{@code ScopeGameRendererMixin}
     * 在同一钩子、本 pass 之前合成镜片，画中画镜内渲染会把自己的描边留在描边缓冲中，若在其上叠加滤镜，会把镜片重新调色并把
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
        depthCaptured = false;
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
