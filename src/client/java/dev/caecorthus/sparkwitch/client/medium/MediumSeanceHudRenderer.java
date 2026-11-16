package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumBlessing;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumWindowKind;
import dev.caecorthus.sparkwitch.util.TickTime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * The three-line prompt a dead round member sees while a vote window is open and the vote screen is not: the question
 * (通灵) or the blessing request (幽灵的加护), either "press the ability key to answer / choose" or the choice already
 * sent with "press to change", and the countdown. It covers the cases the pop-up cannot: the player closed it with
 * Esc, or another screen was open when the window started. Drawn only for players the server lets vote
 * ({@link MediumVoteClient#canVote}); the living never receive the window content at all.
 * 投票窗口开启且投票界面未打开时，阵亡的本局玩家看到的三行提示：提问（通灵）或加护请求（幽灵的加护）、「按技能键回答 /
 * 选择」或已提交的选择加「按键更改」、以及倒计时。它覆盖弹窗无法处理的情况：玩家按 Esc 关闭了弹窗，或窗口开启时已有其他
 * 界面打开。只对服务端允许投票的玩家绘制（{@link MediumVoteClient#canVote}）；存活玩家根本收不到窗口内容。
 */
public final class MediumSeanceHudRenderer {
    private static final int BOTTOM_OFFSET = 100;
    private static final int LINE_HEIGHT = 12;
    private static final int QUESTION_COLOR = MediumPaint.TEAL;
    private static final int HINT_COLOR = 0xFFFFFFFF;
    private static final int COUNTDOWN_COLOR = 0xFFB0B0B0;

    private MediumSeanceHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        // The vote screens show the same three things themselves. / 投票界面自身已显示同样的三项内容。
        if (client.options.hudHidden
                || client.currentScreen instanceof MediumVoteScreen
                || client.currentScreen instanceof MediumBlessingVoteScreen
                || !MediumVoteClient.canVote(client)) {
            return;
        }
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(client.world);
        boolean blessing = seance.getKind() == MediumWindowKind.BLESSING;
        TextRenderer renderer = client.textRenderer;
        int centerX = context.getScaledWindowWidth() / 2;
        int top = context.getScaledWindowHeight() - BOTTOM_OFFSET;
        Text headline = blessing
                ? Text.translatable("hud.sparkwitch.medium.blessing.asks")
                : Text.translatable("hud.sparkwitch.medium.seance.question", seance.getQuestion());
        context.drawCenteredTextWithShadow(renderer, headline, centerX, top, QUESTION_COLOR);
        context.drawCenteredTextWithShadow(renderer,
                blessing ? blessingHint(seance.getWindowId()) : answerHint(seance.getWindowId()),
                centerX, top + LINE_HEIGHT, HINT_COLOR);
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

    /**
     * The local player's own remembered answer, presentation only; the server keeps the vote that counts.
     * 本地玩家自己记住的回答，仅用于展示；计票以服务端保存的那一票为准。
     */
    private static Text answerHint(int windowId) {
        Boolean answer = MediumVoteClient.answer(windowId);
        if (answer == null) {
            return Text.translatable("hud.sparkwitch.medium.vote.prompt", SparkWitchClient.abilityKeyText());
        }
        return Text.translatable(
                "hud.sparkwitch.medium.vote.voted",
                Text.translatable(answer
                        ? "screen.sparkwitch.medium.vote.yes"
                        : "screen.sparkwitch.medium.vote.no"),
                SparkWitchClient.abilityKeyText()
        );
    }

    /** The remembered blessing pick, presentation only. / 记住的加护选择，仅用于展示。 */
    private static Text blessingHint(int windowId) {
        Integer choice = MediumVoteClient.choice(windowId);
        MediumBlessing picked = choice == null ? null : MediumBlessing.byIndex(choice);
        if (picked == null) {
            return Text.translatable("hud.sparkwitch.medium.blessing.prompt", SparkWitchClient.abilityKeyText());
        }
        return Text.translatable("hud.sparkwitch.medium.blessing.voted",
                Text.translatable(MediumBlessingVoteScreen.nameKey(picked)), SparkWitchClient.abilityKeyText());
    }
}
