package dev.caecorthus.sparkwitch.client.blind.render;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Client wiring of the Blind's black screen and line-art pipeline: the depth capture at
 * {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}, the fail-closed HUD hint, and {@link BlindEchoView#reset()} on
 * disconnect and resource reload. The end-of-frame pass itself runs from {@code BlindGameRendererMixin}.
 * 盲人黑屏与线稿管线的客户端接线：在 {@code WorldRenderEvents.BEFORE_DEBUG_RENDER} 捕获深度、失败即全黑时的 HUD 提示，
 * 以及断线与资源重载时的 {@link BlindEchoView#reset()}。帧末 pass 本身由 {@code BlindGameRendererMixin} 运行。
 */
public final class BlindRenderWiring {
    public static final String FALLBACK_HINT_KEY = "hud.sparkwitch.blind.view_unavailable";
    private static final Identifier RELOAD_LISTENER_ID = SparkWitch.id("blind_echo_view");
    private static boolean registered;

    private BlindRenderWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BlindEchoView::captureWorld);
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderFallbackHint(context));
        ClientLoginConnectionEvents.INIT.register((handler, client) -> BlindEchoView.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlindEchoView.reset());
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
                        BlindEchoView.reset();
                    }
                });
    }

    private static void renderFallbackHint(DrawContext context) {
        if (!BlindEchoView.isFallbackHintVisible()) {
            return;
        }
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int width = context.getScaledWindowWidth();
        // Wrapped so the hint stays on screen at narrow GUI widths. / 自动换行，窄 GUI 宽度下提示仍在屏幕内。
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(FALLBACK_HINT_KEY),
                Math.max(64, width - 32));
        int y = context.getScaledWindowHeight() / 2 - 24 - (lines.size() - 1) * (textRenderer.fontHeight + 1);
        for (OrderedText line : lines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, BlindRules.COLOR);
            y += textRenderer.fontHeight + 1;
        }
    }
}
