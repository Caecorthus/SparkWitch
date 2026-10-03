package dev.caecorthus.sparkwitch.client.prophet;

import dev.caecorthus.sparkwitch.client.prophet.ProphetProphecyClientRules.VictimStatus;
import dev.caecorthus.sparkwitch.net.OpenProphecyS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathCauseGroup;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Prophet's Prophecy screen: dead players on the left, the eleven fixed cause groups on the right, and one priced
 * confirmation. Selecting is free; only the confirm button sends, once, and then closes. Exclusions and solved
 * outcomes are read live from the owner-synced {@link ProphetPlayerComponent}; the screen never learns a death's
 * cause or killer before the server reveals it through a correct guess.
 * 先知的预言界面：左侧为死者，右侧为固定的十一个死因分组，底部为一次付费确认。选择免费；只有确认按钮会发送一次并随即
 * 关闭界面。已排除项与已猜中结果实时读取自仅同步给拥有者的 {@link ProphetPlayerComponent}；在服务端通过猜中揭示之前，
 * 界面永远不知道任何死者的死因或凶手。
 */
public final class ProphetProphecyScreen extends Screen {
    // Prophet gold on dark violet. 先知金配深紫。
    private static final int SCRIM = 0xA80A0612;
    private static final int EDGE = 0xFF07040B;
    private static final int RIM = 0xFFD4AF37;
    private static final int RIM_LO = 0xFF6E5A1E;
    private static final int BODY = 0xF61A1026;
    private static final int BAND = 0xFF2B1A42;
    private static final int WELL = 0xFF110A1B;
    private static final int WELL_EDGE = 0xFF2A1F38;
    private static final int HOVER = 0x26D4AF37;
    private static final int SELECT = 0xFF3B2758;
    private static final int SOLVED_FILL = 0xFF21180F;
    private static final int EXCLUDED_FILL = 0xFF261019;
    private static final int TEXT = 0xFFEFE6D2;
    private static final int MUTED = 0xFFA898B8;
    private static final int FAINT = 0xFF6E6180;
    private static final int GOLD = 0xFFD4AF37;
    private static final int GOLD_HI = 0xFFF2DA86;
    private static final int RED = 0xFFE26A6A;
    private static final int BUTTON = 0xFF2E1E46;
    private static final int BUTTON_HOVER = 0xFF453066;
    private static final int BUTTON_OFF = 0xFF1E1529;

    private static final int PAD = 6;
    private static final int GAP = 6;
    private static final int BAND_H = 18;
    private static final int ROW_H = 22;
    private static final int HEAD = 16;
    private static final int CELL_GAP = 3;
    private static final int CELL_ROWS = (ProphetDeathCauseGroup.values().length + 1) / 2;
    private static final int BUTTON_H = 16;
    private static final int SCROLLBAR_W = 3;
    private static final String ELLIPSIS = "…";

    private final UUID sessionId;
    private final List<OpenProphecyS2CPacket.Candidate> candidates;

    // Survives init() (resize, GUI scale). 在 init() 之间保留（窗口尺寸、界面缩放变化）。
    private @Nullable UUID selectedVictim;
    private @Nullable ProphetDeathCauseGroup selectedCause;
    private int listScroll;
    private int gridScroll;
    private boolean submitted;

    // Rebuilt by init(). 由 init() 重建。
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int cellW;
    private int cellH;
    private int headerY;
    private int hintY;
    private int buttonY;
    private int confirmX;
    private int confirmW;
    private int cancelX;
    private int cancelW;

    public ProphetProphecyScreen(OpenProphecyS2CPacket payload) {
        super(Text.translatable("gui.sparkwitch.prophecy.title"));
        sessionId = payload.sessionId();
        candidates = payload.candidates();
    }

