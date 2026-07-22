package dev.caecorthus.sparkwitch.client.blackraven;

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
 * Owns the single SparkWitch desaturation processor; Wraith vision takes priority over Perception.
 * 统一管理 SparkWitch 去饱和处理器；冤魂视野优先于感知效果。
 */
public final class BlackRavenPerceptionScreenEffects {
    private static final Identifier SHADER = SparkWitch.id("shaders/post/perception.json");
    private static final float DESATURATE_FACTOR = 0.50f;
    private static PostEffectProcessor processor;
    private static int processorWidth = -1;
    private static int processorHeight = -1;

    private BlackRavenPerceptionScreenEffects() {
    }

    public static void render(ClientPlayerEntity player, float delta) {
        float desaturation = VendettaClientPresentation.hasActiveOwnerState(player)
                ? VendettaClientPresentation.desaturation(player)
                : WraithVisionRules.desaturation(
                        WraithClientState.isActive(player),
                        WraithClientState.isRestricted(player)
                );
        if (desaturation <= 0.0F && BlackRavenClientState.isPerceptionActive(player)) {
            desaturation = DESATURATE_FACTOR;
        }
        if (desaturation <= 0.0F) {
            closeProcessor();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PostEffectProcessor activeProcessor = ensureProcessor(client);
        if (activeProcessor == null) {
            return;
        }
        activeProcessor.setUniforms("DesaturateFactor", desaturation);
        activeProcessor.render(delta);
        client.getFramebuffer().beginWrite(false);
        // Entity outlines were part of the desaturated world image. Composite the resolved
        // outline framebuffer again afterwards so Vendetta's exact-pair red remains true red.
        // This deliberately reuses Wathe's final outline resolution, preserving Guardian and
        // Black Raven priority without treating arbitrary red world pixels as a shader key.
        client.worldRenderer.drawEntityOutlinesFramebuffer();
    }

    public static void close() {
        closeProcessor();
    }

    private static PostEffectProcessor ensureProcessor(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (processor != null && (processorWidth != framebuffer.textureWidth
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
            return processor;
        } catch (IOException | JsonSyntaxException exception) {
            SparkWitch.LOGGER.warn("Unable to load Black Raven Perception shader", exception);
            closeProcessor();
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
    }
}
