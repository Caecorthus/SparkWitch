package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumSeanceComponent;
import dev.caecorthus.sparkwitch.util.TickTime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The dead's 通灵 vote screen (owner 2026-11-10: a GUI, not chat): the question, the countdown and the two answers
 * 是 / 否 on a Harpy Express panel. A click on an answer sends it and closes; Esc closes without answering, and the
 * shared ability key re-opens the screen to answer or to change the answer while the window is live. Enter and Space
 * do nothing, so an answer is always a deliberate click. The screen belongs to one window: it closes by itself when
 * that window ends, a new one starts, or the player may no longer vote. The server re-checks every vote.
 * 死者的通灵投票界面（所有者 2026-11-10：用界面而非聊天）：在哈比特快面板上显示提问、倒计时与「是」「否」两个回答。
 * 点击回答即发送并关闭；Esc 关闭但不作答，窗口进行期间按共享技能键可重新打开以作答或改票。回车与空格无效，回答只能来自
 * 明确的点击。界面只属于一个窗口：该窗口结束、新窗口开始或玩家不再可投票时自动关闭。每一票都由服务端重新校验。
 */
public final class MediumVoteScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int SCREEN_MARGIN = 8;
    /** Text and buttons keep this distance from the frame edge. / 文字与按钮距面板边缘的距离。 */
    private static final int INSET = 9;
    private static final int ASKS_Y = 27;
    private static final int QUESTION_Y = 39;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_QUESTION_LINES = 2;
    private static final int COUNTDOWN_GAP = 4;
    private static final int ICON_GAP = 3;
    private static final int BUTTON_TOP_GAP = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int STAMP_GAP = 4;
    private static final int RULE_GAP = 7;
    private static final int FOOTER_GAP = 5;
    private static final int FOOTER_ROW_HEIGHT = 13;
    private static final int BOTTOM_PAD = 6;
    private static final int HINT_GAP = 5;
    /** The countdown turns red for the last seconds. / 最后几秒倒计时变红。 */
    private static final int URGENT_SECONDS = 5;
    private static final String ESC_LABEL = "Esc";

    private final int windowId;
    private final String question;

    // Rebuilt by init() (resize, GUI scale). / 由 init() 重建（窗口尺寸、界面缩放变化）。
    private List<String> questionLines = List.of();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int countdownY;
    private int buttonY;
    private int buttonWidth;
    private int yesX;
    private int noX;
    private int ruleY;
    private int footerY;

    MediumVoteScreen(int windowId, String question) {
        super(Text.translatable("screen.sparkwitch.medium.vote.title"));
        this.windowId = windowId;
        this.question = question;
    }

    @Override
    protected void init() {
        panelWidth = Math.max(2 * INSET + BUTTON_GAP + 2, Math.min(PANEL_WIDTH, this.width - 2 * SCREEN_MARGIN));
        int budget = panelWidth - 2 * INSET;
        questionLines = fitLines(Text.translatable("screen.sparkwitch.medium.vote.question", question).getString(),
                budget);
        countdownY = QUESTION_Y + questionLines.size() * LINE_HEIGHT + COUNTDOWN_GAP;
        buttonY = countdownY + BUTTON_TOP_GAP;
        ruleY = buttonY + BUTTON_HEIGHT + RULE_GAP;
        footerY = ruleY + FOOTER_GAP;
        panelHeight = footerY + FOOTER_ROW_HEIGHT + BOTTOM_PAD;
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(0, (this.height - panelHeight) / 2);
        buttonWidth = (budget - BUTTON_GAP) / 2;
        yesX = panelX + INSET;
        noX = panelX + panelWidth - INSET - buttonWidth;
    }

    /**
     * The whole question in at most two lines; only a question too wide even for that loses its tail to an ellipsis.
     * 提问完整显示为至多两行；只有两行仍放不下的提问才会以省略号截断结尾。
     */
    private List<String> fitLines(String text, int budget) {
        List<StringVisitable> wrapped = this.textRenderer.getTextHandler().wrapLines(text, budget, Style.EMPTY);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < wrapped.size() && lines.size() < MAX_QUESTION_LINES; i++) {
            boolean lastShown = lines.size() == MAX_QUESTION_LINES - 1 && i < wrapped.size() - 1;
            if (lastShown) {
                StringBuilder rest = new StringBuilder();
                for (int j = i; j < wrapped.size(); j++) {
                    rest.append(wrapped.get(j).getString());
                }
                lines.add(InventoryCardPaint.ellipsize(this.textRenderer, rest.toString(), budget));
            } else {
                lines.add(InventoryCardPaint.ellipsize(this.textRenderer, wrapped.get(i).getString(), budget));
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * Closes once this window can no longer take the local player's answer: it ended, a newer one replaced it, the
     * server stopped revealing the question, or the player left the voter gate (round over, disconnected).
     * 本窗口无法再接受本地玩家的回答时关闭：窗口结束、被更新的窗口取代、服务端不再公开提问，或玩家不再满足投票门槛
     * （对局结束、断开连接）。
     */
    @Override
    public void tick() {
        if (this.client == null
                || !MediumVoteClient.canVote(this.client)
                || MediumSeanceComponent.KEY.get(this.client.world).getWindowId() != windowId) {
            close();
        }
    }

    /**
     * Scrim only, without vanilla's blur: the dead keep spectating behind the panel. {@code Screen.render} calls this
     * exactly once per frame.
     * 只画遮罩，不使用原版模糊：死者仍可透过面板继续观战。{@code Screen.render} 每帧只调用一次。
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, TarotLedgerPaint.LEDGER_SCRIM);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Boolean choice = MediumVoteClient.choice(windowId);
        boolean yesHot = overButton(yesX, mouseX, mouseY);
        boolean noHot = overButton(noX, mouseX, mouseY);
        boolean yesChosen = Boolean.TRUE.equals(choice);
        boolean noChosen = Boolean.FALSE.equals(choice);

        int seconds = secondsLeft();
        Text countdown = Text.translatable("hud.sparkwitch.medium.seance.remaining", seconds);
        int countdownColor = seconds <= URGENT_SECONDS ? InventoryCardPaint.ALERT_TEXT : InventoryCardPaint.MUTED;
        int countdownWidth = 5 + ICON_GAP + this.textRenderer.getWidth(countdown);
        int countdownX = panelX + (panelWidth - countdownWidth) / 2;

        Text yes = Text.translatable("screen.sparkwitch.medium.vote.yes");
        Text no = Text.translatable("screen.sparkwitch.medium.vote.no");
        int yesContentX = yesX + (buttonWidth - labelWidth(yes)) / 2;
        int noContentX = noX + (buttonWidth - labelWidth(no)) / 2;
        int stampY = panelY + buttonY + (BUTTON_HEIGHT - TarotLedgerLayout.STAMP_W) / 2;

        int keycapX = panelX + TarotLedgerLayout.FOOTER_INSET;
        int keycapY = panelY + footerY + TarotLedgerLayout.KEYCAP_Y;
        int keycapWidth = TarotLedgerLayout.keycapWidth(this.textRenderer.getWidth(ESC_LABEL));

        InventoryCardPaint.panel(context, panelX, panelY, panelWidth, panelHeight);
        // Every fill in one batch, then text: batched vertices flush per layer, not in call order.
        // 所有填充放入一个批次，然后再绘制文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            TarotLedgerPaint.titleBand(context, panelX, panelY, panelWidth);
            MediumPaint.wisp(context, panelX + TarotLedgerLayout.TITLE_ICON_X, panelY + TarotLedgerLayout.TITLE_Y,
                    MediumPaint.TEAL);
            InventoryCardPaint.hourglass(context, countdownX, panelY + countdownY + 1, countdownColor);
            MediumPaint.voteButton(context, yesX, panelY + buttonY, buttonWidth, BUTTON_HEIGHT, yesHot, yesChosen);
            MediumPaint.voteButton(context, noX, panelY + buttonY, buttonWidth, BUTTON_HEIGHT, noHot, noChosen);
            // The shape carries the answer (check / cross); the colour only adds weight.
            // 形状表达回答（勾 / 叉），颜色只作强调。
            TarotLedgerPaint.stamp(context, yesContentX, stampY, true, InventoryCardPaint.READY_TEXT);
            TarotLedgerPaint.stamp(context, noContentX, stampY, false, InventoryCardPaint.ALERT_TEXT);
            TarotLedgerPaint.etched(context, panelX + TarotLedgerLayout.FOOTER_INSET,
                    panelX + panelWidth - TarotLedgerLayout.FOOTER_INSET, panelY + ruleY);
            TarotLedgerPaint.keycap(context, keycapX, keycapY, keycapWidth);
        });

        int budget = panelWidth - 2 * INSET;
        context.drawText(this.textRenderer,
                InventoryCardPaint.ellipsize(this.textRenderer, this.title.getString(),
                        panelWidth - TarotLedgerLayout.TITLE_X - INSET),
                panelX + TarotLedgerLayout.TITLE_X, panelY + TarotLedgerLayout.TITLE_Y, InventoryCardPaint.TITLE,
                false);
        drawCentered(context, InventoryCardPaint.ellipsize(this.textRenderer,
                Text.translatable("screen.sparkwitch.medium.vote.asks").getString(), budget),
                panelY + ASKS_Y, InventoryCardPaint.MUTED);
        for (int i = 0; i < questionLines.size(); i++) {
            drawCentered(context, questionLines.get(i), panelY + QUESTION_Y + i * LINE_HEIGHT,
                    InventoryCardPaint.TEXT);
        }
        context.drawText(this.textRenderer, countdown, countdownX + 5 + ICON_GAP, panelY + countdownY,
                countdownColor, true);

        int labelY = panelY + buttonY + (BUTTON_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
        context.drawText(this.textRenderer, yes, yesContentX + TarotLedgerLayout.STAMP_W + STAMP_GAP, labelY,
                MediumPaint.voteLabelColor(yesHot, yesChosen), false);
        context.drawText(this.textRenderer, no, noContentX + TarotLedgerLayout.STAMP_W + STAMP_GAP, labelY,
                MediumPaint.voteLabelColor(noHot, noChosen), false);

        drawFooter(context, choice, keycapX, keycapY, keycapWidth, yes, no);
    }

    /**
     * Esc keycap and the "answer later" hint on the left, the answer already sent on the right. When both do not fit,
     * the hint gives way.
     * 左侧为 Esc 键帽与「稍后再答」提示，右侧为已提交的回答；两者放不下时省略提示。
     */
    private void drawFooter(DrawContext context, @Nullable Boolean choice, int keycapX, int keycapY, int keycapWidth,
                            Text yes, Text no) {
        int textY = panelY + footerY + TarotLedgerLayout.FOOTER_TEXT_Y;
        context.drawText(this.textRenderer, ESC_LABEL, keycapX + TarotLedgerLayout.KEYCAP_LABEL_X,
                keycapY + TarotLedgerLayout.KEYCAP_LABEL_Y, InventoryCardPaint.FAINT, false);
        int right = panelX + panelWidth - TarotLedgerLayout.FOOTER_INSET;
        int currentX = right;
        if (choice != null) {
            Text current = Text.translatable("screen.sparkwitch.medium.vote.current", (choice ? yes : no).copy()
                    .withColor((choice ? InventoryCardPaint.READY_TEXT : InventoryCardPaint.ALERT_TEXT) & 0xFFFFFF));
            currentX = right - this.textRenderer.getWidth(current);
            context.drawText(this.textRenderer, current, currentX, textY, InventoryCardPaint.TEXT, true);
        }
        Text later = Text.translatable("screen.sparkwitch.medium.vote.later", SparkWitchClient.abilityKeyText());
        int laterX = keycapX + keycapWidth + HINT_GAP;
        if (laterX + this.textRenderer.getWidth(later) + HINT_GAP <= currentX) {
            context.drawText(this.textRenderer, later, laterX, textY, InventoryCardPaint.MUTED, true);
        }
    }

    private void drawCentered(DrawContext context, String text, int y, int color) {
        context.drawText(this.textRenderer, text, panelX + (panelWidth - this.textRenderer.getWidth(text)) / 2, y,
                color, true);
    }

    /** Stamp + gap + label, the content centred in an answer button. / 印记 + 间隔 + 文字，即作答按钮内居中的内容。 */
    private int labelWidth(Text label) {
        return TarotLedgerLayout.STAMP_W + STAMP_GAP + this.textRenderer.getWidth(label);
    }

    private int secondsLeft() {
        return this.client == null || this.client.world == null
                ? 0
                : TickTime.ceilSeconds(MediumSeanceComponent.KEY.get(this.client.world).getTicksLeft());
    }

    private boolean overButton(int x, double mouseX, double mouseY) {
        int y = panelY + buttonY;
        return mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (overButton(yesX, mouseX, mouseY)) {
            answer(true);
            return true;
        }
        if (overButton(noX, mouseX, mouseY)) {
            answer(false);
            return true;
        }
        // A click anywhere else, inside or outside the panel, neither answers nor closes.
        // 点击其他位置（面板内外）既不作答也不关闭。
        return false;
    }

    private void answer(boolean yes) {
        MediumVoteClient.submit(windowId, yes);
        close();
    }

    /**
     * Only Esc acts. Every other key (Enter, Space, Tab included) is swallowed, so no key can ever cast a vote by
     * accident.
     * 只有 Esc 生效。其余按键（包括回车、空格与 Tab）一律被吞掉，任何按键都不会误投。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
        }
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
