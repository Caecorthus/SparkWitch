package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkfactionapi.client.api.card.InventoryCardPaint;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumBlessing;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceResultS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumWindowKind;
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
 * The private result of either vote window as a non-modal HUD card at the top centre of the screen (owner 2026-11-10:
 * never a vanilla title, so an admin {@code /title} cannot collide with it). One layout for both kinds: the title
 * between brass rules, a subtitle line (the 通灵 question, or who granted the blessing), the outcome as a big word
 * (幽灵的加护 adds the blessing icon at the same scale), and the detail lines (the blessing's effect, the tie note and
 * the 招魂 lines when the server sent them). It shows for about eight seconds, then fades, and never takes the mouse.
 * The card draws only what the caster-only packet carries and derives nothing; the caster may be dead by then and
 * still sees it. A newer result of either kind replaces the old one; leaving the world clears it.
 * 任一投票窗口的私密结果，以屏幕顶部居中的非模态 HUD 卡片显示（所有者 2026-11-10：绝不使用原版标题，以免与管理员的
 * {@code /title} 冲突）。两种结果共用一个布局：黄铜刻线之间的标题、副标题行（通灵的提问，或加护的赐予者）、以大字显示的结果
 * （幽灵的加护在同一倍数下加上加护图标），以及细节行（加护效果、服务端发来时才有的平票行与招魂行）。显示约八秒后渐隐，从不
 * 占用鼠标。卡片只绘制仅发给施放者的数据包内容，不做任何推断；此时施放者可能已死亡，仍然能看到。任一种类的新结果都会取代
 * 旧结果；离开世界时清除。
 */
public final class MediumResultCard {
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
    /** Icon to big word, in GUI px at any scale. / 图标与大字之间的间隔（任何倍数下都以 GUI 像素计）。 */
    private static final int ICON_GAP = 4;
    private static final int MAX_BOUND_LINES = 6;

    @Nullable
    private static MediumSeanceResultS2CPacket result;
    private static long receivedAtMs;
    @Nullable
    private static ClientWorld receivedWorld;

    private MediumResultCard() {
    }

