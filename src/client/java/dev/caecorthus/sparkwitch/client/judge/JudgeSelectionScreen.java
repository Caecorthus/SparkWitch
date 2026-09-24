package dev.caecorthus.sparkwitch.client.judge;

import dev.caecorthus.sparkwitch.net.OpenJudgeSelectionS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Selection alone never spends coins: an explicit priced confirmation sends once. / 仅选择不扣费，明确显示价格的确认操作只发送一次。 */
public final class JudgeSelectionScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private final UUID sessionId;
    private final List<UUID> playerUuids;
    private final List<String> playerNames;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listY;
    private int listHeight;
    private int scroll;
    private int selected = -1;
    private boolean submitted;
    private List<OrderedText> warningLines = List.of();
    private ButtonWidget confirmButton;

    public JudgeSelectionScreen(OpenJudgeSelectionS2CPacket payload) {
        super(Text.translatable("gui.sparkwitch.judge.title"));
        sessionId = payload.sessionId();
        playerUuids = payload.playerUuids();
        playerNames = payload.playerNames();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(360, Math.max(1, width - 16));
        warningLines = textRenderer.wrapLines(Text.translatable("gui.sparkwitch.judge.cost_warning",
                JudgeRules.JUDGMENT_COST), Math.max(1, panelWidth - 16));
        int headerHeight = 26 + warningLines.size() * (textRenderer.fontHeight + 1) + 8;
        int available = Math.max(0, height - 16 - headerHeight - 52);
        listHeight = Math.min(Math.min(8, Math.max(1, playerUuids.size())) * ROW_HEIGHT,
                available / ROW_HEIGHT * ROW_HEIGHT);
        panelHeight = headerHeight + listHeight + 52;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(0, (height - panelHeight) / 2);
        listY = panelY + headerHeight;
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
        int buttonWidth = Math.max(1, (panelWidth - 24) / 2);
        int buttonY = listY + listHeight + 24;
        confirmButton = addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.sparkwitch.judge.confirm", JudgeRules.JUDGMENT_COST), button -> submit())
                .dimensions(panelX + 8, buttonY, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sparkwitch.judge.cancel"), button -> close())
                .dimensions(panelX + panelWidth - 8 - buttonWidth, buttonY, buttonWidth, 20).build());
        updateConfirmButton();
    }

    @Override
    public void tick() {
        if (!JudgeClientModule.isPending(sessionId)) {
            close();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int right = panelX + panelWidth;
        context.fill(panelX - 1, panelY - 1, right + 1, panelY + panelHeight + 1, 0xFFDFA94F);
        context.fill(panelX, panelY, right, panelY + panelHeight, 0xF0181818);
        context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(title.getString(), panelWidth - 16),
                panelX + panelWidth / 2, panelY + 8, 0xDFA94F);
        int warningY = panelY + 26;
        for (OrderedText line : warningLines) {
            context.drawTextWithShadow(textRenderer, line, panelX + 8, warningY, 0xDDDDDD);
            warningY += textRenderer.fontHeight + 1;
        }
        context.enableScissor(panelX + 2, listY, right - 2, listY + listHeight);
        if (playerUuids.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.sparkwitch.judge.empty"),
                    panelX + panelWidth / 2, listY + 6, 0xAAAAAA);
        }
        for (int index = 0; index < playerUuids.size(); index++) {
            int y = listY + index * ROW_HEIGHT - scroll;
            if (y + ROW_HEIGHT <= listY || y >= listY + listHeight) {
                continue;
            }
            if (index == selected || inside(mouseX, mouseY, panelX + 2, y, panelWidth - 8, ROW_HEIGHT)) {
                context.fill(panelX + 2, y, right - 6, y + ROW_HEIGHT,
                        index == selected ? 0xFF655131 : 0xFF383838);
            }
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(playerNames.get(index), Math.max(1, panelWidth - 24)),
                    panelX + 8, y + 6, 0xFFFFFF);
        }
        context.disableScissor();
        if (maxScroll() > 0 && listHeight > 0) {
            int thumb = Math.min(listHeight, Math.max(8, listHeight * listHeight / (playerUuids.size() * ROW_HEIGHT)));
            int y = listY + scroll * (listHeight - thumb) / maxScroll();
            context.fill(right - 4, listY, right - 2, listY + listHeight, 0xFF303030);
            context.fill(right - 4, y, right - 2, y + thumb, 0xFFDFA94F);
        }
        Text selection = selected < 0 ? Text.translatable("gui.sparkwitch.judge.choose")
                : Text.translatable("gui.sparkwitch.judge.selected", playerNames.get(selected));
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(selection.getString(), Math.max(1, panelWidth - 16)),
                panelX + 8, listY + listHeight + 8, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !submitted && inside(mouseX, mouseY, panelX + 2, listY, panelWidth - 8, listHeight)) {
            int index = ((int) mouseY - listY + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < playerUuids.size()) {
                selected = index;
                updateConfirmButton();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, panelX, listY, panelWidth, listHeight)) {
            scroll = MathHelper.clamp(scroll + (int) Math.round(-verticalAmount * ROW_HEIGHT), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        JudgeClientModule.cancel(sessionId);
        if (client != null && client.currentScreen == this) {
            client.setScreen(null);
        }
    }

    @Override
    public void removed() {
        JudgeClientModule.cancel(sessionId);
        super.removed();
    }

    private void submit() {
        if (submitted || selected < 0 || client == null) {
            return;
        }
        submitted = true;
        updateConfirmButton();
        JudgeClientModule.confirm(client, sessionId, playerUuids.get(selected));
        close();
    }

    private void updateConfirmButton() {
        confirmButton.active = !submitted && selected >= 0 && JudgeClientModule.isPending(sessionId);
    }

    private int maxScroll() {
        return Math.max(0, playerUuids.size() * ROW_HEIGHT - listHeight);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
