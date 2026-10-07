package dev.caecorthus.sparkwitch.client.blackraven;

import net.minecraft.entity.player.PlayerEntity;
import com.google.gson.JsonSyntaxException;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.render.WraithVisionRules;
import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
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

    private static boolean renderComposition(ClientPlayerEntity player, float delta, float[] escape) {
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
        WraithVisionRules.Vision vision = WraithVisionRules.composeLastEscape(
                new WraithVisionRules.Vision(desaturation, 0.0f, 1.0f,
                        luminanceScale, lumaRed, lumaGreen, lumaBlue), escape);
        desaturation = vision.desaturation();
        if (desaturation <= 0.0F) {
            closeProcessor();
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PostEffectProcessor activeProcessor = ensureProcessor(client);
        if (activeProcessor == null) {
            return false;
        }
        luminanceScale = vision.luminance();
        lumaRed = vision.red();
        lumaGreen = vision.green();
        lumaBlue = vision.blue();
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

    public static void close() {
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