    @Override
    protected void init() {
        panelW = Math.max(1, Math.min(400, width - 12));
        panelH = Math.max(1, Math.min(252, height - 12));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int innerX = panelX + PAD;
        int innerW = Math.max(1, panelW - PAD * 2);

        headerY = panelY + BAND_H + 6;
        int bodyTop = headerY + textRenderer.fontHeight + 3;
        buttonY = panelY + panelH - PAD - BUTTON_H;
        hintY = buttonY - textRenderer.fontHeight - 4;
        int bodyBottom = hintY - 4;
        int bodyH = Math.max(0, bodyBottom - bodyTop);

        listW = MathHelper.clamp(innerW * 5 / 12, Math.min(84, innerW / 2), 168);
        listX = innerX;
        listY = bodyTop;
        listH = bodyH;
        gridX = listX + listW + GAP;
        gridY = bodyTop;
        gridW = Math.max(1, innerX + innerW - gridX);
        gridH = bodyH;
        cellW = Math.max(1, (gridW - SCROLLBAR_W - 1 - CELL_GAP) / 2);
        cellH = MathHelper.clamp((gridH - (CELL_ROWS - 1) * CELL_GAP) / CELL_ROWS, 12, 20);

        cancelW = Math.max(44, textRenderer.getWidth(Text.translatable("gui.sparkwitch.prophecy.cancel")) + 16);
        confirmW = Math.max(72, textRenderer.getWidth(confirmLabel()) + 16);
        cancelX = panelX + panelW - PAD - cancelW;
        confirmX = cancelX - 4 - confirmW;

        if (selectedVictim == null) {
            selectFirstOpenVictim();
        }
        listScroll = MathHelper.clamp(listScroll, 0, maxListScroll());
        gridScroll = MathHelper.clamp(gridScroll, 0, maxGridScroll());
    }

    /** Closes once the session is gone (consumed, expired, cancelled, or the player is no longer a live Prophet). / 会话消失后关闭。 */
    @Override
    public void tick() {
        if (!submitted && !ProphetClientModule.isPending(sessionId)) {
            close();
            return;
        }
        // A victim solved while the screen was open (or between frames) is no longer guessable.
        // 界面打开期间被猜中的死者不再可选。
        if (selectedVictim != null && status(selectedVictim).solved()) {
            selectedVictim = null;
            selectedCause = null;
        }
        if (selectedCause != null && ProphetProphecyClientRules.isExcluded(record(selectedVictim), selectedCause)) {
            selectedCause = null;
        }
    }

