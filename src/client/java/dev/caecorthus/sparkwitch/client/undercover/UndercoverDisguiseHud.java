package dev.caecorthus.sparkwitch.client.undercover;

import dev.caecorthus.sparkwitch.component.UndercoverDisguiseComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Shows the undercover their disguise just below the round timer (which Wathe draws at y = 6, centred).
 * 在回合时间正下方显示卧底本局的伪装身份（Wathe 把时间画在 y = 6、水平居中）。
 */
public final class UndercoverDisguiseHud {

    private static final float TIMER_Y = 6.0F;
    private static final float LINE_GAP = 14.0F;
    private static final int COLOR = 0xC05C6E;
    /** How long the line stays fully visible, then fades out over {@link #FADE_TICKS}. 满显时长，之后在 FADE_TICKS 内渐隐。 */
    private static final long HOLD_TICKS = 1200L;
    private static final long FADE_TICKS = 200L;
    /** First tick this client saw a disguise, used as the fade clock. 首次看到伪装的 tick，作为渐隐计时起点。 */
    private static long firstSeenTick = -1L;

    private UndercoverDisguiseHud() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        // Dead players never show it, and a role change clears it: the marker belongs to the undercover only.
        // 死亡后不显示；换身份也会清掉——这个提示只属于卧底。
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        Identifier disguise = UndercoverDisguiseComponent.KEY.get(player).disguise();
        if (disguise == null) {
            firstSeenTick = -1L;
            return;
        }
        long now = player.getWorld().getTime();
        if (firstSeenTick < 0L) {
            firstSeenTick = now;
        }
        long elapsed = now - firstSeenTick;
        if (elapsed > HOLD_TICKS + FADE_TICKS) {
            return;
        }
        float alpha = elapsed <= HOLD_TICKS
                ? 1.0F
                : 1.0F - (elapsed - HOLD_TICKS) / (float) FADE_TICKS;
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text text = Text.translatable("hud.sparkwitch.undercover.disguise",
                Text.translatable("announcement.role." + disguise.getPath()));
        int color = ((int) (alpha * 255) << 24) | COLOR;
        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2f, TIMER_Y + LINE_GAP, 0);
        context.drawCenteredTextWithShadow(renderer, text, 0, 0, color);
        context.getMatrices().pop();
    }
}
