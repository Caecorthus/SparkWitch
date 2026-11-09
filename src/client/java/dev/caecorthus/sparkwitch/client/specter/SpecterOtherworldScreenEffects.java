package dev.caecorthus.sparkwitch.client.specter;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenPerceptionScreenEffects;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.blind.render.BlindShaderPackCheck;
import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.io.IOException;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Otherworld screen filter: every living non-spectator player's saturation drops 10 points per second and contrast
 * rises 5 points per second for the first five seconds, then holds; screen edges turn red. After the window closes the
 * effect fades back over five seconds. Death, disconnection or a stopped round restores the screen immediately. Players
 * with a known shader/filter (Wraith/Vendetta/Perception, Blind, Rift Gate, Seeker camera, scope lens, Iris shader pack,
 * vanilla post effects) are never touched by this pass; if their effect ends while the Otherworld is still open, this
 * one resumes at the current world level.
 * 里世界画面滤镜：所有存活的非旁观玩家前 5 秒每秒饱和度 -10、对比度 +5，然后保持；屏幕边缘泛红。窗口结束后 5 秒
 * 渐隐恢复；死亡、断线或回合停止立即恢复。已知滤镜（冤魂/复仇/感知、盲人、裂隙门、搜寻者相机、瞄准镜、Iris 光影、
 * 原版后处理）激活时本 pass 完全不介入；若其效果在里世界仍开启时结束，本效果按当前世界进度恢复。
 */
public final class SpecterOtherworldScreenEffects {
    private static final Identifier SHADER = SparkWitch.id("shaders/post/otherworld.json");
    private static final float RAMP_SECONDS = 5.0F;
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
    private static float fadeSaturation = 1.0F;
    private static float fadeContrast = 1.0F;
    private static float fadeVignette;
    /** Cached SparkTraits {@code hasActiveTrait} query; null when absent. / 缓存的 SparkTraits 天赋查询。 */
    private static Method depressionQuery;
    private static boolean depressionResolved;

    private SpecterOtherworldScreenEffects() {
    }

    public static void render(ClientPlayerEntity player, float frameDurationTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            close();
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        if (!game.isRunning() || !GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()) {
            reset();
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

        if (!hadEffect) {
            fadingOut = false;
            return;
        }
        if (!fadingOut) {
            fadingOut = true;
            fadeRemainingTicks = FADE_TICKS;
            fadeSaturation = Math.max(0.0F, 1.0F - SATURATION_STEP * RAMP_SECONDS);
            fadeContrast = 1.0F + CONTRAST_STEP * RAMP_SECONDS;
            fadeVignette = VIGNETTE_MAX;
        }
        fadeRemainingTicks -= frameDurationTicks;
        if (fadeRemainingTicks <= 0.0F) {
            reset();
            return;
        }
        float k = fadeRemainingTicks / FADE_TICKS;
        float saturation = 1.0F - (1.0F - fadeSaturation) * k;
        float contrast = 1.0F + (fadeContrast - 1.0F) * k;
        float vignette = fadeVignette * k;
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
        // Keep entity outlines coloured after the desaturated world pass, matching the other SparkWitch filters.
        // 去饱和世界后重新合成实体描边，保持颜色，与其他 SparkWitch 滤镜一致。
        client.worldRenderer.drawEntityOutlinesFramebuffer();
    }

    /** Any known screen shader/filter is active: leave the frame to it. / 任一已知画面滤镜激活：本帧交给它。 */
    private static boolean anotherShaderActive(MinecraftClient client, ClientPlayerEntity player) {
        return client.gameRenderer.getPostProcessor() != null
                || BlindView.isActive(client)
                || BlindShaderPackCheck.isShaderPackInUse()
                || RiftSessionClient.isActive()
                || SeekerRemoteViewClient.isActive()
                || ScopeClient.isRenderingLens()
                || BlackRavenPerceptionScreenEffects.isActiveFor(player)
                || hasDepression(player);
    }

    /** SparkTraits Depression owns the screen look; it ignores the Otherworld filter entirely. / 抑郁天赋直接无视里世界滤镜。 */
    private static boolean hasDepression(ClientPlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("sparktraits")) {
            return false;
        }
        if (!depressionResolved) {
            depressionResolved = true;
            try {
                Class<?> api = Class.forName(
                        "dev.caecorthus.sparktraits.api.SparkTraitsApi",
                        false,
                        SpecterOtherworldScreenEffects.class.getClassLoader()
                );
                depressionQuery = api.getMethod("hasActiveTrait", PlayerEntity.class, Identifier.class);
            } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
                depressionQuery = null;
            }
        }
        if (depressionQuery == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(depressionQuery.invoke(
                    null,
                    player,
                    Identifier.of("sparktraits", "depression")
            ));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /** Drops the fade state but keeps the loaded pipeline for the next activation. / 清空渐变状态，保留已加载管线。 */
    public static void reset() {
        hadEffect = false;
        fadingOut = false;
        fadeRemainingTicks = 0.0F;
    }

    /** Disconnect/world change: release the GL pipeline too. / 断线/换世界：同时释放 GL 管线。 */
    public static void close() {
        reset();
        closeProcessor();
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
