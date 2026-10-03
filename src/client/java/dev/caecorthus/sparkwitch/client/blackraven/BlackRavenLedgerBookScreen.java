package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Block;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Content;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Footer;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Grid;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Hit;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.HitKind;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Pager;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Panel;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Rect;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Rule;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.TabHint;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.TitleBar;
import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerPaint.PillKind;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.net.SelectBlackRavenDisguiseC2SPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseSyncCodec;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Two-tab Perception Ledger in the role's "raven noir" panel. Tab A, Perceived identities, is read-only knowledge: a
 * ruled, paged grid of player faces with the role frozen for each. Tab B, Absent Good Roles, is a sectioned, scrolling
 * disguise list with the revert button in the footer, interactive only when opened from a mask session. Everything is
 * code-drawn: geometry and hit testing come from {@link BlackRavenLedgerLayout}, tokens and fills from
 * {@link BlackRavenLedgerPaint}, and one per-frame model feeds both paint and hit test, so what is painted is what is
 * clicked. The client never predicts a switch; a click sends one select and closes, and the server answers with a
 * sync.
 * 双页感知册，采用职业自有的“鸦羽暗夜”面板。Tab A“已感知身份”是只读知识：带横线、可翻页的玩家头像网格，每格附其
 * 定格的职业。Tab B“未登场的善良职业”是分区、可滚动的伪装名单，恢复按钮位于页脚，仅在面具会话打开时可交互。全部由
 * 代码绘制：几何与命中判定来自 {@link BlackRavenLedgerLayout}，令牌与填充来自 {@link BlackRavenLedgerPaint}，同一份
 * 逐帧模型同时供绘制与命中使用，绘制即命中。客户端从不预测切换；点击发送一次选择并关闭，由服务端同步结果。
 */
public final class BlackRavenLedgerBookScreen extends Screen {
    public enum Tab {
        PERCEIVED,
        ABSENT
    }

    private static final String LEDGER_KEY = "screen.sparkwitch.black_raven_ledger.";
    /**
     * Wathe's coin glyph, drawn untinted. Keep the escape: a raw private-use character is invisible in editors.
     * Wathe 金币字形，保持原色绘制。保留转义：私用区字符在编辑器中不可见。
     */
    private static final String COIN_GLYPH = "\uE781";
    /** The keycap names the physical key, so it is not translated. 键帽标注的是实体按键，因此不翻译。 */
    private static final String TAB_KEY_LABEL = "Tab";
    private static final int TOOLTIP_Z = 400;

    private final int session;
    /** Client ticks since the open packet built this screen; drives the session expiry. / 自开启数据包构建本界面起的客户端刻数。 */
    private int ticksOpen;
    private Tab tab;
    private int perceivedPage;
    private int absentScroll;
    private boolean draggingRod;
    private boolean tabHeld;
    /** Wheel input, in notches, not yet spent on a page or a pixel; cleared on a tab change. 尚未生效的滚轮量（单位为格）；切换页签时清零。 */
    private double wheelCarry;
    private List<BlackRavenIdentitySnapshot> perceived = List.of();
    private List<BlackRavenDisguiseClientRules.Row> rows = List.of();
    private @Nullable BlackRavenDisguiseSyncCodec.View rowsKey;
    private Panel panel = new Panel(0, 0, 0, 0);
    private Hit hover = Hit.NONE;

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
        rowsKey = null;
        panel = BlackRavenLedgerLayout.panel(width, height);
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

    // ================================================================ input

