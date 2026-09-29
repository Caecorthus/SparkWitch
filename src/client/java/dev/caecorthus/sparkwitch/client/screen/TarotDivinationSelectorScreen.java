package dev.caecorthus.sparkwitch.client.screen;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.hud.TarotDivinationHudRenderer;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries.Entry;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries.Group;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Hit;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Move;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Panel;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Rect;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.SectionSize;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotReadingLog;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.net.SubmitTarotDivinationSelectionC2SPacket;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Tarot Reader's paid divination selector, drawn as a Harpy Express ledger by {@link TarotLedgerRenderer}. One
 * purchase allows exactly one submission: a click selects a row; a second click on it, Enter or the Divine button
 * sends the single {@link SubmitTarotDivinationSelectionC2SPacket} and closes. A row selected only because typing
 * narrowed the list to it takes one click to confirm before a click submits. Closing any other way (Esc on an empty
 * search, or the auto-close once the player is no longer the live Tarot Reader) forfeits the purchase without a refund.
 * Leak guard: rows are grouped from static registry data only, and the only per-row marks are the self tag and the
 * stamps from this reader's own {@link TarotReadingLog}. No synced role, death or assignment state of any player is
 * read to filter, tint, sort or mark a row.
 * 塔罗牌师的付费占卜选择界面，由 {@link TarotLedgerRenderer} 以哈比特快账簿样式绘制。每次购买只能提交一次：单击
 * 选中一行，再次单击该行、按回车或点击“占卜”按钮才会发送唯一的 {@link SubmitTarotDivinationSelectionC2SPacket}
 * 并关闭界面。仅因输入缩小到唯一匹配而自动选中的行，需先单击确认一次，之后的单击才会提交。以其他方式关闭（搜索为空时
 * 按 Esc，或玩家不再是存活的塔罗牌师时自动关闭）即放弃本次购买，不退款。防泄露：各行只按静态注册数据分组，唯一的逐行
 * 标记是“你”标签与本人 {@link TarotReadingLog} 中的印记；从不读取任何玩家同步的职业、死亡或分配状态来筛选、着色、
 * 排序或标记任何一行。
 */
public final class TarotDivinationSelectorScreen extends Screen {
    private static final int FIELD_H = 9;
    private static final int MAX_QUERY_LENGTH = 64;

    private final int mode;
    private final TarotLedgerLayout.Mode ledgerMode;
    private final List<UUID> playerIds;
    private final List<String> playerNames;

    // Survives init() (resize, GUI scale). 在 init() 之间保留（窗口尺寸、界面缩放变化）。
    private String query = "";
    private @Nullable String selectedTarget;
    /**
     * True when the selection came from a click or the arrow keys, so a click on it submits; false when typing
     * selected the only match.
     * 选中项来自单击或方向键时为 true，此时单击它即提交；由输入自动选中唯一匹配时为 false。
     */
    private boolean armed;
    private int scroll;
    private boolean submitted;
    private boolean draggingRod;
    /**
     * Set by the Esc press that cleared the query until that key is released. Vanilla also sends key repeats to
     * {@code keyPressed}, so without this latch holding Esc would clear the query and then close (forfeit).
     * 由清空查询的那次 Esc 设置，直到该键松开。原版会把按键重复也发给 {@code keyPressed}，没有此锁存时按住 Esc
     * 会先清空查询、随即关闭界面（即放弃）。
     */
    private boolean escapeHeld;

    // Rebuilt by init(). 由 init() 重建。
    private List<Group> groups = List.of();
    private int totalCount;
    private TarotLedgerLayout.Grid grid;
    private Panel panel;
    private TarotLedgerRenderer ledger;
    private TextFieldWidget searchField;

    // Rebuilt on every filter change; hover is refreshed every frame.
    // 每次筛选变化时重建；悬停状态每帧刷新。
    private List<Entry> cells = List.of();
    private TarotLedgerLayout.Content content;
    private Hit hover = Hit.NONE;

