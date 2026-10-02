package dev.caecorthus.sparkwitch.client.riftwalker.tablet;

import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateCloseC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleRequestC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleS2CPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet.RiftGateConsoleDevices;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Role-owned Rift Gate console (keys {@code gui.sparkwitch.riftwalker.console.*}; never the witch skill panel).
 * Chinese-first, readable at GUI scale 2–3, styled like the Seeker console. One row per gate in number order: the fixed
 * {@code #n} (the in-gate HUD's numbering; never renumbered), distance in blocks plus a direction arrow relative to the
 * player's yaw (computed live from the snapshot position), the names inside (D16) or 「空」, and a 「关闭」 button that
 * needs a second click (「确认关闭？」 for about 3 s) before {@code rift_gate_close} is sent. Empty state 「还没有裂隙门」.
 * "Witch network" closes the console, arms a one-shot bypass and re-issues the vanilla use so SparkStrength opens its
 * own tablet. Polls {@code rift_gate_console_request(session)} about once per second, never pauses, and closes itself
 * on role/life/gate/tablet loss or when the server stops answering. Presentation only; the server validates everything.
 * 职业自有的裂隙门控制台（键名 {@code gui.sparkwitch.riftwalker.console.*}；绝不使用魔女技能面板）。中文优先，
 * GUI 缩放 2–3 下清晰可读，风格同搜寻者控制台。每扇门一行，按编号排序：固定的 {@code #n}（与门内 HUD 编号一致，
 * 从不重排）、距离（格）与相对玩家朝向的方向箭头（由快照位置实时计算）、门内玩家名（D16）或「空」，以及需要二次点击
 * （约 3 秒内显示「确认关闭？」）才发送 {@code rift_gate_close} 的「关闭」按钮。空列表显示「还没有裂隙门」。
 * 「魔女网络」关闭控制台、设置一次性旁路并重发原版使用，由 SparkStrength 打开自己的平板。打开期间约每秒发送一次
 * {@code rift_gate_console_request(session)} 轮询，不暂停游戏，并在失去职业/死亡/进门/失去平板或服务端不再应答时自行关闭。
 * 仅负责展示；一切由服务端校验。
 */
public class RiftGateConsoleScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 300;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int ROLE_COLOR = RiftwalkerRules.COLOR;
    private static final int BORDER_COLOR = 0xFF000000 | ROLE_COLOR;
    private static final int PANEL_COLOR = 0xF0181818;
    private static final int STRIPE_COLOR = 0x18FFFFFF;
    private static final int ARMED_ROW_COLOR = 0x40FF4D4D;
    private static final int TEXT_COLOR = 0xDDDDDD;
    private static final int HINT_COLOR = 0x9A9A9A;
    private static final int OCCUPIED_COLOR = 0xFFB02E;
    private static final int ARROW_COLOR = 0x9FB0FF;

    private final Hand hand;
    private final RiftGateCloseConfirm confirm = new RiftGateCloseConfirm();
    private int sessionId;
    private List<RiftGateConsoleS2CPacket.Entry> entries;
    private long lastSnapshotTick;
    private long lastPollTick;
    private long lastCloseSentTick = -1L;
    /** Layout-affecting state captured at the last init. / 上次 init 时记录的影响布局的状态。 */
    private int layoutKey;
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int rowsTop;
    private int visibleRows;
    private int rowButtonWidth;
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    @Nullable
    private ButtonWidget networkButton;

    public RiftGateConsoleScreen(Hand hand, RiftGateConsoleS2CPacket snapshot) {
        super(Text.translatable("gui.sparkwitch.riftwalker.console.title"));
        this.hand = hand;
        this.sessionId = snapshot.consoleSessionId();
        this.entries = snapshot.entries();
        long now = RiftGateConsoleOpener.clientTicks();
        this.lastSnapshotTick = now;
        this.lastPollTick = now;
    }

    public Hand hand() {
        return hand;
    }

    public int sessionId() {
        return sessionId;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** A same-session snapshot (poll answer or post-close refresh). / 同一会话的快照（轮询应答或关门后刷新）。 */
    void applySnapshot(RiftGateConsoleS2CPacket snapshot) {
        if (snapshot.consoleSessionId() != sessionId) {
            return;
        }
        entries = snapshot.entries();
        lastSnapshotTick = RiftGateConsoleOpener.clientTicks();
        confirm.retain(this::isListed);
        scroll = RiftGateConsoleClientRules.clampScroll(scroll, entries.size(), visibleRows);
        if (currentLayoutKey() != layoutKey) {
            clearAndInit();
        } else {
            refreshRows();
        }
    }

    /**
     * Moves this console to a newer session the client asked for (two open requests crossed under lag).
     * 让本控制台切换到客户端请求的更新会话（延迟下两个打开请求交错）。
     */
    void adoptSession(RiftGateConsoleS2CPacket snapshot) {
        sessionId = snapshot.consoleSessionId();
        applySnapshot(snapshot);
    }

    @Override
    protected void init() {
        rowButtons.clear();
        networkButton = null;
        panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, width - 16));
        int innerWidth = Math.max(1, panelWidth - PADDING * 2);
        visibleRows = visibleRowsFor(entries.size());
        int header = PADDING + LINE_HEIGHT * 2 + 4;
        int footer = 6 + BUTTON_HEIGHT + PADDING;
        panelHeight = header + Math.max(1, visibleRows) * ROW_HEIGHT + footer;
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(0, (height - panelHeight) / 2);
        rowsTop = panelY + header;
        scroll = RiftGateConsoleClientRules.clampScroll(scroll, entries.size(), visibleRows);
        layoutKey = currentLayoutKey();

        rowButtonWidth = Math.min(innerWidth / 2, 12 + max(
                textRenderer.getWidth(Text.translatable("gui.sparkwitch.riftwalker.console.close_gate")),
                textRenderer.getWidth(Text.translatable("gui.sparkwitch.riftwalker.console.confirm_close")),
                textRenderer.getWidth(Text.translatable("gui.sparkwitch.riftwalker.console.closing"))));
        int buttonX = panelX + PADDING + innerWidth - rowButtonWidth - (scrollable() ? SCROLLBAR_WIDTH + 3 : 0);
        Tooltip closeTooltip = Tooltip.of(Text.translatable("gui.sparkwitch.riftwalker.console.close_gate.tooltip"));
        for (int slot = 0; slot < visibleRows; slot++) {
            int row = slot;
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(
                            Text.translatable("gui.sparkwitch.riftwalker.console.close_gate"), b -> onRowButton(row))
                    .dimensions(buttonX, rowsTop + slot * ROW_HEIGHT + (ROW_HEIGHT - BUTTON_HEIGHT) / 2,
                            rowButtonWidth, BUTTON_HEIGHT)
                    .tooltip(closeTooltip)
                    .build());
            rowButtons.add(button);
        }

        int footerY = panelY + panelHeight - PADDING - BUTTON_HEIGHT;
        int x = panelX + PADDING;
        if (networkVisible()) {
            int half = Math.max(1, (innerWidth - BUTTON_GAP) / 2);
            networkButton = addDrawableChild(ButtonWidget.builder(
                            Text.translatable("gui.sparkwitch.riftwalker.console.network"), b -> openWitchNetwork())
                    .dimensions(x, footerY, half, BUTTON_HEIGHT).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sparkwitch.riftwalker.console.done"),
                    b -> close()).dimensions(x + innerWidth - half, footerY, half, BUTTON_HEIGHT).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.sparkwitch.riftwalker.console.done"),
                    b -> close()).dimensions(x, footerY, innerWidth, BUTTON_HEIGHT).build());
        }
        refreshRows();
    }

    @Override
    public void tick() {
        if (client == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        long now = RiftGateConsoleOpener.clientTicks();
        if (RiftGateConsoleClientRules.shouldAutoClose(RiftGateConsoleOpener.isServerReady(),
                RiftGateConsoleOpener.isLiveRiftwalker(player), RiftSessionService.isInside(player),
                RiftGateConsoleDevices.hasTabletInHotbar(player), now - lastSnapshotTick)) {
            close();
            return;
        }
        if (RiftGateConsoleClientRules.throttleElapsed(now, lastPollTick, RiftGateConsoleClientRules.POLL_INTERVAL_TICKS)) {
            lastPollTick = now;
            ClientPlayNetworking.send(new RiftGateConsoleRequestC2SPacket(sessionId));
        }
        confirm.tick(now);
        if (currentLayoutKey() != layoutKey) {
            clearAndInit();
            return;
        }
        refreshRows();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (scrollable() && verticalAmount != 0.0) {
            int next = RiftGateConsoleClientRules.clampScroll(scroll - (int) Math.signum(verticalAmount),
                    entries.size(), visibleRows);
            if (next != scroll) {
                scroll = next;
                refreshRows();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);
        int right = panelX + panelWidth;
        context.fill(panelX - 1, panelY - 1, right + 1, panelY + panelHeight + 1, BORDER_COLOR);
        context.fill(panelX, panelY, right, panelY + panelHeight, PANEL_COLOR);
        int innerWidth = Math.max(1, panelWidth - PADDING * 2);
        int centerX = panelX + panelWidth / 2;
        context.drawCenteredTextWithShadow(textRenderer, fit(title, innerWidth), centerX, panelY + PADDING, ROLE_COLOR);
        context.drawCenteredTextWithShadow(textRenderer,
                fit(Text.translatable("gui.sparkwitch.riftwalker.console.subtitle", entries.size()), innerWidth),
                centerX, panelY + PADDING + LINE_HEIGHT, HINT_COLOR);
        ClientPlayerEntity player = client == null ? null : client.player;
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    fit(Text.translatable("gui.sparkwitch.riftwalker.console.empty"), innerWidth),
                    centerX, rowsTop + (ROW_HEIGHT - textRenderer.fontHeight) / 2, TEXT_COLOR);
            return;
        }
        long now = RiftGateConsoleOpener.clientTicks();
        int textX = panelX + PADDING;
        int textWidth = Math.max(1, rowButtons.isEmpty() ? innerWidth
                : rowButtons.get(0).getX() - textX - BUTTON_GAP);
        for (int slot = 0; slot < visibleRows; slot++) {
            int index = scroll + slot;
            if (index >= entries.size()) {
                break;
            }
            RiftGateConsoleS2CPacket.Entry entry = entries.get(index);
            int top = rowsTop + slot * ROW_HEIGHT;
            int rowRight = panelX + panelWidth - PADDING;
            if (confirm.state(entry.number(), now) == RiftGateCloseConfirm.RowState.ARMED) {
                context.fill(panelX + 2, top, rowRight + PADDING - 2, top + ROW_HEIGHT, ARMED_ROW_COLOR);
            } else if (index % 2 == 1) {
                context.fill(panelX + 2, top, rowRight + PADDING - 2, top + ROW_HEIGHT, STRIPE_COLOR);
            }
            context.drawTextWithShadow(textRenderer, fit(locationLine(entry, player), textWidth),
                    textX, top + 3, TEXT_COLOR);
            boolean occupied = !entry.occupantNames().isEmpty();
            context.drawTextWithShadow(textRenderer, fitWithEllipsis(occupantLine(entry), textWidth),
                    textX, top + 3 + LINE_HEIGHT - 1, occupied ? OCCUPIED_COLOR : HINT_COLOR);
        }
        if (scrollable()) {
            drawScrollbar(context);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Full occupant list on hover when the row had to be trimmed. / 行被截断时，悬停显示完整的门内名单。
        Text hovered = hoveredOccupants(mouseX, mouseY);
        if (hovered != null) {
            context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(hovered, Math.max(80, panelWidth - PADDING * 2)),
                    mouseX, mouseY);
        }
    }

    // ---- Actions / 动作 ----

    private void onRowButton(int slot) {
        int index = scroll + slot;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        int gate = entries.get(index).number();
        long now = RiftGateConsoleOpener.clientTicks();
        if (confirm.state(gate, now) == RiftGateCloseConfirm.RowState.ARMED
                && !RiftGateConsoleClientRules.throttleElapsed(now, lastCloseSentTick,
                RiftGateConsoleClientRules.CLOSE_SEND_INTERVAL_TICKS)) {
            // Too soon after the previous close: keep the row armed rather than let the server drop it.
            // 距上次关门太近：保持待确认，而不是让服务端丢弃。
            return;
        }
        if (confirm.click(gate, now) == RiftGateCloseConfirm.Click.CONFIRMED) {
            if (ClientPlayNetworking.canSend(RiftGateCloseC2SPacket.ID)) {
                lastCloseSentTick = now;
                ClientPlayNetworking.send(new RiftGateCloseC2SPacket(sessionId, gate));
            } else {
                confirm.clear();
            }
        }
        refreshRows();
    }

    /**
     * Re-issues the vanilla use once so SparkStrength opens its own tablet; our opener passes because of the bypass.
     * 重发一次原版使用，由 SparkStrength 打开自己的平板；因旁路标记，我们的拦截器会放行。
     */
    private void openWitchNetwork() {
        if (client == null || client.player == null || client.interactionManager == null || !networkVisible()) {
            return;
        }
        ClientPlayerEntity player = client.player;
        close();
        RiftGateConsoleOpener.bypassOnce(hand);
        client.interactionManager.interactItem(player, hand);
    }

    // ---- State / 状态 ----

    private void refreshRows() {
        long now = RiftGateConsoleOpener.clientTicks();
        for (int slot = 0; slot < rowButtons.size(); slot++) {
            ButtonWidget button = rowButtons.get(slot);
            int index = scroll + slot;
            if (index >= entries.size()) {
                button.visible = false;
                button.active = false;
                continue;
            }
            button.visible = true;
            switch (confirm.state(entries.get(index).number(), now)) {
                case IDLE -> {
                    button.active = true;
                    button.setMessage(Text.translatable("gui.sparkwitch.riftwalker.console.close_gate"));
                }
                case ARMED -> {
                    button.active = true;
                    button.setMessage(Text.translatable("gui.sparkwitch.riftwalker.console.confirm_close")
                            .formatted(Formatting.RED));
                }
                case CLOSING -> {
                    button.active = false;
                    button.setMessage(Text.translatable("gui.sparkwitch.riftwalker.console.closing"));
                }
            }
        }
        if (networkButton != null) {
            networkButton.active = networkVisible();
        }
    }

    private boolean networkVisible() {
        ClientPlayerEntity player = client == null ? null : client.player;
        return player != null && SparkStrengthTabletCompat.isTablet(player.getStackInHand(hand));
    }

    private boolean isListed(int gate) {
        for (RiftGateConsoleS2CPacket.Entry entry : entries) {
            if (entry.number() == gate) {
                return true;
            }
        }
        return false;
    }

    private boolean scrollable() {
        return entries.size() > visibleRows;
    }

    /**
     * Row count, scrollbar (it moves the row buttons) and the 「魔女网络」 button; a change re-runs {@link #init()}.
     * 行数、滚动条（会移动行按钮）与「魔女网络」按钮；任一变化都会重新执行 {@link #init()}。
     */
    private int currentLayoutKey() {
        int rows = visibleRowsFor(entries.size());
        return rows << 2 | (entries.size() > rows ? 1 : 0) | (networkVisible() ? 2 : 0);
    }

    /**
     * Rows that fit the screen (at most {@link #MAX_VISIBLE_ROWS}), but never more than the gates listed (at least 1
     * for the empty state).
     * 屏幕能容纳的行数（最多 {@link #MAX_VISIBLE_ROWS}），但不超过门的数量（空列表至少 1 行）。
     */
    private int visibleRowsFor(int rows) {
        int chrome = PADDING + LINE_HEIGHT * 2 + 4 + 6 + BUTTON_HEIGHT + PADDING + 16;
        int fit = Math.max(1, (height - chrome) / ROW_HEIGHT);
        return Math.max(1, Math.min(Math.min(fit, MAX_VISIBLE_ROWS), Math.max(1, rows)));
    }

    private void drawScrollbar(DrawContext context) {
        int x = panelX + panelWidth - PADDING - SCROLLBAR_WIDTH;
        int trackHeight = visibleRows * ROW_HEIGHT;
        context.fill(x, rowsTop, x + SCROLLBAR_WIDTH, rowsTop + trackHeight, 0x40FFFFFF);
        int thumbHeight = Math.max(8, trackHeight * visibleRows / Math.max(1, entries.size()));
        int maxScroll = Math.max(1, entries.size() - visibleRows);
        int thumbY = rowsTop + (trackHeight - thumbHeight) * scroll / maxScroll;
        context.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, BORDER_COLOR);
    }

    /** {@code #n  12 格 ↗}: number in the role colour, distance and live arrow. / 编号（职业色）、距离与实时箭头。 */
    private Text locationLine(RiftGateConsoleS2CPacket.Entry entry, @Nullable ClientPlayerEntity player) {
        MutableText line = Text.empty().append(Text.translatable("gui.sparkwitch.riftwalker.console.gate",
                entry.number()).styled(style -> style.withColor(ROLE_COLOR).withBold(true)));
        if (player == null) {
            return line;
        }
        double dx = entry.x() - player.getX();
        double dy = entry.y() - player.getY();
        double dz = entry.z() - player.getZ();
        line.append(Text.literal("  "));
        line.append(Text.translatable("gui.sparkwitch.riftwalker.console.distance",
                RiftGateConsoleClientRules.blocks(dx, dy, dz)).formatted(Formatting.GRAY));
        line.append(Text.literal(" " + RiftGateConsoleClientRules.arrow(player.getYaw(), dx, dz))
                .styled(style -> style.withColor(ARROW_COLOR).withBold(true)));
        return line;
    }

    /** {@code 门内：A、B} or {@code 门内：空} (D16). / 门内玩家名或「空」（D16）。 */
    private static Text occupantLine(RiftGateConsoleS2CPacket.Entry entry) {
        List<String> names = entry.occupantNames();
        if (names.isEmpty()) {
            return Text.translatable("gui.sparkwitch.riftwalker.console.occupants",
                    Text.translatable("gui.sparkwitch.riftwalker.console.occupants.empty"));
        }
        MutableText joined = Text.empty();
        for (int index = 0; index < names.size(); index++) {
            if (index > 0) {
                joined.append(Text.translatable("gui.sparkwitch.riftwalker.console.name_separator"));
            }
            joined.append(Text.literal(names.get(index)));
        }
        return Text.translatable("gui.sparkwitch.riftwalker.console.occupants", joined);
    }

    @Nullable
    private Text hoveredOccupants(int mouseX, int mouseY) {
        if (rowButtons.isEmpty() || entries.isEmpty()) {
            return null;
        }
        int textX = panelX + PADDING;
        int textRight = rowButtons.get(0).getX() - BUTTON_GAP;
        if (mouseX < textX || mouseX >= textRight || mouseY < rowsTop || mouseY >= rowsTop + visibleRows * ROW_HEIGHT) {
            return null;
        }
        int index = scroll + (mouseY - rowsTop) / ROW_HEIGHT;
        if (index >= entries.size()) {
            return null;
        }
        Text occupants = occupantLine(entries.get(index));
        return textRenderer.getWidth(occupants) > textRight - textX ? occupants : null;
    }

    private OrderedText fit(Text text, int maxWidth) {
        return Language.getInstance().reorder(textRenderer.trimToWidth(text, maxWidth));
    }

    private OrderedText fitWithEllipsis(Text text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text.asOrderedText();
        }
        Text ellipsis = Text.literal("…");
        StringVisitable trimmed = textRenderer.trimToWidth(text,
                Math.max(0, maxWidth - textRenderer.getWidth(ellipsis)));
        return Language.getInstance().reorder(StringVisitable.concat(trimmed, ellipsis));
    }

    private static int max(int first, int... rest) {
        int result = first;
        for (int value : rest) {
            result = Math.max(result, value);
        }
        return result;
    }
}