    /** Only the scrim: vanilla's blur would smear the panel. / 只绘制遮罩：原版模糊会把面板糊掉。 */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, SCRIM);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawFrame(context);
        drawHeaders(context);
        drawVictims(context, mouseX, mouseY);
        drawCauses(context, mouseX, mouseY);
        drawFooter(context, mouseX, mouseY);
    }

    // ================================================================ drawing

    private void drawFrame(DrawContext context) {
        int right = panelX + panelW;
        int bottom = panelY + panelH;
        context.fill(panelX - 2, panelY - 2, right + 2, bottom + 2, EDGE);
        context.fill(panelX - 1, panelY - 1, right + 1, bottom + 1, RIM_LO);
        context.fill(panelX, panelY, right, bottom, BODY);
        context.fill(panelX + 1, panelY + 1, right - 1, panelY + 2, RIM);
        context.fill(panelX + 3, panelY + 3, right - 3, panelY + BAND_H, BAND);
        context.fill(panelX + 3, panelY + BAND_H, right - 3, panelY + BAND_H + 1, RIM_LO);
        // Small corner studs. 四角小铆钉。
        for (int x : new int[]{panelX + 1, right - 3}) {
            for (int y : new int[]{panelY + 1, bottom - 3}) {
                context.fill(x, y, x + 2, y + 2, RIM);
            }
        }
        int titleY = panelY + 3 + (BAND_H - 3 - textRenderer.fontHeight) / 2 + 1;
        String title = fit(this.title.getString(), panelW - 80);
        int titleWidth = textRenderer.getWidth(title);
        int titleX = panelX + (panelW - titleWidth) / 2;
        context.drawText(textRenderer, title, titleX, titleY, GOLD_HI, true);
        // Diamond flourishes beside the title. 标题两侧的菱形装饰。
        diamond(context, titleX - 8, titleY + 4);
        diamond(context, titleX + titleWidth + 6, titleY + 4);
        int seconds = (ProphetClientModule.remainingTicks() + 19) / 20;
        if (seconds > 0) {
            String time = Text.translatable("gui.sparkwitch.prophecy.time_left", seconds).getString();
            context.drawText(textRenderer, time, panelX + panelW - PAD - 2 - textRenderer.getWidth(time), titleY,
                    seconds <= 10 ? RED : FAINT, false);
        }
    }

    private void drawHeaders(DrawContext context) {
        context.drawText(textRenderer, fit(Text.translatable("gui.sparkwitch.prophecy.victims").getString(), listW),
                listX + 1, headerY, GOLD, false);
        String victimName = selectedVictim == null ? null : nameOf(selectedVictim);
        Text causes = victimName == null
                ? Text.translatable("gui.sparkwitch.prophecy.causes")
                : Text.translatable("gui.sparkwitch.prophecy.causes_for", victimName);
        context.drawText(textRenderer, fit(causes.getString(), gridW), gridX + 1, headerY, GOLD, false);
    }

    private void drawVictims(DrawContext context, int mouseX, int mouseY) {
        well(context, listX, listY, listW, listH);
        if (listH <= 2) {
            return;
        }
        if (candidates.isEmpty()) {
            String empty = fit(Text.translatable("gui.sparkwitch.prophecy.empty").getString(), listW - 8);
            context.drawText(textRenderer, empty, listX + 4, listY + 4, FAINT, false);
            return;
        }
        int rowW = listW - 2 - (maxListScroll() > 0 ? SCROLLBAR_W + 1 : 0);
        context.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        for (int index = 0; index < candidates.size(); index++) {
            int y = listY + 1 + index * ROW_H - listScroll;
            if (y + ROW_H <= listY || y >= listY + listH) {
                continue;
            }
            OpenProphecyS2CPacket.Candidate candidate = candidates.get(index);
            ProphecyRecord record = record(candidate.player());
            VictimStatus status = ProphetProphecyClientRules.status(record);
            boolean selected = candidate.player().equals(selectedVictim);
            boolean hovered = !status.solved() && inside(mouseX, mouseY, listX + 1, Math.max(y, listY), rowW,
                    Math.min(y + ROW_H, listY + listH) - Math.max(y, listY));
            int x = listX + 1;
            if (status.solved()) {
                context.fill(x, y, x + rowW, y + ROW_H - 1, SOLVED_FILL);
            } else if (selected) {
                context.fill(x, y, x + rowW, y + ROW_H - 1, SELECT);
                context.fill(x, y, x + 2, y + ROW_H - 1, GOLD);
            } else if (hovered) {
                context.fill(x, y, x + rowW, y + ROW_H - 1, HOVER);
            }
            context.fill(x + 2, y + ROW_H - 1, x + rowW - 2, y + ROW_H, WELL_EDGE);

            int headX = x + 4;
            int headY = y + (ROW_H - 1 - HEAD) / 2;
            if (status.solved()) {
                context.setShaderColor(0.55F, 0.55F, 0.55F, 1.0F);
            }
            PlayerSkinDrawer.draw(context, skinOf(candidate.player()), headX, headY, HEAD);
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            int textX = headX + HEAD + 4;
            int textW = Math.max(1, x + rowW - textX - 2);
            context.drawText(textRenderer, fit(candidate.name(), textW), textX, y + 2,
                    status.solved() ? MUTED : (selected ? GOLD_HI : TEXT), false);
            Text line = statusLine(record, status);
            context.drawText(textRenderer, fit(line.getString(), textW), textX, y + 2 + textRenderer.fontHeight + 1,
                    statusColor(status), false);
        }
        context.disableScissor();
        scrollbar(context, listX + listW - 1 - SCROLLBAR_W, listY + 1, listH - 2, listScroll, maxListScroll(),
                candidates.size() * ROW_H);
    }

    private void drawCauses(DrawContext context, int mouseX, int mouseY) {
        well(context, gridX, gridY, gridW, gridH);
        if (gridH <= 2) {
            return;
        }
        ProphecyRecord record = record(selectedVictim);
        boolean victimSelected = selectedVictim != null;
        context.enableScissor(gridX + 1, gridY + 1, gridX + gridW - 1, gridY + gridH - 1);
        ProphetDeathCauseGroup[] groups = ProphetDeathCauseGroup.values();
        for (int index = 0; index < groups.length; index++) {
            ProphetDeathCauseGroup group = groups[index];
            int x = cellX(index);
            int y = cellY(index);
            if (y + cellH <= gridY || y >= gridY + gridH) {
                continue;
            }
            boolean excluded = ProphetProphecyClientRules.isExcluded(record, group);
            boolean selectable = ProphetProphecyClientRules.isCauseSelectable(victimSelected, record, group);
            boolean selected = selectable && group == selectedCause;
            boolean hovered = selectable && inside(mouseX, mouseY, x, y, cellW, cellH)
                    && inside(mouseX, mouseY, gridX, gridY, gridW, gridH);

            int fill = excluded ? EXCLUDED_FILL : selected ? SELECT : hovered ? BUTTON_HOVER : BUTTON_OFF;
            int border = selected ? GOLD : excluded ? 0xFF5A2430 : hovered ? RIM_LO : WELL_EDGE;
            context.fill(x, y, x + cellW, y + cellH, border);
            context.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, fill);

            String label = fit(Text.translatable(group.translationKey()).getString(), cellW - 6);
            Text text = excluded
                    ? Text.literal(label).formatted(Formatting.STRIKETHROUGH)
                    : Text.literal(label);
            int color = excluded ? RED : !selectable ? FAINT : selected ? GOLD_HI : TEXT;
            context.drawText(textRenderer, text, x + (cellW - textRenderer.getWidth(text)) / 2,
                    y + (cellH - textRenderer.fontHeight) / 2 + 1, color, false);
        }
        context.disableScissor();
        scrollbar(context, gridX + gridW - 1 - SCROLLBAR_W, gridY + 1, gridH - 2, gridScroll, maxGridScroll(),
                gridContentHeight());
    }

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        context.fill(panelX + PAD, hintY - 3, panelX + panelW - PAD, hintY - 2, WELL_EDGE);
        Hint hint = hint();
        context.drawText(textRenderer, fit(hint.text().getString(), panelW - PAD * 2), panelX + PAD + 1, hintY,
                hint.color(), false);

        int balance = balance();
        Text balanceText = Text.translatable("gui.sparkwitch.prophecy.balance", balance);
        context.drawText(textRenderer, fit(balanceText.getString(), Math.max(1, confirmX - panelX - PAD - 4)),
                panelX + PAD + 1, buttonY + (BUTTON_H - textRenderer.fontHeight) / 2 + 1,
                ProphetProphecyClientRules.hasEnoughMoney(balance) ? GOLD : RED, false);

        boolean confirmActive = canConfirm();
        button(context, confirmX, confirmW, confirmLabel(), confirmActive,
                confirmActive && inside(mouseX, mouseY, confirmX, buttonY, confirmW, BUTTON_H), true);
        button(context, cancelX, cancelW, Text.translatable("gui.sparkwitch.prophecy.cancel"), true,
                inside(mouseX, mouseY, cancelX, buttonY, cancelW, BUTTON_H), false);
    }

    private void button(DrawContext context, int x, int w, Text label, boolean active, boolean hovered,
                        boolean primary) {
        int border = !active ? WELL_EDGE : hovered || primary ? GOLD : RIM_LO;
        int fill = !active ? BUTTON_OFF : hovered ? BUTTON_HOVER : BUTTON;
        context.fill(x, buttonY, x + w, buttonY + BUTTON_H, border);
        context.fill(x + 1, buttonY + 1, x + w - 1, buttonY + BUTTON_H - 1, fill);
        String text = fit(label.getString(), w - 6);
        context.drawText(textRenderer, text, x + (w - textRenderer.getWidth(text)) / 2,
                buttonY + (BUTTON_H - textRenderer.fontHeight) / 2 + 1,
                !active ? FAINT : primary ? GOLD_HI : TEXT, false);
    }

    private void well(DrawContext context, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        context.fill(x, y, x + w, y + h, WELL_EDGE);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
    }

    private void scrollbar(DrawContext context, int x, int y, int h, int scroll, int maxScroll, int contentHeight) {
        if (maxScroll <= 0 || h <= 0 || contentHeight <= 0) {
            return;
        }
        int thumb = MathHelper.clamp(h * h / contentHeight, 8, h);
        int thumbY = y + scroll * (h - thumb) / maxScroll;
        context.fill(x, y, x + SCROLLBAR_W, y + h, WELL_EDGE);
        context.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumb, RIM);
    }

    private static void diamond(DrawContext context, int cx, int cy) {
        context.fill(cx, cy - 2, cx + 1, cy + 3, RIM);
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, RIM);
        context.fill(cx - 2, cy, cx + 3, cy + 1, RIM);
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || submitted) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mouseX, mouseY, confirmX, buttonY, confirmW, BUTTON_H)) {
            if (canConfirm()) {
                click();
                submit();
            }
            return true;
        }
        if (inside(mouseX, mouseY, cancelX, buttonY, cancelW, BUTTON_H)) {
            click();
            close();
            return true;
        }
        if (inside(mouseX, mouseY, listX + 1, listY + 1, listW - 2, listH - 2)) {
            int index = ((int) mouseY - listY - 1 + listScroll) / ROW_H;
            if (index >= 0 && index < candidates.size()) {
                UUID victim = candidates.get(index).player();
                if (!status(victim).solved() && !victim.equals(selectedVictim)) {
                    selectedVictim = victim;
                    selectedCause = null;
                    click();
                }
            }
            return true;
        }
        if (inside(mouseX, mouseY, gridX + 1, gridY + 1, gridW - 2, gridH - 2)) {
            ProphetDeathCauseGroup[] groups = ProphetDeathCauseGroup.values();
            for (int index = 0; index < groups.length; index++) {
                if (inside(mouseX, mouseY, cellX(index), cellY(index), cellW, cellH)
                        && ProphetProphecyClientRules.isCauseSelectable(
                                selectedVictim != null, record(selectedVictim), groups[index])) {
                    selectedCause = groups[index];
                    click();
                    break;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, listX, listY, listW, listH)) {
            listScroll = MathHelper.clamp(listScroll - (int) Math.round(verticalAmount * ROW_H), 0, maxListScroll());
            return true;
        }
        if (inside(mouseX, mouseY, gridX, gridY, gridW, gridH)) {
            gridScroll = MathHelper.clamp(gridScroll - (int) Math.round(verticalAmount * (cellH + CELL_GAP)), 0,
                    maxGridScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Esc and cancel are free: the session is dropped and nothing is charged. / Esc 与取消免费：丢弃会话，不扣费。 */
    @Override
    public void close() {
        ProphetClientModule.cancel(sessionId);
        if (client != null && client.currentScreen == this) {
            client.setScreen(null);
        }
    }

    @Override
    public void removed() {
        ProphetClientModule.cancel(sessionId);
        super.removed();
    }

    private void submit() {
        if (submitted || client == null || selectedVictim == null || selectedCause == null) {
            return;
        }
        submitted = true;
        ProphetClientModule.confirm(client, sessionId, selectedVictim, selectedCause);
        close();
    }

    // ================================================================ state

    private boolean canConfirm() {
        return ProphetProphecyClientRules.canConfirm(submitted, ProphetClientModule.isPending(sessionId),
                selectedVictim != null, record(selectedVictim), selectedCause, balance());
    }

    private record Hint(Text text, int color) {
    }

    private Hint hint() {
        if (selectedVictim == null) {
            return new Hint(Text.translatable(allSolved()
                    ? "gui.sparkwitch.prophecy.hint.all_solved"
                    : "gui.sparkwitch.prophecy.hint.choose_victim"), MUTED);
        }
        if (selectedCause == null) {
            return new Hint(Text.translatable("gui.sparkwitch.prophecy.hint.choose_cause"), MUTED);
        }
        if (!ProphetProphecyClientRules.hasEnoughMoney(balance())) {
            return new Hint(Text.translatable("gui.sparkwitch.prophecy.hint.not_enough_money",
                    ProphetRules.PROPHECY_COIN_COST), RED);
        }
        return new Hint(Text.translatable("gui.sparkwitch.prophecy.hint.ready", nameOf(selectedVictim),
                Text.translatable(selectedCause.translationKey())), GOLD_HI);
    }

    private Text statusLine(@Nullable ProphecyRecord record, VictimStatus status) {
        return switch (status) {
            case SOLVED_KILLER -> Text.translatable("gui.sparkwitch.prophecy.status.killer",
                    record == null || record.killerName() == null ? "?" : record.killerName());
            case SOLVED_NO_KILLER -> Text.translatable("gui.sparkwitch.prophecy.status.no_killer");
            case PENDING -> Text.translatable("gui.sparkwitch.prophecy.status.excluded",
                    record == null ? 0 : record.excluded().size());
            case UNGUESSED -> Text.translatable("gui.sparkwitch.prophecy.status.unguessed");
        };
    }

    private static int statusColor(VictimStatus status) {
        return switch (status) {
            case SOLVED_KILLER, SOLVED_NO_KILLER -> GOLD;
            case PENDING -> RED;
            case UNGUESSED -> FAINT;
        };
    }

    private void selectFirstOpenVictim() {
        for (OpenProphecyS2CPacket.Candidate candidate : candidates) {
            if (!status(candidate.player()).solved()) {
                selectedVictim = candidate.player();
                return;
            }
        }
    }

    private boolean allSolved() {
        for (OpenProphecyS2CPacket.Candidate candidate : candidates) {
            if (!status(candidate.player()).solved()) {
                return false;
            }
        }
        return true;
    }

    private VictimStatus status(@Nullable UUID victim) {
        return ProphetProphecyClientRules.status(record(victim));
    }

    private @Nullable ProphecyRecord record(@Nullable UUID victim) {
        if (victim == null || client == null || client.player == null) {
            return null;
        }
        return ProphetPlayerComponent.KEY.get(client.player).prophecy(victim).orElse(null);
    }

    private int balance() {
        return client == null || client.player == null ? 0 : PlayerShopComponent.KEY.get(client.player).getBalance();
    }

    private @Nullable String nameOf(UUID victim) {
        for (OpenProphecyS2CPacket.Candidate candidate : candidates) {
            if (candidate.player().equals(victim)) {
                return candidate.name();
            }
        }
        return null;
    }

    private Identifier skinOf(UUID player) {
        ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
        PlayerListEntry entry = handler == null ? null : handler.getPlayerListEntry(player);
        return entry != null ? entry.getSkinTextures().texture() : DefaultSkinHelper.getSkinTextures(player).texture();
    }

    private Text confirmLabel() {
        return Text.translatable("gui.sparkwitch.prophecy.confirm", ProphetRules.PROPHECY_COIN_COST);
    }

    private void click() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ================================================================ layout

    private int cellX(int index) {
        return gridX + 2 + (index % 2) * (cellW + CELL_GAP);
    }

    private int cellY(int index) {
        return gridY + 2 + (index / 2) * (cellH + CELL_GAP) - gridScroll;
    }

    private int gridContentHeight() {
        return CELL_ROWS * cellH + (CELL_ROWS - 1) * CELL_GAP + 4;
    }

    private int maxGridScroll() {
        return Math.max(0, gridContentHeight() - gridH);
    }

    private int maxListScroll() {
        return Math.max(0, candidates.size() * ROW_H + 2 - listH);
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        int room = maxWidth - textRenderer.getWidth(ELLIPSIS);
        return room <= 0 ? "" : textRenderer.trimToWidth(text, room) + ELLIPSIS;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return width > 0 && height > 0 && x >= left && x < left + width && y >= top && y < top + height;
    }
}