    /**
     * Client thread. Only a confirmed SparkWitch server can put a card on screen. A granted Speed also starts the
     * corner chip's grace timer ({@link MediumHudRenderer#noteSpeedGranted}).
     * 客户端线程。只有已确认的 SparkWitch 服务器才能让卡片显示。获得速度时同时启动角落图标的计时
     * （{@link MediumHudRenderer#noteSpeedGranted}）。
     */
    static void receive(MinecraftClient client, MediumSeanceResultS2CPacket packet) {
        if (packet == null || client.world == null || client.player == null
                || !SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        result = packet;
        receivedAtMs = Util.getMeasuringTimeMs();
        receivedWorld = client.world;
        if (packet.blessing() == MediumBlessing.SPEED) {
            MediumHudRenderer.noteSpeedGranted(client.world, receivedAtMs);
        }
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

    /** What one card shows; built from the packet each frame. / 一张卡片显示的内容；每帧由数据包构建。 */
    private record Model(Text title, Text subtitle, int subtitleColor, @Nullable MediumBlessing icon, Text headline,
                         int headlineColor, List<Text> details) {
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
        Model model = shown.kind() == MediumWindowKind.BLESSING
                ? blessingModel(renderer, shown, maxWidth)
                : questionModel(renderer, shown, maxWidth);

        int headlineWidth = renderer.getWidth(model.headline());
        int iconWidth = model.icon() == null ? 0 : MediumPaint.ICON;
        // The big word falls back to normal size on a screen too narrow for it. / 屏幕过窄时大字退回普通字号。
        int scale = headWidth(headlineWidth, iconWidth, ANSWER_SCALE) <= maxWidth ? ANSWER_SCALE : 1;
        int headWidth = headWidth(headlineWidth, iconWidth, scale);

        int titleWidth = renderer.getWidth(model.title());
        int width = titleWidth + 2 * (RULE_GAP + RULE_MIN + RULE_INSET);
        width = Math.max(width, renderer.getWidth(model.subtitle()));
        width = Math.max(width, headWidth);
        for (Text detail : model.details()) {
            width = Math.max(width, renderer.getWidth(detail));
        }
        width = Math.max(minWidth, Math.min(maxWidth, width));

        int answerHeight = renderer.fontHeight * scale;
        int subtitleY = renderer.fontHeight + TITLE_GAP;
        int answerY = subtitleY + renderer.fontHeight + ANSWER_GAP;
        int detailY = answerY + answerHeight + ANSWER_GAP;
        int height = model.details().isEmpty()
                ? answerY + answerHeight
                : detailY + (model.details().size() - 1) * LINE_HEIGHT + renderer.fontHeight;

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
        int headX = centerX - headWidth / 2;
        int headY = y + answerY;
        MediumBlessing icon = model.icon();
        int iconScale = scale;

        // Every fill in one batch, then text: batched vertices flush per layer, not in call order.
        // 所有填充放入一个批次，然后再绘制文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            MediumPaint.fadeFrame(context, x, y, cardWidth, height, alpha);
            if (rules) {
                context.fill(x + RULE_INSET, ruleY, titleX - RULE_GAP, ruleY + 1, rule);
                context.fill(titleX + titleWidth + RULE_GAP, ruleY, x + cardWidth - RULE_INSET, ruleY + 1, rule);
            }
            if (icon != null) {
                MediumPaint.blessingIconShadowed(context, headX, headY, icon,
                        MediumPaint.withAlpha(model.headlineColor(), alpha), iconScale);
            }
        });

        context.drawTextWithShadow(renderer, model.title(), titleX, y, MediumPaint.withAlpha(MediumPaint.TEAL, alpha));
        drawCentered(context, renderer, model.subtitle(), centerX, y + subtitleY,
                MediumPaint.withAlpha(model.subtitleColor(), alpha));

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(headX + (icon == null ? 0 : MediumPaint.ICON * scale + ICON_GAP), headY, 0.0F);
        matrices.scale(scale, scale, 1.0F);
        context.drawTextWithShadow(renderer, model.headline(), 0, 0, MediumPaint.withAlpha(model.headlineColor(), alpha));
        matrices.pop();

        int muted = MediumPaint.withAlpha(InventoryCardPaint.MUTED, alpha);
        for (int i = 0; i < model.details().size(); i++) {
            drawCentered(context, renderer, model.details().get(i), centerX, y + detailY + i * LINE_HEIGHT, muted);
        }
    }

    /** Icon (scaled) + gap + word (scaled); no icon means the word alone. / 图标（缩放）+ 间隔 + 大字（缩放）。 */
    private static int headWidth(int wordWidth, int iconWidth, int scale) {
        return wordWidth * scale + (iconWidth > 0 ? iconWidth * scale + ICON_GAP : 0);
    }

    // ------------------------------------------------------------------------------------------------ 通灵

    private static Model questionModel(TextRenderer renderer, MediumSeanceResultS2CPacket shown, int maxWidth) {
        List<Text> details = new ArrayList<>();
        if (shown.tie()) {
            details.add(Text.translatable("hud.sparkwitch.medium.result.tie"));
        }
        addBoundLines(details, shown, maxWidth, renderer, bound -> {
            int outcome = bound.choice() == MediumRules.VOTE_YES
                    ? MediumSeanceResultS2CPacket.OUTCOME_YES
                    : MediumSeanceResultS2CPacket.OUTCOME_NO;
            return Text.translatable(answerKey(outcome)).withColor(answerColor(outcome) & 0xFFFFFF);
        });
        return new Model(Text.translatable("hud.sparkwitch.medium.result.title"),
                question(renderer, shown.question(), maxWidth), InventoryCardPaint.TEXT, null,
                Text.translatable(answerKey(shown.outcome())), answerColor(shown.outcome()), details);
    }