    public TarotDivinationSelectorScreen(int mode, List<UUID> playerIds, List<String> playerNames) {
        super(Text.translatable(identity(mode)
                ? "screen.sparkwitch.tarot.identity.title"
                : "screen.sparkwitch.tarot.survival.title"));
        this.mode = mode;
        this.ledgerMode = identity(mode) ? TarotLedgerLayout.Mode.IDENTITY : TarotLedgerLayout.Mode.SURVIVAL;
        this.playerIds = List.copyOf(playerIds);
        this.playerNames = List.copyOf(playerNames);
    }

    @Override
    protected void init() {
        super.init();
        String language = client.getLanguageManager().getLanguage();
        UUID self = client.player == null ? null : client.player.getUuid();
        List<Entry> entries = ledgerMode == TarotLedgerLayout.Mode.IDENTITY
                ? TarotLedgerEntries.identity()
                : TarotLedgerEntries.survival(playerIds, playerNames, self);
        groups = TarotLedgerEntries.group(entries, TarotLedgerEntries.labelOrder(language));
        totalCount = entries.size();

        // Columns and panel height come from the UNFILTERED list, so typing never reflows or resizes the ledger.
        // 列数与面板高度取自未筛选的完整列表，输入搜索时账簿不会重排或改变尺寸。
        int widest = 0;
        for (Entry entry : entries) {
            widest = Math.max(widest, textRenderer.getWidth(entry.label()));
        }
        grid = TarotLedgerLayout.grid(width, ledgerMode, widest);
        int fullHeight = TarotLedgerLayout.content(sizes(groups), grid).height();
        Rect hudBacking = TarotDivinationHudRenderer.backingBounds(width).map(Rect::ofHudBacking).orElse(null);
        panel = TarotLedgerLayout.panel(width, height, ledgerMode, fullHeight, hudBacking);
        ledger = new TarotLedgerRenderer(textRenderer, title, mode, panel, totalCount);

        // The well, placeholder and match count are ours; vanilla keeps the caret, selection and IME input.
        // 凹槽、占位文字与匹配计数由本界面绘制；光标、选区与输入法输入保留原版行为。
        int countReserve = textRenderer.getWidth(totalCount + "/" + totalCount);
        searchField = new TextFieldWidget(textRenderer, panel.fieldX(), panel.fieldY(),
                panel.fieldWidth(countReserve), FIELD_H, TarotLedgerText.searchPrompt(identity(mode)));
        searchField.setMaxLength(MAX_QUERY_LENGTH);
        searchField.setDrawsBackground(false);
        searchField.setEditableColor(InventoryCardPaint.TEXT_HI);
        searchField.setText(query);
        searchField.setChangedListener(this::onQueryChanged);
        addDrawableChild(searchField);
        setInitialFocus(searchField);
        refilter(false);
    }

    /**
     * Mirrors the server's {@code canDivine} for the local player only, so a reader who dies, leaves the role or
     * outlives the round loses the screen. Nothing is sent: the purchase is forfeited like any other close.
     * 仅针对本地玩家镜像服务端的 {@code canDivine}，占卜者死亡、失去职业或对局结束时关闭界面。不发送任何数据，
     * 与其他关闭方式一样视为放弃本次购买。
     */
    @Override
    public void tick() {
        if (client != null && !submitted && !isLiveTarotReader(client)) {
            close();
        }
    }

