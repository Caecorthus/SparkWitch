package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceResultS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The private 通灵 result as a non-modal HUD card at the top centre of the screen (owner 2026-11-10: never a vanilla
 * title, so an admin {@code /title} cannot collide with it): the question, the tallied answer as a big word, and the
 * tie and 招魂 lines when the server sent them. It shows for about eight seconds, then fades, and never takes the
 * mouse. The card draws only what the caster-only packet carries and derives nothing; the caster may be dead by then
 * and still sees it. A newer result replaces the old one; leaving the world clears it.
 * 通灵的私密结果，以屏幕顶部居中的非模态 HUD 卡片显示（所有者 2026-11-10：绝不使用原版标题，以免与管理员的
 * {@code /title} 冲突）：提问、以大字显示的计票答案，以及服务端发来时才有的平票行与招魂行。显示约八秒后渐隐，从不占用
 * 鼠标。卡片只绘制仅发给施放者的数据包内容，不做任何推断；此时施放者可能已死亡，仍然能看到。新结果取代旧结果；
 * 离开世界时清除。
 */
public final class MediumSeanceResultCard {
    static final long FADE_IN_MS = 200L;
    static final long SOLID_MS = 8000L;
    static final long FADE_MS = 1000L;
    private static final long TOTAL_MS = FADE_IN_MS + SOLID_MS + FADE_MS;

    /** Top edge of the frame. / 外框上缘。 */
    private static final int TOP = 28;
    private static final int MIN_WIDTH = 150;
    private static final int SCREEN_MARGIN = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_GAP = 4;
    private static final int ANSWER_GAP = 5;
    private static final int RULE_GAP = 4;
    private static final int RULE_MIN = 10;
    private static final int RULE_INSET = 2;
    private static final int ANSWER_SCALE = 2;
    private static final int MAX_BOUND_LINES = 6;

    @Nullable
    private static MediumSeanceResultS2CPacket result;
    private static long receivedAtMs;
    @Nullable
    private static ClientWorld receivedWorld;

    private MediumSeanceResultCard() {
    }