    /**
     * Ledger keys come before vanilla's so Tab and the arrows never move widget focus; Esc still closes through
     * vanilla.
     * 感知册按键先于原版处理，Tab 与方向键不会切换控件焦点；Esc 仍由原版关闭。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            // Key repeat also arrives here: latch until release so a held Tab switches once.
            // 按键重复同样会进入此处：锁定到松开为止，按住 Tab 只切换一次。
            if (!tabHeld) {
                tabHeld = true;
                selectTab(tab == Tab.PERCEIVED ? Tab.ABSENT : Tab.PERCEIVED);
            }
            return true;
        }
        boolean handled = tab == Tab.PERCEIVED ? pageKey(keyCode) : scrollKey(keyCode);
        return handled || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            tabHeld = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /** Tab A keys: one page per press. Tab A 按键：每次翻一页。 */
    private boolean pageKey(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_LEFT -> turnPage(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_RIGHT -> turnPage(1);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Tab B keys: PageUp / PageDown scroll one view, Up / Down one wheel step. Tab B 按键：翻页键滚动一屏，上下键滚动一格。 */
    private boolean scrollKey(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> absentScroll =
                    BlackRavenLedgerLayout.page(panel, listContent(), absentScroll, -1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> absentScroll =
                    BlackRavenLedgerLayout.page(panel, listContent(), absentScroll, 1);
            case GLFW.GLFW_KEY_UP -> absentScroll = BlackRavenLedgerLayout.clampScroll(panel, listContent(),
                    absentScroll - BlackRavenLedgerLayout.SCROLL_STEP);
            case GLFW.GLFW_KEY_DOWN -> absentScroll = BlackRavenLedgerLayout.clampScroll(panel, listContent(),
                    absentScroll + BlackRavenLedgerLayout.SCROLL_STEP);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Frame frame = frame();
        Hit hit = hitAt(frame, mouseX, mouseY);
        switch (hit.kind()) {
            case TAB -> selectTab(hit.index() == 0 ? Tab.PERCEIVED : Tab.ABSENT);
            case PREV -> turnPage(-1);
            case NEXT -> turnPage(1);
            case CELL -> {
                // Tab A cells are knowledge only; a Tab B cell asks the server for that role.
                // Tab A 的单元只是知识；Tab B 的单元向服务端请求伪装为该职业。
                if (frame.body() instanceof RoleList list) {
                    select(list.entries().get(hit.index()).row());
                }
            }
            case REVERT -> {
                if (frame.body() instanceof RoleList list && list.revert() != null) {
                    select(list.revert().row());
                }
            }
            case ROD -> {
                if (frame.body() instanceof RoleList list) {
                    draggingRod = true;
                    absentScroll = BlackRavenLedgerLayout.dragScroll(panel, list.content(), mouseY);
                }
            }
            case NONE -> {
                return panel.frame().contains(mouseX, mouseY);
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingRod && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            absentScroll = BlackRavenLedgerLayout.dragScroll(panel, listContent(), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingRod && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingRod = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Trackpads send many sub-notch amounts: carry them until they add up, so a slow swipe neither stalls the
        // list nor turns a page per event. A change of direction drops what was carried.
        // 触控板会发送大量不足一格的滚动量：累计后再生效，慢速滑动既不会卡住名单，也不会每个事件翻一页。改变方向时丢弃已累计的量。
        if (verticalAmount * wheelCarry < 0) {
            wheelCarry = 0;
        }
        wheelCarry += verticalAmount;
        if (tab == Tab.ABSENT) {
            absentScroll = BlackRavenLedgerLayout.wheel(panel, listContent(), absentScroll, wheelCarry);
            wheelCarry -= Math.round(wheelCarry * BlackRavenLedgerLayout.SCROLL_STEP)
                    / (double) BlackRavenLedgerLayout.SCROLL_STEP;
        } else if (Math.abs(wheelCarry) >= 1) {
            // One page per event, whatever the scroll sensitivity. 无论滚动灵敏度如何，每个事件最多翻一页。
            turnPage(wheelCarry > 0 ? -1 : 1);
            wheelCarry = 0;
        }
        return true;
    }

    private void selectTab(Tab next) {
        if (tab != next) {
            playSound(SoundEvents.UI_BUTTON_CLICK.value());
            tab = next;
            draggingRod = false;
            wheelCarry = 0;
        }
    }

    private void turnPage(int delta) {
        int pageCount = gridPage().pageCount();
        int before = perceivedPage;
        perceivedPage = Math.clamp((long) before + delta, 0, pageCount - 1);
        if (perceivedPage != before) {
            playSound(SoundEvents.ITEM_BOOK_PAGE_TURN);
        }
    }

    /**
     * Restores the vanilla widget feedback the hand-drawn controls lost: a click for a tab change and a sent
     * selection, and the book page-turn for paging. Only played when the action actually changed something.
     * 补回手绘控件缺失的原版反馈音：切换页签与已发送的选择播放点击音，翻页播放书页声；仅在操作确实生效时播放。
     */
    private void playSound(SoundEvent sound) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F));
        }
    }

    private void refresh() {
        // A perception that completes while the ledger is open shows up on the next tick.
        // 感知册打开期间完成的感知会在下一刻显示出来。
        if (client != null && client.player != null) {
            perceived = BlackRavenLedgerScreen.perceivedSnapshots(client.player);
        }
        BlackRavenDisguiseSyncCodec.View view = currentView();
        // Countdowns tick locally every frame; rows are rebuilt only when the synced identity or pool changes.
        // 倒计时每帧本地递减；仅在同步的身份或名单变化时重建行。
        BlackRavenDisguiseSyncCodec.View key = new BlackRavenDisguiseSyncCodec.View(
                view.bound(), view.acting(), 0, 0, view.pool(), 0);
        if (!key.equals(rowsKey)) {
            rowsKey = key;
            rows = BlackRavenDisguiseClientRules.rows(view, roleOrder());
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
        playSound(SoundEvents.UI_BUTTON_CLICK.value());
        close();
    }

    // ================================================================ frame model

    /**
     * Everything one frame paints and hit-tests, measured once: paint and hit test read the same rects, so they can
     * never disagree.
     * 一帧内绘制与命中判定所需的全部内容，只测量一次：两者读取同一组矩形，因此不会不一致。
     */
    private record Frame(BlackRavenDisguiseSyncCodec.View view, Chrome chrome, Body body) {
    }

    /** The content of the tab on screen; the hidden tab is never built. 当前显示页签的内容；隐藏的页签不会被构建。 */
    private sealed interface Body {
        /**
         * Width of the control this tab puts at the footer's right end (0 = none); the status yields to it.
         * 本页签放在页脚右端的控件宽度（0 表示没有）；页脚状态为其让位。
         */
        int footerRight();
    }

    /**
     * Title band with the disguise chip, the tab row with its Tab-key hint, and the footer status.
     * 标题带与伪装徽章、页签行与 Tab 键提示、页脚状态。
     */
    private record Chrome(TitleBar bar, Text chipLabel, String chipRole, Text wallet, List<Rect> tabs,
                          List<String> tabNames, List<String> tabCounts, TabHint hint, Text hintText,
                          @Nullable Status status, Footer footer) {
    }

    /** Tab A: the grid, the cells of the page shown, and the pager. Tab A：网格、当前页的单元与翻页。 */
    private record GridPage(Grid grid, List<PerceivedCell> cells, int index, int pageCount, Pager pager,
                            Text pageText, List<OrderedText> emptyHint) implements Body {
        @Override
        public int footerRight() {
            return pager.width();
        }
    }

    /** One Tab A cell; {@code cut} = a line was ellipsized, so the cell carries a tooltip. cut 表示有文字被截断，需要提示框。 */
    private record PerceivedCell(BlackRavenIdentitySnapshot snapshot, Rect rect, String name, String role,
                                 boolean cut) {
    }

    /**
     * Tab B: the laid-out list without the revert row, its scroll, and the revert row as the footer button.
     * Tab B：不含恢复行的排版名单、滚动位置，以及作为页脚按钮的恢复行。
     */
    private record RoleList(Content content, int scroll, List<Entry> entries, @Nullable Revert revert)
            implements Body {
        @Override
        public int footerRight() {
            return revert == null ? 0 : revert.button().width();
        }
    }

    private record Entry(BlackRavenDisguiseClientRules.Row row, String name) {
    }

    /** The footer revert button; it only takes clicks and hover while {@code enabled}. 页脚恢复按钮；仅在可用时响应点击与悬停。 */
    private record Revert(BlackRavenDisguiseClientRules.Row row, Text label, Rect button, boolean enabled) {
    }

    private Frame frame() {
        BlackRavenDisguiseSyncCodec.View view = currentView();
        Body body = tab == Tab.PERCEIVED ? gridPage() : roleList(view);
        return new Frame(view, chrome(view, body.footerRight()), body);
    }

    private Chrome chrome(BlackRavenDisguiseSyncCodec.View view, int footerRight) {
        // The chip shows only while disguised: zero widths hide the role and the wallet.
        // 徽章仅在伪装时显示：宽度为 0 即隐藏职业与钱包。
        boolean disguised = view.bound() && view.disguised();
        Text chipLabel = Text.translatable(LEDGER_KEY + "chip.disguised");
        String chipRole = disguised ? BlackRavenDisguiseClientState.roleName(view.acting()).getString() : "";
        Text wallet = disguised ? Text.literal(view.ravenBalance() + COIN_GLYPH) : Text.empty();
        TitleBar bar = BlackRavenLedgerLayout.titleBar(panel, textRenderer.getWidth(title),
                textRenderer.getWidth(chipLabel), textRenderer.getWidth(chipRole), textRenderer.getWidth(wallet));

        List<String> names = List.of(Text.translatable(LEDGER_KEY + "tab.perceived").getString(),
                Text.translatable(LEDGER_KEY + "tab.absent").getString());
        List<String> counts = List.of(Integer.toString(perceived.size()), Integer.toString(selectableCount()));
        List<Rect> tabs = BlackRavenLedgerLayout.tabs(panel,
                textRenderer.getWidth(names.get(0)), textRenderer.getWidth(counts.get(0)),
                textRenderer.getWidth(names.get(1)), textRenderer.getWidth(counts.get(1)));
        Text hintText = Text.translatable(LEDGER_KEY + "hint.switch_tab");
        TabHint hint = BlackRavenLedgerLayout.tabHint(panel, tabs, textRenderer.getWidth(TAB_KEY_LABEL),
                textRenderer.getWidth(hintText));

        Status status = status(view);
        Footer footer = BlackRavenLedgerLayout.footer(panel,
                status == null ? 0 : textRenderer.getWidth(status.text()), status != null && status.total() > 0,
                footerRight);
        return new Chrome(bar, chipLabel, chipRole, wallet, tabs, names, counts, hint, hintText, status, footer);
    }

    /** Builds Tab A and keeps {@code perceivedPage} inside the page range. 构建 Tab A，并将 perceivedPage 限制在页码范围内。 */
    private GridPage gridPage() {
        // The widest role on the whole ledger picks the columns, so turning a page never reflows the grid.
        // 列数由整本感知册中最宽的职业名决定，翻页时网格不会重排。
        int widestRole = 0;
        for (BlackRavenIdentitySnapshot snapshot : perceived) {
            widestRole = Math.max(widestRole, textRenderer.getWidth(roleName(snapshot)));
        }
        Grid grid = BlackRavenLedgerLayout.perceivedGrid(panel, widestRole);
        int perPage = BlackRavenLedgerLayout.perceivedPerPage(panel, grid);
        int pageCount = BlackRavenLedgerLayout.pageCount(perceived.size(), perPage);
        perceivedPage = Math.clamp(perceivedPage, 0, pageCount - 1);
        List<BlackRavenIdentitySnapshot> page = BlackRavenDisguiseClientRules.page(perceived, perceivedPage, perPage);
        int nameMax = BlackRavenLedgerLayout.perceivedNameMax(grid.cellWidth());
        int roleMax = BlackRavenLedgerLayout.perceivedRoleMax(grid.cellWidth());
        List<PerceivedCell> cells = new ArrayList<>(page.size());
        for (int index = 0; index < page.size(); index++) {
            BlackRavenIdentitySnapshot snapshot = page.get(index);
            String fullRole = roleName(snapshot);
            String name = ellipsize(snapshot.playerName(), nameMax);
            String role = ellipsize(fullRole, roleMax);
            cells.add(new PerceivedCell(snapshot, BlackRavenLedgerLayout.perceivedCell(panel, grid, index), name, role,
                    !name.equals(snapshot.playerName()) || !role.equals(fullRole)));
        }
        Text pageText = Text.translatable(LEDGER_KEY + "page", perceivedPage + 1, pageCount);
        Pager pager = BlackRavenLedgerLayout.pager(panel, textRenderer.getWidth(pageText), pageCount);
        return new GridPage(grid, cells, perceivedPage, pageCount, pager, pageText,
                perceived.isEmpty() ? hintLines() : List.of());
    }

    /** Builds Tab B and keeps {@code absentScroll} inside the scroll range. 构建 Tab B，并将 absentScroll 限制在滚动范围内。 */
    private RoleList roleList(BlackRavenDisguiseSyncCodec.View view) {
        List<Entry> entries = new ArrayList<>(rows.size());
        List<Integer> sectionCounts = new ArrayList<>();
        BlackRavenDisguiseClientRules.Row revertRow = null;
        int widestNeed = 0;
        for (BlackRavenDisguiseClientRules.Row row : rows) {
            if (row.kind() == BlackRavenDisguiseClientRules.RowKind.REVERT) {
                revertRow = row;
                continue;
            }
            // Rows arrive grouped by flag, so a section is a run of consecutive rows sharing one flag.
            // 行已按标记分组，因此一个分区就是一段连续且标记相同的行。
            if (!entries.isEmpty() && entries.getLast().row().flag() == row.flag()) {
                sectionCounts.set(sectionCounts.size() - 1, sectionCounts.getLast() + 1);
            } else {
                sectionCounts.add(1);
            }
            String name = label(row).getString();
            entries.add(new Entry(row, name));
            widestNeed = Math.max(widestNeed,
                    BlackRavenLedgerLayout.cellNeed(textRenderer.getWidth(name), row.current() || row.visited()));
        }
        Content content = BlackRavenLedgerLayout.absentContent(panel, sectionCounts, widestNeed);
        absentScroll = BlackRavenLedgerLayout.clampScroll(panel, content, absentScroll);
        Revert revert = null;
        if (revertRow != null) {
            Text label = label(revertRow);
            revert = new Revert(revertRow, label, BlackRavenLedgerLayout.button(panel, textRenderer.getWidth(label)),
                    BlackRavenDisguiseClientRules.clickable(revertRow, session, ticksOpen, view));
        }
        return new RoleList(content, absentScroll, entries, revert);
    }

    private Content listContent() {
        return roleList(currentView()).content();
    }

    /** Hit test against the frame exactly as it is drawn. 按实际绘制的这一帧进行命中判定。 */
    private Hit hitAt(Frame frame, double mouseX, double mouseY) {
        List<Rect> tabs = frame.chrome().tabs();
        return switch (frame.body()) {
            case GridPage page -> BlackRavenLedgerLayout.hitPerceived(panel, tabs, page.pager(), page.index(),
                    page.pageCount(), page.grid(), page.cells().size(), mouseX, mouseY);
            case RoleList list -> {
                Revert revert = list.revert();
                yield BlackRavenLedgerLayout.hitAbsent(panel, tabs, list.content(), list.scroll(),
                        revert != null && revert.enabled() ? revert.button() : Rect.EMPTY, mouseX, mouseY);
            }
        };
    }

    // ================================================================ render

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // A flat scrim, no blur: the round keeps running behind the ledger. 平面遮罩，不模糊：对局仍在进行。
        context.fill(0, 0, width, height, BlackRavenLedgerPaint.SCRIM);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Frame frame = frame();
        // A rod drag owns the pointer: nothing else reacts to it until release. 拖动滑杆期间指针不触发其他悬停效果。
        hover = draggingRod ? Hit.NONE : hitAt(frame, mouseX, mouseY);

        // Every fill outside the clipped list in one batch, then faces, then text: batched vertices flush per layer,
        // not in call order. 裁剪名单以外的所有填充放入一个批次，然后绘制头像与文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            paintChrome(context, frame);
            if (frame.body() instanceof GridPage page) {
                paintPerceived(context, page);
            }
            paintFooter(context, frame);
        });
        switch (frame.body()) {
            case GridPage page -> {
                for (PerceivedCell cell : page.cells()) {
                    PlayerSkinDrawer.draw(context, skin(cell.snapshot().targetUuid()),
                            cell.rect().x() + BlackRavenLedgerLayout.FACE_X,
                            cell.rect().y() + BlackRavenLedgerLayout.FACE_Y, BlackRavenLedgerLayout.FACE_SIZE);
                }
                drawPerceivedText(context, page);
            }
            case RoleList list -> drawAbsent(context, list, absentCells(list, frame.view()));
        }
        drawChromeText(context, frame);
        drawFooterText(context, frame);
        drawTooltip(context, tooltipAt(frame, mouseX, mouseY), mouseX, mouseY);
    }