    /**
     * Everything but the search field and the tooltip. {@code Screen.render} calls this exactly once per frame, so
     * the scrim is drawn once and the field (a child) renders above the ledger.
     * 除搜索框与提示框外的全部内容。{@code Screen.render} 每帧只调用一次，因此遮罩只绘制一次，搜索框（子元素）位于账簿之上。
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, TarotLedgerPaint.LEDGER_SCRIM);
        if (hudRedrawn()) {
            // The divination table again above the scrim, so its counts stay readable while picking (Wathe's
            // LimitedInventoryScreen redraws its money row the same way).
            // 在遮罩上方再次绘制占卜表，选择时仍可读取人数（Wathe 的 LimitedInventoryScreen 以同样方式重绘金币行）。
            TarotDivinationHudRenderer.render(context);
        }
        hover = draggingRod ? Hit.NONE : hitAt(mouseX, mouseY);
        ledger.draw(context, view());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ledger.drawTooltip(context, view());
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!panel.frame().contains(mouseX, mouseY)) {
            // A stray click outside never closes, so it can never forfeit the purchase. 面板外的点击不做任何事。
            return false;
        }
        if (submitted || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        Hit hit = hitAt(mouseX, mouseY);
        switch (hit.kind()) {
            case BUTTON -> submit();
            case CLEAR -> searchField.setText("");
            case TAB -> scroll = TarotLedgerLayout.tabScroll(panel, content, hit.section(), scroll);
            case GUTTER -> {
                // The gutter only scrolls; it is tested before cells, so it can never spend the reading.
                // 滚动槽只负责滚动，且先于单元判定，绝不会花掉本次占卜。
                if (TarotLedgerLayout.maxScroll(panel, content) > 0) {
                    draggingRod = true;
                    scroll = TarotLedgerLayout.dragScroll(panel, content, mouseY);
                }
            }
            case CELL -> clickCell(hit.cell());
            case NONE -> {
                if (panel.well().contains(mouseX, mouseY)) {
                    focusSearch();
                    searchField.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
        if (!submitted) {
            // Typing always filters, whatever was clicked. 无论点击何处，输入始终用于筛选。
            focusSearch();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingRod && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scroll = TarotLedgerLayout.dragScroll(panel, content, mouseY);
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
        if (!panel.frame().contains(mouseX, mouseY)) {
            return false;
        }
        scroll = TarotLedgerLayout.wheel(panel, content, scroll, verticalAmount);
        return true;
    }

    /**
     * Esc is two-stage and handled before vanilla's close path: a query is cleared first, and only an empty search
     * closes (forfeiting). Every other key ends in the search field, so E and other letters are text, never "close".
     * Esc 分两步，并在原版关闭流程之前处理：先清空查询，查询为空时才关闭（即放弃）。其余按键最终都交给搜索框，因此 E 等
     * 字母只是文字，绝不会关闭界面。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (submitted) {
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (escapeHeld) {
                    return true;
                }
                if (query.isEmpty()) {
                    close();
                } else {
                    escapeHeld = true;
                    searchField.setText("");
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                submit();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveSelection(Move.UP);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveSelection(Move.DOWN);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                if (query.isEmpty()) {
                    moveSelection(keyCode == GLFW.GLFW_KEY_LEFT ? Move.LEFT : Move.RIGHT);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll = TarotLedgerLayout.page(panel, content, scroll, keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> {
                scroll = keyCode == GLFW.GLFW_KEY_HOME ? 0 : TarotLedgerLayout.maxScroll(panel, content);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                // No focus navigation: the search field keeps the keyboard. 不切换焦点，键盘始终留在搜索框。
                return true;
            }
            default -> {
            }
        }
        focusSearch();
        searchField.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            escapeHeld = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (submitted) {
            return true;
        }
        focusSearch();
        return searchField.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(null);
        }
    }

    // ================================================================ state

    private void onQueryChanged(String value) {
        query = value;
        scroll = 0;
        refilter(true);
    }

    /**
     * Rebuilds the visible cells. The selection stays while it still matches; typing down to exactly one match
     * selects it unarmed, so "type, then Enter" divines while a stray click on it only confirms it.
     * 重建可见单元。选中项在仍匹配时保留；输入到只剩一个匹配时自动选中但不确认，“输入后回车”即可占卜，
     * 而误点该行只会确认它。
     */
    private void refilter(boolean typed) {
        List<Group> shown = TarotLedgerEntries.filter(groups, query);
        List<Entry> flat = new ArrayList<>();
        for (Group group : shown) {
            flat.addAll(group.entries());
        }
        cells = List.copyOf(flat);
        content = TarotLedgerLayout.content(sizes(shown), grid);
        if (typed && !query.isBlank() && cells.size() == 1) {
            String only = cells.get(0).target();
            if (!only.equals(selectedTarget)) {
                selectedTarget = only;
                armed = false;
            }
        } else if (indexOf(selectedTarget) < 0) {
            selectedTarget = null;
            armed = false;
        }
        scroll = TarotLedgerLayout.clampScroll(panel, content, scroll);
    }