    /**
     * Client thread. Only a confirmed SparkWitch server can put a card on screen.
     * 客户端线程。只有已确认的 SparkWitch 服务器才能让卡片显示。
     */
    static void receive(MinecraftClient client, MediumSeanceResultS2CPacket packet) {
        if (packet == null || client.world == null || client.player == null
                || !SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        result = packet;
        receivedAtMs = Util.getMeasuringTimeMs();
        receivedWorld = client.world;
    }

    /** Drops the card once it has faded, on a world change and on disconnect. / 渐隐结束、世界更换或断开连接时丢弃卡片。 */
    static void tick(MinecraftClient client) {
        if (result == null) {
            return;
        }
        if (client.world == null || client.world != receivedWorld
                || !SparkWitchServerConnection.isConfirmedServer()
                || Util.getMeasuringTimeMs() - receivedAtMs >= TOTAL_MS) {
            clear();
        }
    }

    static void clear() {
        result = null;
        receivedWorld = null;
    }

    /**
     * Card alpha {@code elapsedMs} after the result arrived: a short fade-in, solid, then a linear fade-out. Zero
     * means "do not draw", including every value under {@link MediumPaint#MIN_ALPHA}.
     * 收到结果 {@code elapsedMs} 毫秒后的透明度：短暂淡入、完全显示、再线性渐隐。返回 0 表示不绘制，低于
     * {@link MediumPaint#MIN_ALPHA} 的值也返回 0。
     */
    static int alpha(long elapsedMs) {
        long elapsed = Math.max(0L, elapsedMs);
        long alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = 0xFF * elapsed / FADE_IN_MS;
        } else if (elapsed < FADE_IN_MS + SOLID_MS) {
            alpha = 0xFF;
        } else {
            alpha = Math.max(0L, 0xFF * (TOTAL_MS - elapsed) / FADE_MS);
        }
        return alpha < MediumPaint.MIN_ALPHA ? 0 : (int) alpha;
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MediumSeanceResultS2CPacket shown = result;
        // No alive gate: the caster may have died during the window. / 不要求存活：施放者可能已在窗口期间死亡。
        if (shown == null || client.player == null || client.options.hudHidden) {
            return;
        }
        int alpha = alpha(Util.getMeasuringTimeMs() - receivedAtMs);
        if (alpha == 0) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int maxWidth = Math.max(1, screenWidth - 2 * (SCREEN_MARGIN + MediumPaint.FRAME));
        int minWidth = Math.min(maxWidth, MIN_WIDTH - 2 * MediumPaint.FRAME);

        Text title = Text.translatable("hud.sparkwitch.medium.result.title");
        Text question = question(renderer, shown.question(), maxWidth);
        Text answer = Text.translatable(answerKey(shown.outcome()));
        int answerColor = answerColor(shown.outcome());
        int answerWidth = renderer.getWidth(answer);
        // The big word falls back to normal size on a screen too narrow for it. / 屏幕过窄时大字退回普通字号。
        int scale = answerWidth * ANSWER_SCALE <= maxWidth ? ANSWER_SCALE : 1;
        List<Text> details = details(renderer, shown, maxWidth);

        int titleWidth = renderer.getWidth(title);
        int width = titleWidth + 2 * (RULE_GAP + RULE_MIN + RULE_INSET);
        width = Math.max(width, renderer.getWidth(question));
        width = Math.max(width, answerWidth * scale);
        for (Text detail : details) {
            width = Math.max(width, renderer.getWidth(detail));
        }
        width = Math.max(minWidth, Math.min(maxWidth, width));

        int answerHeight = renderer.fontHeight * scale;
        int questionY = renderer.fontHeight + TITLE_GAP;
        int answerY = questionY + renderer.fontHeight + ANSWER_GAP;
        int detailY = answerY + answerHeight + ANSWER_GAP;
        int height = details.isEmpty()
                ? answerY + answerHeight
                : detailY + (details.size() - 1) * LINE_HEIGHT + renderer.fontHeight;

        int x = (screenWidth - width) / 2;
        int y = TOP + MediumPaint.FRAME;
        int centerX = x + width / 2;
        int titleX = centerX - titleWidth / 2;
        int cardWidth = width;
        int ruleY = y + renderer.fontHeight / 2 - 1;
        int rule = MediumPaint.withAlpha(InventoryCardPaint.BRASS_LO, alpha);
        // DrawContext.fill swaps reversed corners, so a card too narrow for the rules must skip them.
        // DrawContext.fill 会交换颠倒的坐标，因此卡片窄到放不下刻线时必须跳过。
        boolean rules = titleX - RULE_GAP - (x + RULE_INSET) >= RULE_MIN;

        // Every fill in one batch, then text: batched vertices flush per layer, not in call order.
        // 所有填充放入一个批次，然后再绘制文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            MediumPaint.fadeFrame(context, x, y, cardWidth, height, alpha);
            if (rules) {
                context.fill(x + RULE_INSET, ruleY, titleX - RULE_GAP, ruleY + 1, rule);
                context.fill(titleX + titleWidth + RULE_GAP, ruleY, x + cardWidth - RULE_INSET, ruleY + 1, rule);
            }
        });

        context.drawTextWithShadow(renderer, title, titleX, y, MediumPaint.withAlpha(MediumPaint.TEAL, alpha));
        drawCentered(context, renderer, question, centerX, y + questionY,
                MediumPaint.withAlpha(InventoryCardPaint.TEXT, alpha));

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(centerX - answerWidth * scale / 2.0F, y + answerY, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        context.drawTextWithShadow(renderer, answer, 0, 0, MediumPaint.withAlpha(answerColor, alpha));
        matrices.pop();

        int muted = MediumPaint.withAlpha(InventoryCardPaint.MUTED, alpha);
        for (int i = 0; i < details.size(); i++) {
            drawCentered(context, renderer, details.get(i), centerX, y + detailY + i * LINE_HEIGHT, muted);
        }
    }

    /** The question inside its brackets; only the question is ellipsized, never the brackets. / 括号内的提问；只截断提问，不截断括号。 */
    private static Text question(TextRenderer renderer, String question, int maxWidth) {
        String key = "hud.sparkwitch.medium.result.question";
        int brackets = renderer.getWidth(Text.translatable(key, ""));
        return Text.translatable(key,
                InventoryCardPaint.ellipsize(renderer, question, Math.max(0, maxWidth - brackets)));
    }

    /**
     * The optional lines under the answer, exactly as the server sent them: the tie note, then one line per 招魂-bound
     * answer (capped, with a "more" line). The server leaves both empty unless the round's deaths are over half.
     * 答案下方的可选行，完全按服务端发来的内容绘制：平票提示，然后每个招魂绑定回答一行（有上限，超出时显示「另有」行）。
     * 除非本局死者过半，服务端不会填写这两项。
     */
    private static List<Text> details(TextRenderer renderer, MediumSeanceResultS2CPacket shown, int maxWidth) {
        List<Text> lines = new ArrayList<>();
        if (shown.tie()) {
            lines.add(Text.translatable("hud.sparkwitch.medium.result.tie"));
        }
        List<MediumSeanceResultS2CPacket.BoundAnswer> bound = shown.boundAnswers();
        int listed = Math.min(bound.size(), MAX_BOUND_LINES);
        for (int i = 0; i < listed; i++) {
            lines.add(boundLine(renderer, bound.get(i), maxWidth));
        }
        if (bound.size() > listed) {
            lines.add(Text.translatable("hud.sparkwitch.medium.result.more", bound.size() - listed));
        }
        return lines;
    }

    /** Only the player name is ellipsized, never the words around it. / 只截断玩家名，不截断其两侧的文字。 */
    private static Text boundLine(TextRenderer renderer, MediumSeanceResultS2CPacket.BoundAnswer bound, int maxWidth) {
        String key = "hud.sparkwitch.medium.result.bound";
        int outcome = bound.yes() ? MediumSeanceResultS2CPacket.OUTCOME_YES : MediumSeanceResultS2CPacket.OUTCOME_NO;
        MutableText word = Text.translatable(answerKey(outcome)).withColor(answerColor(outcome) & 0xFFFFFF);
        int fixed = renderer.getWidth(Text.translatable(key, "", word));
        String name = InventoryCardPaint.ellipsize(renderer, bound.name(), Math.max(0, maxWidth - fixed));
        return Text.translatable(key, Text.literal(name).withColor(InventoryCardPaint.TEXT_HI & 0xFFFFFF), word);
    }

    private static String answerKey(int outcome) {
        return switch (outcome) {
            case MediumSeanceResultS2CPacket.OUTCOME_YES -> "hud.sparkwitch.medium.result.yes";
            case MediumSeanceResultS2CPacket.OUTCOME_NO -> "hud.sparkwitch.medium.result.no";
            default -> "hud.sparkwitch.medium.result.no_response";
        };
    }

    private static int answerColor(int outcome) {
        return switch (outcome) {
            case MediumSeanceResultS2CPacket.OUTCOME_YES -> InventoryCardPaint.READY_TEXT;
            case MediumSeanceResultS2CPacket.OUTCOME_NO -> InventoryCardPaint.ALERT_TEXT;
            default -> InventoryCardPaint.MUTED;
        };
    }

    private static void drawCentered(DrawContext context, TextRenderer renderer, Text text, int centerX, int y,
                                     int color) {
        context.drawTextWithShadow(renderer, text, centerX - renderer.getWidth(text) / 2, y, color);
    }
}