    /** The question inside its brackets; only the question is ellipsized, never the brackets. / 括号内的提问；只截断提问，不截断括号。 */
    private static Text question(TextRenderer renderer, String question, int maxWidth) {
        String key = "hud.sparkwitch.medium.result.question";
        int brackets = renderer.getWidth(Text.translatable(key, ""));
        return Text.translatable(key,
                InventoryCardPaint.ellipsize(renderer, question, Math.max(0, maxWidth - brackets)));
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

    // ------------------------------------------------------------------------------------------------ 幽灵的加护

    /**
     * The granted blessing in teal with its icon and effect line; "no one answered" as the subtitle when the server drew
     * it at random; the no-response word when nothing could be granted ({@code OUTCOME_BLESSING_NONE}).
     * 获得的加护以青绿显示，带图标与效果行；服务端随机抽取时副标题为「无人回应」；无法给予任何加护时
     * （{@code OUTCOME_BLESSING_NONE}）显示无人回应。
     */
    private static Model blessingModel(TextRenderer renderer, MediumSeanceResultS2CPacket shown, int maxWidth) {
        MediumBlessing granted = shown.blessing();
        List<Text> details = new ArrayList<>();
        if (granted != null) {
            details.add(Text.translatable(MediumBlessingVoteScreen.effectKey(granted)));
        }
        if (shown.tie()) {
            details.add(Text.translatable("hud.sparkwitch.medium.result.tie"));
        }
        addBoundLines(details, shown, maxWidth, renderer, bound -> {
            MediumBlessing pick = MediumBlessing.byIndex(bound.choice());
            return pick == null ? null
                    : Text.translatable(MediumBlessingVoteScreen.nameKey(pick)).withColor(MediumPaint.TEAL & 0xFFFFFF);
        });
        Text title = Text.translatable("hud.sparkwitch.medium.result.blessing.title");
        if (granted == null) {
            return new Model(title, Text.translatable("hud.sparkwitch.medium.result.blessing.none"),
                    InventoryCardPaint.MUTED, null, Text.translatable("hud.sparkwitch.medium.result.no_response"),
                    InventoryCardPaint.MUTED, details);
        }
        boolean random = shown.random();
        return new Model(title,
                Text.translatable(random
                        ? "hud.sparkwitch.medium.result.blessing.random"
                        : "hud.sparkwitch.medium.result.blessing.granted"),
                random ? InventoryCardPaint.MUTED : InventoryCardPaint.TEXT, granted,
                Text.translatable(MediumBlessingVoteScreen.nameKey(granted)), MediumPaint.TEAL, details);
    }

    // ------------------------------------------------------------------------------------------------ shared

    private interface ChoiceWord {
        /** The coloured word for a bound voter's own choice; null skips the line. / 绑定死者自己选择的着色词；null 跳过该行。 */
        @Nullable MutableText word(MediumSeanceResultS2CPacket.BoundAnswer bound);
    }

    /**
     * One line per 招魂-bound voter's own choice, exactly as the server sent them (capped, with a "more" line). The
     * server leaves the list empty unless the round's deaths are over half.
     * 每个招魂绑定死者自己的选择各一行，完全按服务端发来的内容（有上限，超出时显示「另有」行）。除非本局死者过半，服务端不会
     * 填写该列表。
     */
    private static void addBoundLines(List<Text> lines, MediumSeanceResultS2CPacket shown, int maxWidth,
                                      TextRenderer renderer, ChoiceWord words) {
        List<MediumSeanceResultS2CPacket.BoundAnswer> bound = shown.boundAnswers();
        int listed = 0;
        int shownCount = 0;
        for (MediumSeanceResultS2CPacket.BoundAnswer answer : bound) {
            MutableText word = words.word(answer);
            if (word == null) {
                continue;
            }
            shownCount++;
            if (listed < MAX_BOUND_LINES) {
                lines.add(boundLine(renderer, answer.name(), word, maxWidth));
                listed++;
            }
        }
        if (shownCount > listed) {
            lines.add(Text.translatable("hud.sparkwitch.medium.result.more", shownCount - listed));
        }
    }

    /** Only the player name is ellipsized, never the words around it. / 只截断玩家名，不截断其两侧的文字。 */
    private static Text boundLine(TextRenderer renderer, String playerName, MutableText word, int maxWidth) {
        String key = "hud.sparkwitch.medium.result.bound";
        int fixed = renderer.getWidth(Text.translatable(key, "", word));
        String name = InventoryCardPaint.ellipsize(renderer, playerName, Math.max(0, maxWidth - fixed));
        return Text.translatable(key, Text.literal(name).withColor(InventoryCardPaint.TEXT_HI & 0xFFFFFF), word);
    }

    private static void drawCentered(DrawContext context, TextRenderer renderer, Text text, int centerX, int y,
                                     int color) {
        context.drawTextWithShadow(renderer, text, centerX - renderer.getWidth(text) / 2, y, color);
    }
}
