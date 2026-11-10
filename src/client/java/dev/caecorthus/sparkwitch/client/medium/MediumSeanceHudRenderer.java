package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * The three-line séance prompt every dead round member sees while a vote window is open: the question, the 1/2
 * instructions and the countdown. Drawn for dead round members only; living players never see the question.
 * 投票窗口开启时每名阵亡本局玩家看到的三行通灵提示：提问、1/2 说明与倒计时。只对已阵亡的本局玩家绘制；存活玩家
 * 永远看不到提问。
 */
public final class MediumSeanceHudRenderer {
    private static final int BOTTOM_OFFSET = 100;
    private static final int LINE_HEIGHT = 12;
    private static final int QUESTION_COLOR = 0xFF000000 | 0x54E0B8;
    private static final int HINT_COLOR = 0xFFB0B0B0;
    private static final int COUNTDOWN_COLOR = 0xFFFFFFFF;

    private MediumSeanceHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(client.world);
        if (!seance.isActive()) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        // Dead members of this round only: the overflow spectators and the living never see the question.
        // 只对已阵亡的本局成员显示：旁观者与存活玩家永远看不到提问。
        if (!game.hasAnyRole(player) || !game.isPlayerDead(player.getUuid())) {
            return;
        }
        String question = seance.getQuestion();
        if (question.isEmpty()) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        int centerX = context.getScaledWindowWidth() / 2;
        int top = context.getScaledWindowHeight() - BOTTOM_OFFSET;
        context.drawCenteredTextWithShadow(
                renderer,
                Text.translatable("hud.sparkwitch.medium.seance.question", question),
                centerX,
                top,
                QUESTION_COLOR
        );
        context.drawCenteredTextWithShadow(
                renderer,
                Text.translatable("hud.sparkwitch.medium.seance.vote_hint"),
                centerX,
                top + LINE_HEIGHT,
                HINT_COLOR
        );
        context.drawCenteredTextWithShadow(
                renderer,
                Text.translatable(
                        "hud.sparkwitch.medium.seance.remaining",
                        TickTime.ceilSeconds(seance.getTicksLeft())
                ),
                centerX,
                top + LINE_HEIGHT * 2,
                COUNTDOWN_COLOR
        );
    }
}
