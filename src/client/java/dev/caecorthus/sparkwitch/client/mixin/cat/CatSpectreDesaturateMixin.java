package dev.caecorthus.sparkwitch.client.mixin.cat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * Drains the colour out of the local view while the cat sits in its three-second spectre window.
 * <p>Uses a real post-effect shader (the same {@link PostEffectProcessor} mechanism the Blind and Seeker views use),
 * not a flat shader colour, so the picture keeps its structure instead of looking like a dark overlay.
 * 猫处于三秒幽魂窗口时，用后处理着色器抽掉本地画面的颜色。
 * 走的是盲人/搜寻者视角同款的 {@link PostEffectProcessor}，而不是平铺的着色器颜色，
 * 因此画面保留层次，不会像盖了一层暗色蒙板。
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class CatSpectreDesaturateMixin {

    /** The grayscale post effect shipped with this mod. 本模组自带的灰阶后处理。 */
    private static final Identifier SPARKWITCH$SPECTRE_EFFECT =
            Identifier.of("sparkwitch", "shaders/post/sparkwitch_cat_spectre.json");

    private static PostEffectProcessor sparkwitch$processor;
    private static boolean sparkwitch$loadFailed;
    /** Size the current processor was built for; PostEffectProcessor keeps its own fields private. */
    /** 当前处理器构建时的尺寸；PostEffectProcessor 把自己的字段设为私有。 */
    private static int sparkwitch$width = -1;
    private static int sparkwitch$height = -1;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void sparkwitch$desaturateSpectre(CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) {
            return;
        }
        // Invisibility is the server-driven spectre flag, so no extra packet is needed.
        // 隐身就是服务端驱动的幽魂标记，因此不需要额外的网络包。
        if (!player.isInvisible() || sparkwitch$loadFailed) {
            return;
        }
        PostEffectProcessor processor = sparkwitch$processor(client);
        if (processor != null) {
            processor.render(1.0F);
        }
    }

    private static PostEffectProcessor sparkwitch$processor(MinecraftClient client) {
        var framebuffer = client.getFramebuffer();
        if (sparkwitch$processor != null) {
            if (sparkwitch$width != framebuffer.textureWidth
                    || sparkwitch$height != framebuffer.textureHeight) {
                // Window resize: rebuild the intermediate targets in place. 窗口尺寸变化：原地重建中间目标。
                sparkwitch$processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
                sparkwitch$width = framebuffer.textureWidth;
                sparkwitch$height = framebuffer.textureHeight;
            }
            return sparkwitch$processor;
        }
        try {
            sparkwitch$processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(),
                    framebuffer, SPARKWITCH$SPECTRE_EFFECT);
            sparkwitch$processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
            sparkwitch$width = framebuffer.textureWidth;
            sparkwitch$height = framebuffer.textureHeight;
            return sparkwitch$processor;
        } catch (IOException | RuntimeException exception) {
            // A broken resource pack must not break the game: fall back to rendering normally.
            // 资源包损坏时不能让游戏崩溃：退回正常渲染。
            sparkwitch$loadFailed = true;
            return null;
        }
    }
}
