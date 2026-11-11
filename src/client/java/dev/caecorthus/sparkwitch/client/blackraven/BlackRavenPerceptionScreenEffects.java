package dev.caecorthus.sparkwitch.client.blackraven;

import net.minecraft.entity.player.PlayerEntity;
import com.google.gson.JsonSyntaxException;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.render.WraithVisionRules;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;

/**
 * Owns the single SparkWitch desaturation processor; escape uses the strongest active effect.
 * Without escape, Wraith vision retains its original priority over Perception.
 * 统一管理 SparkWitch 去饱和处理器；脱险时取最大灰阶，平时保留冤魂优先于感知的原规则。
 */
public final class BlackRavenPerceptionScreenEffects {
    private static final Identifier SHADER = SparkWitch.id("shaders/post/perception.json");
    private static final float PERCEPTION_DESATURATION = 1.0f;
    private static final float PERCEPTION_LUMINANCE = 0.85f;
    private static final float LEGACY_LUMA_RED = 0.299f;
    private static final float LEGACY_LUMA_GREEN = 0.587f;
    private static final float LEGACY_LUMA_BLUE = 0.114f;
    private static final float PERCEPTION_LUMA_RED = 0.2126f;
    private static final float PERCEPTION_LUMA_GREEN = 0.7152f;
    private static final float PERCEPTION_LUMA_BLUE = 0.0722f;
    private static PostEffectProcessor processor;
    private static Framebuffer processorTarget;
    private static boolean loadFailed;
    private static int processorWidth = -1;
    private static int processorHeight = -1;

    private BlackRavenPerceptionScreenEffects() {
    }

    public static void render(ClientPlayerEntity player, float delta) {
        // Off a confirmed SparkWitch server no Vendetta, Wraith or Perception state can be active (each gate asks for
        // the confirmation first), so with no processor open both branches below are no-ops: skip them. A processor
        // left open by a Last Escape pass still takes the full path, which closes it once the escape ends.
        // 未确认 SparkWitch 服务端时，复仇者、冤魂与感知状态都不可能生效（各门禁都先检查确认状态），因此没有已打开的处理器时
        // 下面两个分支都不会产生任何效果，直接跳过。脱险后处理遗留的处理器仍走完整流程，脱险结束后由其关闭。
        if (processor == null && !SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        // Traits renders the combined pass through our public facade. This gate is independent
        // of which GameRenderer mixin runs first; querying parameters never renders recursively.
        if (LastEscapeTraitsVisionBridge.composition(player)[0] >= 0.5f) {
            return;
        }
        renderComposition(player, delta, null);
    }

    public static boolean renderLastEscape(PlayerEntity player, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(player instanceof ClientPlayerEntity localPlayer) || player != client.player || client.world == null) {
            return false;
        }
        float[] escape = LastEscapeTraitsVisionBridge.composition(player);
        return escape[0] >= 0.5f && renderComposition(localPlayer, delta, escape);
    }

    /**
     * The single vision decision shared by {@link #renderComposition} and {@link #isActiveFor}: the composed
     * Vendetta / Wraith / Perception look, merged with the Last Escape parameters when {@code escape} is non-null, or
     * null when that look is off. Pure: no GL work and no processor changes.
     * {@link #renderComposition} 与 {@link #isActiveFor} 共用的唯一效果判定：合成后的复仇者/冤魂/感知效果（{@code escape}
     * 非空时再与脱险参数合成），效果关闭时为 null。纯查询：不做 GL 操作，也不改动处理器。
     */
    private static WraithVisionRules.Vision resolveVision(ClientPlayerEntity player, float[] escape) {
        float desaturation = VendettaClientPresentation.hasActiveOwnerState(player)
                ? VendettaClientPresentation.desaturation(player)
                : WraithVisionRules.desaturation(
                        WraithClientState.isActive(player),
                        WraithClientState.isRestricted(player)
                );
        float luminanceScale = 1.0F;
        float lumaRed = LEGACY_LUMA_RED;
        float lumaGreen = LEGACY_LUMA_GREEN;
        float lumaBlue = LEGACY_LUMA_BLUE;
        if (WraithVisionRules.usePerception(desaturation, BlackRavenClientState.isPerceptionActive(player), escape != null)) {
            desaturation = PERCEPTION_DESATURATION;
            luminanceScale = PERCEPTION_LUMINANCE;
            lumaRed = PERCEPTION_LUMA_RED;
            lumaGreen = PERCEPTION_LUMA_GREEN;
            lumaBlue = PERCEPTION_LUMA_BLUE;
        }
        // Without an escape composeLastEscape returns the existing vision unchanged, so an off effect is known here,
        // before building it. / 没有脱险时 composeLastEscape 原样返回现有效果，因此可在构建前得知效果已关闭。
        if (escape == null && desaturation <= 0.0F) {
            return null;
        }
        WraithVisionRules.Vision vision = WraithVisionRules.composeLastEscape(
                new WraithVisionRules.Vision(desaturation, 0.0f, 1.0f,
                        luminanceScale, lumaRed, lumaGreen, lumaBlue), escape);
        desaturation = vision.desaturation();
        if (desaturation <= 0.0F) {
            return null;
        }
        return vision;
    }

