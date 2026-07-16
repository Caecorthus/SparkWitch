package dev.caecorthus.sparkwitch.client.blackraven;

import com.google.gson.JsonSyntaxException;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;

/** Fixed 50 percent desaturation during the server-synced Perception window. / 服务端同步感知窗口中的固定 50% 去饱和后处理。 */
public final class BlackRavenPerceptionScreenEffects {
    private static final Identifier SHADER = SparkWitch.id("shaders/post/perception.json");
    private static final float DESATURATE_FACTOR = 0.50f;
    private static PostEffectProcessor processor;
    private static int processorWidth = -1;
    private static int processorHeight = -1;

    private BlackRavenPerceptionScreenEffects() {
    }

    public static void render(ClientPlayerEntity player, float delta) {
        if (!BlackRavenClientState.isPerceptionActive(player)) {
            closeProcessor();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PostEffectProcessor activeProcessor = ensureProcessor(client);
        if (activeProcessor == null) {
            return;
        }
        activeProcessor.setUniforms("DesaturateFactor", DESATURATE_FACTOR);
        activeProcessor.render(delta);
        client.getFramebuffer().beginWrite(false);
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