    private boolean tabHovered(int index) {
        return hover.kind() == HitKind.TAB && hover.index() == index;
    }

    private boolean cellHovered(int index) {
        return hover.kind() == HitKind.CELL && hover.index() == index;
    }

    // ---------------------------------------------------------------- chrome

    private void paintChrome(DrawContext context, Frame frame) {
        Chrome chrome = frame.chrome();
        TitleBar bar = chrome.bar();
        BlackRavenLedgerPaint.frame(context, panel.x(), panel.y(), panel.width(), panel.height());
        BlackRavenLedgerPaint.titleBand(context, panel.x(), panel.y(), panel.width());
        BlackRavenLedgerPaint.titleFeather(context, panel.x() + BlackRavenLedgerLayout.TITLE_ICON_X,
                panel.y() + BlackRavenLedgerLayout.TITLE_ICON_Y);
        if (bar.roleShown()) {
            BlackRavenLedgerPaint.roleGem(context, bar.gemX(), bar.gemY(),
                    BlackRavenDisguiseClientState.roleColor(frame.view().acting()), true);
        }
        if (bar.dividerX() >= 0) {
            BlackRavenLedgerPaint.chipDivider(context, bar.dividerX(), bar.textY());
        }
        // The tab-row rule goes first, so the active tab's glint bar covers it. 先画页签行刻线，激活页签的光泽条再盖住它。
        BlackRavenLedgerPaint.engraved(context, panel.innerLeft(), panel.innerRight(), panel.tabRuleY());
        for (int index = 0; index < chrome.tabs().size(); index++) {
            BlackRavenLedgerPaint.tab(context, chrome.tabs().get(index), index == tab.ordinal(), tabHovered(index));
        }
        if (chrome.hint().shown()) {
            BlackRavenLedgerPaint.keycap(context, chrome.hint().keycap());
        }
        BlackRavenLedgerPaint.etched(context, panel.innerLeft(), panel.innerRight(), panel.footerRuleY());
    }

