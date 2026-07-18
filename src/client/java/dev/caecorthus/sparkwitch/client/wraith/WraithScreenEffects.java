package dev.caecorthus.sparkwitch.client.wraith;

import com.google.gson.JsonSyntaxException;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;

/** Owns restricted and promoted Wraith desaturation using SparkWitch's grayscale pass. / 使用 SparkWitch 灰阶通道管理受限与升变冤魂的去饱和视野。 */
public final class WraithScreenEffects {
    private static final Identifier SHADER = SparkWitch.id("shaders/post/perception.json");
    private static PostEffectProcessor processor;
    private static int processorWidth = -1;
    private static int processorHeight = -1;

    private WraithScreenEffects() {
    }

    public static void render(ClientPlayerEntity player, float delta) {
        float desaturation = WraithVisionRules.desaturation(
                WraithClientState.isActive(player),
                WraithClientState.isRestricted(player)
        );
        if (desaturation <= 0.0f) {
            close();
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
    }

    public static void close() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorWidth = -1;
        processorHeight = -1;
    }

    private static PostEffectProcessor ensureProcessor(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (processor != null && (processorWidth != framebuffer.textureWidth
                || processorHeight != framebuffer.textureHeight)) {
            close();
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
            SparkWitch.LOGGER.warn("Unable to load Wraith vision shader", exception);
            close();
            return null;
        }
    }
}