    private static boolean renderComposition(ClientPlayerEntity player, float delta, float[] escape) {
        WraithVisionRules.Vision vision = resolveVision(player, escape);
        if (vision == null) {
            closeProcessor();
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PostEffectProcessor activeProcessor = ensureProcessor(client);
        if (activeProcessor == null) {
            return false;
        }
        float desaturation = vision.desaturation();
        float luminanceScale = vision.luminance();
        float lumaRed = vision.red();
        float lumaGreen = vision.green();
        float lumaBlue = vision.blue();
        activeProcessor.setUniforms("DesaturateFactor", desaturation);
        activeProcessor.setUniforms("LuminanceScale", luminanceScale);
        activeProcessor.setUniforms("LumaRed", lumaRed);
        activeProcessor.setUniforms("LumaGreen", lumaGreen);
        activeProcessor.setUniforms("LumaBlue", lumaBlue);
        activeProcessor.setUniforms("SpreadFactor", vision.spread());
        activeProcessor.setUniforms("Brightness", vision.brightness());
        try {
            activeProcessor.render(delta);
        } finally {
            client.getFramebuffer().beginWrite(false);
        }
        // Entity outlines were part of the desaturated world image. Composite the resolved
        // outline framebuffer again afterwards so Vendetta's exact-pair red remains true red.
        // This deliberately reuses Wathe's final outline resolution, preserving Guardian and
        // Black Raven priority without treating arbitrary red world pixels as a shader key.
        client.worldRenderer.drawEntityOutlinesFramebuffer();
        return true;
    }

    /**
     * Read-only seam for other client filters (today the Specter's Otherworld filter): whether a Wraith / Vendetta /
     * Perception / Last Escape grayscale is on screen this frame, so they can yield instead of stacking. It follows the
     * same gates as the render paths: a Last Escape frame always shows a grayscale (this compositor's pass through
     * {@link #renderLastEscape}, or SparkTraits' own fallback when that returns false); otherwise the plain path's
     * unconfirmed-server skip, a shader that failed to load, and {@link #resolveVision} decide. Never renders.
     * 供其他客户端滤镜（目前是幽魂的里世界滤镜）使用的只读接缝：本帧屏幕上是否有冤魂/复仇/感知/脱险灰阶，以便让路而不是
     * 叠加。它与渲染路径使用相同的门槛：脱险帧总会显示灰阶（本合成器经 {@link #renderLastEscape} 绘制，返回 false 时由
     * SparkTraits 自行回退绘制）；否则由普通路径的未确认服务端跳过、着色器加载失败与 {@link #resolveVision} 决定。从不渲染。
     */
    public static boolean isActiveFor(ClientPlayerEntity player) {
        if (LastEscapeTraitsVisionBridge.composition(player)[0] >= 0.5f) {
            return true;
        }
        if (processor == null && !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        return !loadFailed && resolveVision(player, null) != null;
    }

    public static void close() {
        closeProcessor();
        loadFailed = false;
        LastEscapeTraitsVisionBridge.forgetProtocol();
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
            SparkWitch.LOGGER.warn("Unable to load Black Raven Perception shader", exception);
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