    private void drawChromeText(DrawContext context, Frame frame) {
        Chrome chrome = frame.chrome();
        TitleBar bar = chrome.bar();
        context.drawText(textRenderer, ellipsize(title.getString(), bar.titleMaxWidth()), bar.titleX(), bar.textY(),
                BlackRavenLedgerPaint.SHEEN_HI, true);
        if (bar.labelShown()) {
            context.drawText(textRenderer, chrome.chipLabel(), bar.labelX(), bar.textY(), BlackRavenLedgerPaint.MUTED,
                    true);
        }
        if (bar.roleShown()) {
            context.drawText(textRenderer, ellipsize(chrome.chipRole(), bar.roleMaxWidth()), bar.roleX(), bar.textY(),
                    BlackRavenLedgerPaint.TEXT, true);
        }
        if (bar.walletShown()) {
            InventoryCardPaint.iconText(context, textRenderer, chrome.wallet(), bar.walletX(), bar.textY(),
                    BlackRavenLedgerPaint.TEXT_HI, true);
        }
        for (int index = 0; index < chrome.tabs().size(); index++) {
            Rect rect = chrome.tabs().get(index);
            boolean active = index == tab.ordinal();
            String count = chrome.tabCounts().get(index);
            String name = ellipsize(chrome.tabNames().get(index),
                    BlackRavenLedgerLayout.tabNameMax(rect, textRenderer.getWidth(count)));
            int x = rect.x() + BlackRavenLedgerLayout.TAB_PAD;
            int y = rect.y() + BlackRavenLedgerLayout.TAB_TEXT_Y;
            int nameColor = active ? BlackRavenLedgerPaint.TEXT_HI
                    : tabHovered(index) ? BlackRavenLedgerPaint.TEXT : BlackRavenLedgerPaint.MUTED;
            context.drawText(textRenderer, name, x, y, nameColor, true);
            context.drawText(textRenderer, count,
                    x + textRenderer.getWidth(name) + BlackRavenLedgerLayout.TAB_COUNT_GAP, y,
                    active ? BlackRavenLedgerPaint.GLINT : BlackRavenLedgerPaint.FAINT, true);
        }
        TabHint hint = chrome.hint();
        if (hint.shown()) {
            context.drawText(textRenderer, TAB_KEY_LABEL, hint.keyTextX(), hint.textY(), BlackRavenLedgerPaint.FAINT,
                    false);
            context.drawText(textRenderer, chrome.hintText(), hint.hintX(), hint.textY(), BlackRavenLedgerPaint.FAINT,
                    false);
        }
    }

