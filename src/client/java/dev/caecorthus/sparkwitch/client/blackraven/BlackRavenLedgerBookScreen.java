package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.net.SelectBlackRavenDisguiseC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseSyncCodec;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-tab ledger: Perceived identities (read-only knowledge) and Absent Good Roles (interactive only when
 * opened from a mask session). The client never predicts a switch; the server answers with a sync.
 * Everything is laid out inside the vanilla book page (x + 36, y 32, 114 × 126) so no GUI scale overflows it.
 * 双页感知册：已感知身份（只读知识）与未登场的善良职业（仅在面具会话打开时可交互）。客户端从不预测切换，由服务端同步结果。
 * 所有内容都排在原版书页文字区域内（x + 36、y 32、114 × 126），任何界面缩放下都不会溢出。
 */
public final class BlackRavenLedgerBookScreen extends Screen {
    public enum Tab {
        PERCEIVED,
        ABSENT
    }

    private static final String LEDGER_KEY = "screen.sparkwitch.black_raven_ledger.";
    private static final int BOOK_SIZE = 192;
    private static final int BOOK_TOP = 2;
    private static final int TEXT_LEFT = 36;
    private static final int TEXT_TOP = 32;
    private static final int TEXT_WIDTH = 114;
    private static final int LINE_HEIGHT = 9;
    /** Vanilla page height 128 / line 9. / 原版书页高度 128 / 行高 9。 */
    private static final int MAX_PAGE_LINES = 14;
    private static final int ROW_HEIGHT = LINE_HEIGHT * 2;
    private static final int FOOTER_TOP = TEXT_TOP + BlackRavenDisguiseClientRules.ROWS_PER_PAGE * ROW_HEIGHT;
    private static final int FOOTER_MAX_LINES = MAX_PAGE_LINES - BlackRavenDisguiseClientRules.ROWS_PER_PAGE * 2;
    private static final int PAGE_INDICATOR_Y = 18;
    private static final int PAGE_TURN_Y = 159;
    private static final int TAB_Y = 196;
    private static final int DONE_Y = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int INK = 0x000000;
    private static final int MUTED_INK = 0x5F5F5F;
    private static final int GREYED_INK = 0x9C9C9C;
    private static final int HOVER_FILL = 0x22000000;
    private static final Text ELLIPSIS = Text.literal("…");

    private final int session;
    /** Client ticks since the open packet built this screen; drives the session expiry. / 自开启数据包构建本界面起的客户端刻数。 */
    private int ticksOpen;
    private Tab tab;
    private int perceivedPage;
    private int absentPage;
    private @Nullable BookScreen.Contents perceived;
    private List<BlackRavenDisguiseClientRules.Row> rows = List.of();
    private @Nullable BlackRavenDisguiseSyncCodec.View rowsKey;
    private final List<RowWidget> rowWidgets = new ArrayList<>();
    private @Nullable PageTurnWidget nextPage;
    private @Nullable PageTurnWidget previousPage;
    private @Nullable ButtonWidget perceivedTabButton;
    private @Nullable ButtonWidget absentTabButton;

    public BlackRavenLedgerBookScreen(Tab initialTab, int session) {
        super(Text.translatable("item.sparkwitch.black_raven_ledger"));
        this.session = session;
        this.tab = initialTab;
    }

    /** session > 0 makes Tab B rows clickable; 0 means read-only. / session > 0 时 Tab B 可点击；0 为只读。 */
    public static void open(MinecraftClient client, Tab tab, int session) {
        if (client.player == null
                || !SparkWitchServerConnection.isConfirmedServer()
                || !BlackRavenClientState.isEligible(client.player)) {
            return;
        }
        client.setScreen(new BlackRavenLedgerBookScreen(tab, Math.max(0, session)));
    }

