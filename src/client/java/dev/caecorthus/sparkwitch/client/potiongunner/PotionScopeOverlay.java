package dev.caecorthus.sparkwitch.client.potiongunner;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.client.mixin.potiongunner.PotionScopeGameRendererInvoker;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * Scope picture drawn by {@code PotionScopeInGameHudMixin} instead of the crosshair while scoped: the WP5 mask scaled
 * to the shorter screen side, black bars, a thin crosshair whose centre is labelled 0-50 (flat flight), the
 * 60/70/80/90/100-block drop ticks below it, and the loaded shell. Tick placement uses the vertical FOV the world was
 * projected with this frame and the exact shell ballistics at the current pitch ({@link PotionScopeRules#tickOffset});
 * everything is drawn around the exact framebuffer centre. Presentation only; the server decides every shot.
 * 开镜时由 {@code PotionScopeInGameHudMixin} 代替准星绘制的瞄准镜画面：按屏幕短边缩放的 WP5 遮罩、黑边、中心标注
 * 0-50（平飞）的细十字线、其下方的 60/70/80/90/100 格下坠刻度，以及已装填弹种。刻度位置使用本帧世界投影的竖直视场角
 * 与当前俯仰角下的精确弹道（{@link PotionScopeRules#tickOffset}）；全部内容围绕帧缓冲的精确中心绘制。仅负责展示，
 * 每次发射都由服务端决定。
 */
public final class PotionScopeOverlay {
    private static final int LINE = 0xE6F2F2F2;
    private static final int LINE_SHADOW = 0x99000000;
    private static final int BLACK = 0xFF000000;
    private static final int CENTER_GAP = 3;
    private static final int TICK_HALF_WIDTH = 4;
    private static final float LABEL_SCALE = 0.75F;
    private static final int CORNER_INSET = 8;
    /** Ticks stay a little inside the mask's soft edge. / 刻度略微保持在遮罩柔和边缘以内。 */
    private static final double TICK_RADIUS_SHARE = 0.92;

    private PotionScopeOverlay() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden) {
            return;
        }
        Window window = client.getWindow();
        double scale = window.getScaleFactor();
        double centerX = window.getFramebufferWidth() / scale / 2.0;
        double centerY = window.getFramebufferHeight() / scale / 2.0;
        int side = Math.min(context.getScaledWindowWidth(), context.getScaledWindowHeight());
        int half = side / 2;
        int reachX = (int) Math.ceil(centerX) + 1;
        int reachY = (int) Math.ceil(centerY) + 1;
        double clearRadius = side * PotionScopeRules.MASK_CLEAR_RADIUS;
        float tickDelta = tickCounter.getTickDelta(true);

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0.0);

        RenderSystem.enableBlend();
        context.drawTexture(PotionScopeRules.MASK_TEXTURE, -half, -half, 0, 0.0F, 0.0F, side, side, side, side);
        RenderSystem.disableBlend();
        context.fill(-reachX, -reachY, -half, reachY, BLACK);
        context.fill(half, -reachY, reachX, reachY, BLACK);
        context.fill(-half, -reachY, half, -half, BLACK);
        context.fill(-half, half, half, reachY, BLACK);

        int arm = (int) Math.floor(clearRadius);
        line(context, -arm, 0, -CENTER_GAP, 1);
        line(context, CENTER_GAP + 1, 0, arm, 1);
        line(context, 0, -arm, 1, -CENTER_GAP);
        line(context, 0, CENTER_GAP + 1, 1, arm);
        line(context, 0, 0, 1, 1);

        double fov = ((PotionScopeGameRendererInvoker) client.gameRenderer)
                .sparkwitch$getFov(client.gameRenderer.getCamera(), tickDelta, true);
        renderRangeTicks(context, client.textRenderer, player.getPitch(tickDelta), fov, centerY,
                clearRadius * TICK_RADIUS_SHARE);
        renderLoadedShell(context, client.textRenderer, PotionScopeClient.loaded(player.getActiveItem()), half);

        context.getMatrices().pop();
    }

    private static void renderRangeTicks(DrawContext context, TextRenderer text, float pitch, double fov,
                                         double halfHeight, double radius) {
        // Flat flight: the centre is the aim point for the whole 0-50 range; the shell passes only the 0.1-block launch
        // offset below it. / 平飞：整个 0-50 格内准星中心即瞄准点；炮弹只比它低 0.1 格的发射偏移。
        label(context, text, PotionScopeRules.CENTER_LABEL, CENTER_GAP + 3.0,
                -text.fontHeight * LABEL_SCALE - 1.0);
        double labelHeight = text.fontHeight * LABEL_SCALE;
        for (int index = 0; index < PotionScopeRules.RANGE_TICKS.size(); index++) {
            int distance = PotionScopeRules.RANGE_TICKS.get(index);
            double offset = PotionScopeRules.tickOffset(pitch, distance, fov, halfHeight, radius);
            if (Double.isNaN(offset)) {
                continue;
            }
            int y = (int) Math.round(offset);
            line(context, -TICK_HALF_WIDTH, y, TICK_HALF_WIDTH + 1, y + 1);
            String value = Integer.toString(distance);
            double labelX = PotionScopeRules.labelOnRight(index)
                    ? TICK_HALF_WIDTH + 4.0
                    : -TICK_HALF_WIDTH - 3.0 - text.getWidth(value) * LABEL_SCALE;
            label(context, text, value, labelX, PotionScopeRules.labelTop(offset, labelHeight));
        }
    }

    private static void label(DrawContext context, TextRenderer text, String value, double x, double y) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0);
        context.getMatrices().scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        context.drawTextWithShadow(text, value, 0, 0, LINE);
        context.getMatrices().pop();
    }

    private static void renderLoadedShell(DrawContext context, TextRenderer text, Optional<PotionShellType> loaded,
                                          int half) {
        Text line = PotionGunnerHud.loadedText(loaded);
        int right = half - CORNER_INSET;
        int top = -half + CORNER_INSET;
        int x = right - text.getWidth(line);
        if (loaded.isPresent()) {
            int swatch = 0xFF000000 | loaded.get().color();
            context.fill(x - 9, top + 1, x - 3, top + 7, 0xFFFFFFFF);
            context.fill(x - 8, top + 2, x - 4, top + 6, swatch);
        }
        context.drawTextWithShadow(text, line, x, top, PotionGunnerHud.baseColor(loaded));
    }

    /** A filled rectangle with a one-pixel dark shadow, readable on bright and dark scenes. / 带一像素暗影的填充矩形，明暗场景都可读。 */
    private static void line(DrawContext context, int x1, int y1, int x2, int y2) {
        context.fill(x1 + 1, y1 + 1, x2 + 1, y2 + 1, LINE_SHADOW);
        context.fill(x1, y1, x2, y2, LINE);
    }
}