    // ---------------------------------------------------------------- Tab A

    private void paintPerceived(DrawContext context, GridPage page) {
        if (perceived.isEmpty()) {
            BlackRavenLedgerPaint.feather(context, panel.centerX() - BlackRavenLedgerLayout.EMPTY_FEATHER_W / 2,
                    BlackRavenLedgerLayout.emptyTop(panel, 1 + page.emptyHint().size()),
                    BlackRavenLedgerPaint.SHEEN_LO, BlackRavenLedgerLayout.EMPTY_FEATHER_SCALE);
        }
        for (int index = 0; index < page.cells().size(); index++) {
            BlackRavenIdentitySnapshot snapshot = page.cells().get(index).snapshot();
            Rect rect = page.cells().get(index).rect();
            boolean hovered = cellHovered(index);
            if (hovered) {
                BlackRavenLedgerPaint.rowHover(context, rect);
            }
            BlackRavenLedgerPaint.rowRule(context, rect);
            BlackRavenLedgerPaint.faceFrame(context, rect.x() + BlackRavenLedgerLayout.FACE_FRAME_X,
                    rect.y() + BlackRavenLedgerLayout.FACE_FRAME_Y, hovered);
            BlackRavenLedgerPaint.roleGem(context, rect.x() + BlackRavenLedgerLayout.ROLE_GEM_X,
                    rect.y() + BlackRavenLedgerLayout.ROLE_GEM_Y, snapshot.roleColor(), hovered);
        }
    }

    private void drawPerceivedText(DrawContext context, GridPage page) {
        for (int index = 0; index < page.cells().size(); index++) {
            PerceivedCell cell = page.cells().get(index);
            Rect rect = cell.rect();
            context.drawText(textRenderer, cell.name(), rect.x() + BlackRavenLedgerLayout.NAME_X,
                    rect.y() + BlackRavenLedgerLayout.NAME_Y,
                    cellHovered(index) ? BlackRavenLedgerPaint.TEXT_HI : BlackRavenLedgerPaint.TEXT, true);
            context.drawText(textRenderer, cell.role(), rect.x() + BlackRavenLedgerLayout.ROLE_X,
                    rect.y() + BlackRavenLedgerLayout.ROLE_Y, BlackRavenLedgerPaint.MUTED, true);
        }
        if (perceived.isEmpty()) {
            int y = BlackRavenLedgerLayout.emptyTop(panel, 1 + page.emptyHint().size())
                    + BlackRavenLedgerLayout.EMPTY_FEATHER_H + BlackRavenLedgerLayout.EMPTY_TEXT_GAP;
            drawCentered(context, Text.translatable(LEDGER_KEY + "empty"), y, BlackRavenLedgerPaint.MUTED);
            for (OrderedText line : page.emptyHint()) {
                y += BlackRavenLedgerLayout.EMPTY_LINE_H;
                context.drawText(textRenderer, line, panel.centerX() - textRenderer.getWidth(line) / 2, y,
                        BlackRavenLedgerPaint.FAINT, true);
            }
        }
    }