    @Override
    protected void init() {
        rowWidgets.clear();
        rowsKey = null;
        int left = bookLeft();
        perceivedTabButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable(LEDGER_KEY + "tab.perceived"), button -> selectTab(Tab.PERCEIVED))
                .dimensions(width / 2 - 100, TAB_Y, 98, BUTTON_HEIGHT)
                .build());
        absentTabButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable(LEDGER_KEY + "tab.absent"), button -> selectTab(Tab.ABSENT))
                .dimensions(width / 2 + 2, TAB_Y, 98, BUTTON_HEIGHT)
                .build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(width / 2 - 100, DONE_Y, 200, BUTTON_HEIGHT)
                .build());
        nextPage = addDrawableChild(new PageTurnWidget(left + 116, PAGE_TURN_Y, true, button -> turnPage(1), true));
        previousPage = addDrawableChild(new PageTurnWidget(left + 43, PAGE_TURN_Y, false, button -> turnPage(-1), true));
        if (client != null && client.player != null) {
            perceived = BlackRavenLedgerScreen.perceivedContents(client.player);
        }
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (client == null) {
            return;
        }
        // Auto-close once the local player is no longer a living Black Raven in a running round.
        // 本地玩家不再是本局存活的黑羽鸦时自动关闭。
        if (client.player == null || !BlackRavenClientState.isEligible(client.player)) {
            close();
            return;
        }
        if (ticksOpen < Integer.MAX_VALUE) {
            ticksOpen++;
        }
        refresh();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            turnPage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            turnPage(1);
            return true;
        }
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        renderInGameBackground(context);
        context.drawTexture(BookScreen.BOOK_TEXTURE, bookLeft(), BOOK_TOP, 0, 0, BOOK_SIZE, BOOK_SIZE);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int left = bookLeft();
        Text indicator = Text.translatable("book.pageIndicator", currentPage() + 1, pageCount());
        context.drawText(textRenderer, indicator,
                left - textRenderer.getWidth(indicator) + BOOK_SIZE - 44, PAGE_INDICATOR_Y, INK, false);
        if (tab == Tab.PERCEIVED) {
            renderPerceived(context, left);
        } else {
            renderAbsent(context, left);
        }
    }

    private void renderPerceived(DrawContext context, int left) {
        if (perceived == null || perceived.getPageCount() == 0) {
            return;
        }
        List<OrderedText> lines = textRenderer.wrapLines(perceived.getPage(perceivedPage), TEXT_WIDTH);
        int count = Math.min(MAX_PAGE_LINES, lines.size());
        for (int index = 0; index < count; index++) {
            context.drawText(textRenderer, lines.get(index),
                    left + TEXT_LEFT, TEXT_TOP + index * LINE_HEIGHT, INK, false);
        }
    }

    private void renderAbsent(DrawContext context, int left) {
        BlackRavenDisguiseSyncCodec.View view = currentView();
        int x = left + TEXT_LEFT;
        if (rows.isEmpty()) {
            List<OrderedText> empty = textRenderer.wrapLines(
                    Text.translatable(LEDGER_KEY + "absent.empty"), TEXT_WIDTH);
            int count = Math.min(BlackRavenDisguiseClientRules.ROWS_PER_PAGE * 2, empty.size());
            for (int index = 0; index < count; index++) {
                context.drawText(textRenderer, empty.get(index), x, TEXT_TOP + index * LINE_HEIGHT, MUTED_INK, false);
            }
        }
        List<Text> footer = footerTexts(view);
        int line = 0;
        for (Text text : footer) {
            for (OrderedText wrapped : textRenderer.wrapLines(text, TEXT_WIDTH)) {
                if (line >= FOOTER_MAX_LINES) {
                    return;
                }
                context.drawText(textRenderer, wrapped, x, FOOTER_TOP + line * LINE_HEIGHT, MUTED_INK, false);
                line++;
            }
        }
    }

    /** Status first, then the Black Raven wallet while disguised (amendment W). / 先状态，伪装时再显示黑羽鸦钱包（修订 W）。 */
    private List<Text> footerTexts(BlackRavenDisguiseSyncCodec.View view) {
        if (!view.bound()) {
            return List.of();
        }
        List<Text> footer = new ArrayList<>();
        int seconds = BlackRavenDisguiseClientRules.statusSeconds(view);
        footer.add(switch (BlackRavenDisguiseClientRules.status(view)) {
            case LOCKED -> Text.translatable(LEDGER_KEY + "footer.locked", seconds);
            case COOLDOWN -> Text.translatable(LEDGER_KEY + "footer.cooldown", seconds);
            case READY -> session <= 0
                    ? Text.translatable(LEDGER_KEY + "absent.read_only")
                    : BlackRavenDisguiseClientRules.sessionExpired(session, ticksOpen)
                            ? Text.translatable(LEDGER_KEY + "footer.session_expired")
                            : Text.translatable(LEDGER_KEY + "footer.ready");
        });
        if (view.disguised()) {
            footer.add(Text.translatable(LEDGER_KEY + "footer.raven_wallet", view.ravenBalance()));
        }
        return footer;
    }

    private void selectTab(Tab next) {
        tab = next;
        rowsKey = null;
        refresh();
    }

    private void turnPage(int delta) {
        if (tab == Tab.PERCEIVED) {
            perceivedPage = Math.clamp((long) perceivedPage + delta, 0, pageCount() - 1);
        } else {
            absentPage = Math.clamp((long) absentPage + delta, 0, pageCount() - 1);
            rowsKey = null;
        }
        refresh();
    }

    private void refresh() {
        BlackRavenDisguiseSyncCodec.View view = currentView();
        // Countdowns tick locally every frame; rows are rebuilt only when the synced identity or pool changes.
        // 倒计时每帧本地递减；仅在同步的身份或名单变化时重建行。
        BlackRavenDisguiseSyncCodec.View key = new BlackRavenDisguiseSyncCodec.View(
                view.bound(), view.acting(), 0, 0, view.pool(), 0);
        if (!key.equals(rowsKey)) {
            rowsKey = key;
            rows = BlackRavenDisguiseClientRules.rows(view, roleOrder());
            layoutRows();
        }
        for (RowWidget widget : rowWidgets) {
            widget.active = BlackRavenDisguiseClientRules.clickable(widget.row, session, ticksOpen, view);
        }
        perceivedPage = Math.clamp(perceivedPage, 0, perceivedPageCount() - 1);
        absentPage = Math.clamp(absentPage, 0, BlackRavenDisguiseClientRules.pageCount(rows.size()) - 1);
        int page = currentPage();
        int count = pageCount();
        if (nextPage != null) {
            nextPage.visible = page < count - 1;
        }
        if (previousPage != null) {
            previousPage.visible = page > 0;
        }
        if (perceivedTabButton != null) {
            perceivedTabButton.active = tab != Tab.PERCEIVED;
        }
        if (absentTabButton != null) {
            absentTabButton.active = tab != Tab.ABSENT;
        }
    }

    private void layoutRows() {
        for (RowWidget widget : rowWidgets) {
            remove(widget);
        }
        rowWidgets.clear();
        if (tab != Tab.ABSENT) {
            return;
        }
        absentPage = Math.clamp(absentPage, 0, BlackRavenDisguiseClientRules.pageCount(rows.size()) - 1);
        List<BlackRavenDisguiseClientRules.Row> page = BlackRavenDisguiseClientRules.page(rows, absentPage);
        int x = bookLeft() + TEXT_LEFT;
        for (int index = 0; index < page.size(); index++) {
            RowWidget widget = new RowWidget(x, TEXT_TOP + index * ROW_HEIGHT, page.get(index));
            rowWidgets.add(addDrawableChild(widget));
        }
    }

    private void select(BlackRavenDisguiseClientRules.Row row) {
        if (client == null
                || client.player == null
                || !BlackRavenDisguiseClientRules.clickable(row, session, ticksOpen, currentView())
                || !ClientPlayNetworking.canSend(SelectBlackRavenDisguiseC2SPacket.ID)) {
            return;
        }
        // The server consumes the one-shot session and re-validates; the result arrives as a sync + actionbar.
        // 服务端消费一次性会话并重新校验；结果通过同步与动作栏返回。
        ClientPlayNetworking.send(new SelectBlackRavenDisguiseC2SPacket(session, row.id()));
        close();
    }

    private BlackRavenDisguiseSyncCodec.View currentView() {
        return client == null ? BlackRavenDisguiseSyncCodec.View.EMPTY : BlackRavenDisguiseClientState.view(client.player);
    }

    private int currentPage() {
        return tab == Tab.PERCEIVED ? perceivedPage : absentPage;
    }

    private int pageCount() {
        return tab == Tab.PERCEIVED ? perceivedPageCount() : BlackRavenDisguiseClientRules.pageCount(rows.size());
    }

    private int perceivedPageCount() {
        return perceived == null ? 1 : Math.max(1, perceived.getPageCount());
    }

    private int bookLeft() {
        return (width - BOOK_SIZE) / 2;
    }

    private static List<Identifier> roleOrder() {
        List<Identifier> order = new ArrayList<>(WatheRoles.ROLES.size());
        for (Role role : WatheRoles.ROLES) {
            order.add(role.identifier());
        }
        return order;
    }

    /** Single-line fit: trims with an ellipsis so nothing leaves the page. / 单行适配：超宽时加省略号截断，绝不溢出书页。 */
    private OrderedText fit(Text text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text.asOrderedText();
        }
        StringVisitable trimmed = textRenderer.trimToWidth(text, Math.max(0, maxWidth - textRenderer.getWidth(ELLIPSIS)));
        return Language.getInstance().reorder(StringVisitable.concat(trimmed, ELLIPSIS));
    }

    /** One Tab B row drawn as two book lines: role name, then a short muted tag. / Tab B 一行：职业名与一行简短提示。 */
    private final class RowWidget extends ClickableWidget {
        private final BlackRavenDisguiseClientRules.Row row;

        private RowWidget(int x, int y, BlackRavenDisguiseClientRules.Row row) {
            super(x, y, TEXT_WIDTH, ROW_HEIGHT, label(row));
            this.row = row;
            Text tooltip = tooltip(row);
            if (tooltip != null) {
                setTooltip(Tooltip.of(tooltip));
            }
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            if (active && isHovered()) {
                context.fill(getX() - 2, getY(), getX() + getWidth() + 2, getY() + getHeight() - 1, HOVER_FILL);
            }
            Text name = label(row);
            int nameColor = row.greyed() ? GREYED_INK : INK;
            Text shown = row.greyed() ? Text.literal(name.getString()) : name;
            context.drawText(textRenderer, fit(shown, getWidth()), getX(), getY() + 1, nameColor, false);
            Text tag = tag(row);
            if (tag != null) {
                context.drawText(textRenderer, fit(tag, getWidth() - 4), getX() + 4, getY() + 1 + LINE_HEIGHT,
                        row.greyed() ? GREYED_INK : MUTED_INK, false);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            select(row);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private static Text label(BlackRavenDisguiseClientRules.Row row) {
        if (row.kind() == BlackRavenDisguiseClientRules.RowKind.REVERT) {
            return Text.translatable(LEDGER_KEY + "absent.revert")
                    .styled(style -> style.withColor(BlackRavenRules.COLOR));
        }
        return BlackRavenDisguiseClientState.roleName(row.id());
    }

    private static @Nullable Text tag(BlackRavenDisguiseClientRules.Row row) {
        if (row.kind() == BlackRavenDisguiseClientRules.RowKind.REVERT) {
            return null;
        }
        if (row.current()) {
            return Text.translatable(LEDGER_KEY + "absent.current");
        }
        if (row.greyed()) {
            return Text.translatable(LEDGER_KEY + "absent.tag." + tagSuffix(row.flag()));
        }
        return row.visited() ? Text.translatable(LEDGER_KEY + "absent.tag.visited") : null;
    }

    private @Nullable Text tooltip(BlackRavenDisguiseClientRules.Row row) {
        if (row.greyed() && row.flag() != null && row.flag().reasonKey() != null) {
            return Text.translatable(row.flag().reasonKey());
        }
        if (row.current()) {
            return Text.translatable(LEDGER_KEY + "absent.current");
        }
        List<Text> parts = new ArrayList<>();
        if (row.visited()) {
            parts.add(Text.translatable(LEDGER_KEY + "absent.visited"));
        }
        if (session <= 0) {
            parts.add(Text.translatable(LEDGER_KEY + "absent.read_only"));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return Texts.join(parts, Text.literal("\n"));
    }

    private static String tagSuffix(@Nullable BlackRavenDisguiseRules.PoolFlag flag) {
        if (flag == null) {
            return "unknown";
        }
        return switch (flag) {
            case POLICE -> "police";
            case DENYLISTED -> "denylisted";
            case UNSUPPORTED -> "unsupported";
            case SELECTABLE, UNKNOWN -> "unknown";
        };
    }
}