    /** Only a click on the armed selection submits; any other click selects and arms. 仅单击已确认的选中项才提交。 */
    private void clickCell(int cell) {
        String target = cells.get(cell).target();
        if (armed && target.equals(selectedTarget)) {
            submit();
        } else {
            selectedTarget = target;
            armed = true;
        }
    }

    private void moveSelection(Move move) {
        int next = TarotLedgerLayout.move(content, indexOf(selectedTarget), move);
        if (next >= 0) {
            selectedTarget = cells.get(next).target();
            armed = true;
            scroll = TarotLedgerLayout.reveal(panel, content, next, scroll);
        }
    }

    /** The one paid submission of this session. 本次会话唯一的付费提交。 */
    private void submit() {
        Entry entry = selectedEntry();
        if (submitted || entry == null) {
            return;
        }
        submitted = true;
        ClientPlayNetworking.send(new SubmitTarotDivinationSelectionC2SPacket(mode, entry.target()));
        close();
    }

    private void focusSearch() {
        if (getFocused() != searchField || !searchField.isFocused()) {
            setFocused(searchField);
        }
    }

    private int indexOf(@Nullable String target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).target().equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private @Nullable Entry selectedEntry() {
        int index = indexOf(selectedTarget);
        return index < 0 ? null : cells.get(index);
    }

    /** Hit test against the footer exactly as it is drawn. 按实际绘制的页脚进行命中判定。 */
    private Hit hitAt(double mouseX, double mouseY) {
        return TarotLedgerLayout.hit(panel, content, ledger.footer(selectedEntry()), scroll, !query.isEmpty(),
                mouseX, mouseY);
    }

    private TarotLedgerRenderer.View view() {
        return new TarotLedgerRenderer.View(content, cells, query, selectedEntry(), armed, hover, scroll,
                draggingRod, submitted, searchField.isFocused());
    }

    /**
     * Decided every frame from the table's current backing, so a snapshot that arrives while the selector is open
     * still shows; only the panel position is fixed by init(). The table is redrawn only where it cannot touch the
     * panel: its backing, with {@link TarotLedgerLayout#HUD_AIR} of air, must clear the frame and its shadow.
     * 每帧根据表格当前的底板判定，因此界面打开期间收到的快照也会显示；只有面板位置由 init() 固定。仅当表格
     * 不会碰到面板时才重绘：其底板（含 {@link TarotLedgerLayout#HUD_AIR} 空隙）必须避开外框及其投影。
     */
    private boolean hudRedrawn() {
        if (client == null || client.options.hudHidden) {
            return false;
        }
        return TarotDivinationHudRenderer.backingBounds(width).map(Rect::ofHudBacking)
                .filter(backing -> TarotLedgerLayout.clearOfHud(panel.frame(), backing)).isPresent();
    }

    private static List<SectionSize> sizes(List<Group> groups) {
        List<SectionSize> sizes = new ArrayList<>(groups.size());
        for (Group group : groups) {
            sizes.add(new SectionSize(group.section(), group.entries().size()));
        }
        return sizes;
    }

    private static boolean identity(int mode) {
        return mode == OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY;
    }

    /**
     * Client mirror of the server's {@code canDivine}, reading only the local player's own state.
     * 服务端 {@code canDivine} 的客户端镜像，只读取本地玩家自身的状态。
     */
    private static boolean isLiveTarotReader(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        UUID uuid = player.getUuid();
        Role role = game.getRole(player);
        return game.isRunning()
                && game.hasAnyRole(uuid)
                && !game.isPlayerDead(uuid)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && role != null
                && SparkWitchRoles.TAROT_READER_ID.equals(role.identifier());
    }
}