    private List<OrderedText> hintLines() {
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(LEDGER_KEY + "hint.perceived"),
                panel.view().width());
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }

    private SkinTextures skin(UUID uuid) {
        ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
        PlayerListEntry entry = handler == null ? null : handler.getPlayerListEntry(uuid);
        return entry != null ? entry.getSkinTextures() : DefaultSkinHelper.getSkinTextures(uuid);
    }

    // ---------------------------------------------------------------- Tab B

    private enum Trail {
        NONE,
        CURRENT,
        PICK,
        VISITED
    }

    /** A section header inside the view; {@code live} = the selectable section. 视口内的分区标题；live 表示可选分区。 */
    private record Head(Rect rect, Text label, Text tail, boolean live) {
    }

    /**
     * A list cell inside the view. {@code hot} = clickable and hovered: a row that cannot be picked never shows a
     * hover affordance.
     * 视口内的名单单元。{@code hot} 表示可点击且被悬停；不可选择的行从不显示悬停效果。
     */
    private record AbsentCell(BlackRavenDisguiseClientRules.Row row, Rect rect, String name, boolean hot,
                              Trail trail) {
    }

    private List<Head> heads(RoleList list) {
        Rect view = panel.view();
        List<Head> heads = new ArrayList<>();
        for (Block block : list.content().blocks()) {
            Rect rect = BlackRavenLedgerLayout.header(panel, list.content(), block, list.scroll());
            if (!rect.intersects(view)) {
                continue;
            }
            BlackRavenDisguiseRules.PoolFlag flag = list.entries().get(block.firstCell()).row().flag();
            heads.add(new Head(rect, sectionLabel(flag),
                    Text.translatable(LEDGER_KEY + "section.count", block.count()),
                    flag == BlackRavenDisguiseRules.PoolFlag.SELECTABLE));
        }
        return heads;
    }

    private List<AbsentCell> absentCells(RoleList list, BlackRavenDisguiseSyncCodec.View view) {
        Rect viewport = panel.view();
        List<AbsentCell> cells = new ArrayList<>();
        for (int index = 0; index < list.entries().size(); index++) {
            Rect rect = BlackRavenLedgerLayout.absentCell(panel, list.content(), index, list.scroll());
            if (!rect.intersects(viewport)) {
                continue;
            }
            Entry entry = list.entries().get(index);
            BlackRavenDisguiseClientRules.Row row = entry.row();
            boolean clickable = BlackRavenDisguiseClientRules.clickable(row, session, ticksOpen, view);
            boolean hot = clickable && hover.kind() == HitKind.CELL && hover.index() == index;
            // The pick arrow has no reserved room: it shows only when the full name still fits beside it.
            // 选择箭头没有预留空间：仅当完整名称在其旁边仍放得下时才显示。
            Trail trail = Trail.NONE;
            if (row.current()) {
                trail = Trail.CURRENT;
            } else if (hot && textRenderer.getWidth(entry.name())
                    <= BlackRavenLedgerLayout.absentNameMax(rect.width(), true)) {
                trail = Trail.PICK;
            } else if (row.visited()) {
                trail = Trail.VISITED;
            }
            String name = ellipsize(entry.name(),
                    BlackRavenLedgerLayout.absentNameMax(rect.width(), trail != Trail.NONE));
            cells.add(new AbsentCell(row, rect, name, hot, trail));
        }
        return cells;
    }

    /**
     * The list is clipped to the view: its fills in one batch, then its text, then the scroll fades; the rod is
     * drawn after the clip.
     * 名单按视口裁剪：填充放入一个批次，然后是文字，再是滚动渐隐；滑杆在裁剪结束后绘制。
     */
    private void drawAbsent(DrawContext context, RoleList list, List<AbsentCell> cells) {
        Rect view = panel.view();
        if (list.entries().isEmpty()) {
            drawCentered(context, Text.translatable(LEDGER_KEY + "absent.empty"),
                    view.y() + (view.height() - textRenderer.fontHeight) / 2, BlackRavenLedgerPaint.MUTED);
            return;
        }
        Content content = list.content();
        int scroll = list.scroll();
        List<Head> heads = heads(list);

        context.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        InventoryCardPaint.batch(context, () -> {
            for (Head head : heads) {
                paintHead(context, head);
            }
            for (AbsentCell cell : cells) {
                paintCell(context, cell);
            }
        });
        for (Head head : heads) {
            int y = head.rect().y() + BlackRavenLedgerLayout.HEADER_LABEL_Y;
            context.drawText(textRenderer, head.label(), head.rect().x() + BlackRavenLedgerLayout.HEADER_LABEL_X, y,
                    head.live() ? BlackRavenLedgerPaint.SHEEN : BlackRavenLedgerPaint.FAINT, true);
            context.drawText(textRenderer, head.tail(), head.rect().right() - textRenderer.getWidth(head.tail()), y,
                    head.live() ? BlackRavenLedgerPaint.MUTED : BlackRavenLedgerPaint.FAINT, true);
        }
        for (AbsentCell cell : cells) {
            BlackRavenDisguiseClientRules.Row row = cell.row();
            int color = row.greyed() ? BlackRavenLedgerPaint.GREYED
                    : row.current() || cell.hot() ? BlackRavenLedgerPaint.TEXT_HI : BlackRavenLedgerPaint.TEXT;
            context.drawText(textRenderer, cell.name(), cell.rect().x() + BlackRavenLedgerLayout.CELL_TEXT_X,
                    cell.rect().y() + BlackRavenLedgerLayout.CELL_TEXT_Y, color, true);
        }
        if (content.scrolls()) {
            paintFades(context, content, scroll);
        }
        context.disableScissor();
        if (content.scrolls()) {
            boolean hot = draggingRod || hover.kind() == HitKind.ROD;
            InventoryCardPaint.batch(context, () -> BlackRavenLedgerPaint.rod(context,
                    BlackRavenLedgerLayout.track(panel), BlackRavenLedgerLayout.thumb(panel, content, scroll), hot));
        }
    }

    private void paintHead(DrawContext context, Head head) {
        Rect rect = head.rect();
        BlackRavenLedgerPaint.sectionMark(context, rect.x(), rect.y() + BlackRavenLedgerLayout.HEADER_MARK_Y,
                head.live());
        Rule rule = BlackRavenLedgerLayout.headerRule(rect, textRenderer.getWidth(head.label()),
                textRenderer.getWidth(head.tail()));
        if (rule.shown()) {
            BlackRavenLedgerPaint.sectionRule(context, rule.x1(), rule.x2(), rule.y(), head.live());
        }
    }

    private void paintCell(DrawContext context, AbsentCell cell) {
        BlackRavenDisguiseClientRules.Row row = cell.row();
        Rect rect = cell.rect();
        BlackRavenLedgerPaint.cellState(context, rect, row.current(), cell.hot());
        int gemX = rect.x() + BlackRavenLedgerLayout.CELL_GEM_X;
        int gemY = rect.y() + BlackRavenLedgerLayout.CELL_GEM_Y;
        if (row.greyed()) {
            BlackRavenLedgerPaint.emptyGem(context, gemX, gemY);
        } else {
            BlackRavenLedgerPaint.roleGem(context, gemX, gemY, BlackRavenDisguiseClientState.roleColor(row.id()),
                    cell.hot() || row.current());
        }
        int trailX = BlackRavenLedgerLayout.trailX(rect);
        int trailY = rect.y() + BlackRavenLedgerLayout.TRAIL_Y;
        switch (cell.trail()) {
            case CURRENT -> InventoryCardPaint.diamond(context, trailX, trailY, BlackRavenLedgerPaint.GLINT);
            case PICK -> InventoryCardPaint.play(context, trailX + 1, trailY, BlackRavenLedgerPaint.GLINT);
            case VISITED -> InventoryCardPaint.check(context, trailX, trailY, BlackRavenLedgerPaint.MUTED);
            case NONE -> {
            }
        }
    }

    /**
     * The scroll fades sit on their own z+1 layer: cell glyphs are drawn at +0.03, so a same-z fill would let their
     * pixels show through.
     * 滚动渐隐位于独立的 z+1 图层：单元文字位于 +0.03，同层填充会透出文字像素。
     */
    private void paintFades(DrawContext context, Content content, int scroll) {
        Rect view = panel.view();
        int right = view.x() + content.listWidth();
        int maxScroll = BlackRavenLedgerLayout.maxScroll(panel, content);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 1);
        InventoryCardPaint.batch(context, () -> {
            if (scroll > 0) {
                BlackRavenLedgerPaint.fadeTop(context, view.x(), view.y(), right);
            }
            if (scroll < maxScroll) {
                BlackRavenLedgerPaint.fadeBottom(context, view.x(), view.bottom(), right);
            }
        });
        matrices.pop();
    }

    // ---------------------------------------------------------------- footer

    /** Footer status; {@code total} 0 = no gauge. 页脚状态；total 为 0 表示无计量条。 */
    private record Status(PillKind kind, Text text, int remaining, int total) {
    }

    /** Shown on both tabs, only for a bound view. 两个页签均显示，仅在视图已绑定时。 */
    private @Nullable Status status(BlackRavenDisguiseSyncCodec.View view) {
        if (!view.bound()) {
            return null;
        }
        int seconds = BlackRavenDisguiseClientRules.statusSeconds(view);
        return switch (BlackRavenDisguiseClientRules.status(view)) {
            case LOCKED -> new Status(PillKind.LOCKED, Text.translatable(LEDGER_KEY + "footer.locked", seconds),
                    view.unlockRemaining(), BlackRavenDisguiseRules.UNLOCK_DELAY_TICKS);
            case COOLDOWN -> new Status(PillKind.COOLDOWN, Text.translatable(LEDGER_KEY + "footer.cooldown", seconds),
                    view.cooldownRemaining(), BlackRavenDisguiseRules.SWITCH_COOLDOWN_TICKS);
            case READY -> {
                if (session <= 0) {
                    yield new Status(PillKind.IDLE, Text.translatable(LEDGER_KEY + "absent.read_only"), 0, 0);
                }
                if (BlackRavenDisguiseClientRules.sessionExpired(session, ticksOpen)) {
                    yield new Status(PillKind.EXPIRED, Text.translatable(LEDGER_KEY + "footer.session_expired"), 0, 0);
                }
                yield new Status(PillKind.READY, Text.translatable(LEDGER_KEY + "footer.ready"), 0, 0);
            }
        };
    }

    private void paintFooter(DrawContext context, Frame frame) {
        Status status = frame.chrome().status();
        if (status != null) {
            Footer footer = frame.chrome().footer();
            BlackRavenLedgerPaint.pill(context, footer.pill(), status.kind(), footer.iconX(), footer.iconY());
            BlackRavenLedgerPaint.gauge(context, footer.gauge(), BlackRavenLedgerLayout.gaugeFill(
                    footer.gauge().width(), status.remaining(), status.total()));
        }
        switch (frame.body()) {
            case GridPage page -> {
                Pager pager = page.pager();
                if (pager.shown()) {
                    BlackRavenLedgerPaint.arrow(context, pager.prev(), true, page.index() > 0,
                            hover.kind() == HitKind.PREV);
                    BlackRavenLedgerPaint.arrow(context, pager.next(), false, page.index() < page.pageCount() - 1,
                            hover.kind() == HitKind.NEXT);
                }
            }
            case RoleList list -> {
                Revert revert = list.revert();
                if (revert != null) {
                    BlackRavenLedgerPaint.button(context, revert.button(), revert.enabled(),
                            hover.kind() == HitKind.REVERT);
                }
            }
        }
    }

    private void drawFooterText(DrawContext context, Frame frame) {
        Status status = frame.chrome().status();
        if (status != null) {
            Footer footer = frame.chrome().footer();
            context.drawText(textRenderer, ellipsize(status.text().getString(), footer.textMaxWidth()),
                    footer.textX(), footer.textY(), BlackRavenLedgerPaint.pillTextColor(status.kind()), false);
        }
        switch (frame.body()) {
            case GridPage page -> {
                Pager pager = page.pager();
                if (pager.shown()) {
                    context.drawText(textRenderer, page.pageText(), pager.textX(), pager.textY(),
                            BlackRavenLedgerPaint.MUTED, true);
                }
            }
            case RoleList list -> {
                Revert revert = list.revert();
                if (revert != null) {
                    Rect button = revert.button();
                    context.drawText(textRenderer, revert.label(),
                            button.x() + BlackRavenLedgerLayout.BUTTON_PAD_L + BlackRavenLedgerLayout.BUTTON_ICON_W
                                    + BlackRavenLedgerLayout.BUTTON_ICON_GAP,
                            button.y() + BlackRavenLedgerLayout.BUTTON_TEXT_Y,
                            BlackRavenLedgerPaint.buttonLabelColor(revert.enabled(), hover.kind() == HitKind.REVERT),
                            false);
                }
            }
        }
    }

    // ---------------------------------------------------------------- tooltip

    /**
     * The wallet shows its full label; a Tab A cell shows name and role only when a line was cut; Tab B cells keep
     * the row tooltip rules.
     * 钱包显示完整说明；Tab A 单元仅在文字被截断时显示名称与职业；Tab B 单元沿用行提示规则。
     */
    private @Nullable Text tooltipAt(Frame frame, int mouseX, int mouseY) {
        if (draggingRod) {
            return null;
        }
        Chrome chrome = frame.chrome();
        if (chrome.bar().wallet(textRenderer.getWidth(chrome.wallet())).contains(mouseX, mouseY)) {
            return Text.translatable(LEDGER_KEY + "footer.raven_wallet", frame.view().ravenBalance());
        }
        if (hover.kind() != HitKind.CELL) {
            return null;
        }
        return switch (frame.body()) {
            case GridPage page -> {
                PerceivedCell cell = page.cells().get(hover.index());
                BlackRavenIdentitySnapshot snapshot = cell.snapshot();
                yield cell.cut() ? Text.literal(snapshot.playerName() + " — " + roleName(snapshot)) : null;
            }
            case RoleList list -> tooltip(list.entries().get(hover.index()).row());
        };
    }

    private void drawTooltip(DrawContext context, @Nullable Text text, int mouseX, int mouseY) {
        if (text == null) {
            return;
        }
        List<OrderedText> lines = textRenderer.wrapLines(text, BlackRavenLedgerLayout.TIP_MAX_W);
        if (lines.isEmpty()) {
            return;
        }
        int tipWidth = 0;
        for (OrderedText line : lines) {
            tipWidth = Math.max(tipWidth, textRenderer.getWidth(line));
        }
        Rect box = BlackRavenLedgerLayout.tooltip(width, height, mouseX, mouseY, tipWidth,
                BlackRavenLedgerLayout.tooltipHeight(lines.size()));
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, TOOLTIP_Z);
        InventoryCardPaint.batch(context, () -> BlackRavenLedgerPaint.tooltip(context, box.x(), box.y(), box.width(),
                box.height()));
        for (int line = 0; line < lines.size(); line++) {
            context.drawText(textRenderer, lines.get(line), box.x(),
                    box.y() + line * BlackRavenLedgerLayout.TIP_LINE_H, BlackRavenLedgerPaint.TEXT, true);
        }
        matrices.pop();
    }

    // ================================================================ state helpers

    private BlackRavenDisguiseSyncCodec.View currentView() {
        return client == null ? BlackRavenDisguiseSyncCodec.View.EMPTY : BlackRavenDisguiseClientState.view(client.player);
    }

    /** Tab B count: selectable pool rows only. Tab B 计数：仅可选择的名单行。 */
    private int selectableCount() {
        int count = 0;
        for (BlackRavenDisguiseClientRules.Row row : rows) {
            if (row.kind() == BlackRavenDisguiseClientRules.RowKind.ROLE
                    && row.flag() == BlackRavenDisguiseRules.PoolFlag.SELECTABLE) {
                count++;
            }
        }
        return count;
    }

    private static List<Identifier> roleOrder() {
        List<Identifier> order = new ArrayList<>(WatheRoles.ROLES.size());
        for (Role role : WatheRoles.ROLES) {
            order.add(role.identifier());
        }
        return order;
    }

    private static String roleName(BlackRavenIdentitySnapshot snapshot) {
        return WitchRoleDisplayTexts.roleName(snapshot.roleTranslationKey()).getString();
    }

    private String ellipsize(String text, int maxWidth) {
        return InventoryCardPaint.ellipsize(textRenderer, text, Math.max(0, maxWidth));
    }

    /** One line centred in the view, ellipsized to its width. 在视口内居中的单行文字，超宽时截断。 */
    private void drawCentered(DrawContext context, Text text, int y, int color) {
        String line = ellipsize(text.getString(), panel.view().width());
        context.drawText(textRenderer, line, panel.centerX() - textRenderer.getWidth(line) / 2, y, color, true);
    }

    // ================================================================ row text

    private static Text label(BlackRavenDisguiseClientRules.Row row) {
        if (row.kind() == BlackRavenDisguiseClientRules.RowKind.REVERT) {
            return Text.translatable(LEDGER_KEY + "absent.revert");
        }
        return BlackRavenDisguiseClientState.roleName(row.id());
    }

    /** Section title: the selectable group, or the reason shared by a greyed group. 分区标题：可选分组，或灰色分组共同的原因。 */
    private static Text sectionLabel(@Nullable BlackRavenDisguiseRules.PoolFlag flag) {
        if (flag == BlackRavenDisguiseRules.PoolFlag.SELECTABLE) {
            return Text.translatable(LEDGER_KEY + "section.selectable");
        }
        return Text.translatable(LEDGER_KEY + "absent.tag." + tagSuffix(flag));
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
