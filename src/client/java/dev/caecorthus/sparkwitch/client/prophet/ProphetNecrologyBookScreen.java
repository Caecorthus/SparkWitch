package dev.caecorthus.sparkwitch.client.prophet;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathCauseGroup;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.NecrologyEntry;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Read-only two-tab Necrology book on the vanilla book texture: Names (discovery order, ✦ on a solved Prophecy) and
 * Prophecies (killer, "no killer", or the excluded causes). It reads only the owner-synced
 * {@code sparkwitch:prophet_player} component and rebuilds when that sync changes; it sends nothing to the server.
 * Every line is wrapped to the book text area (x + 36, y 32, 114 × 126) and clipped at 14 lines, so long names never
 * overflow.
 * 原版书本贴图上的只读双页签亡者名录：「名录」（按发现顺序，预言已揭晓者带 ✦）与「预言」（凶手、「无人行凶」或已排除的
 * 死因）。只读取仅同步给所有者的 {@code sparkwitch:prophet_player} 组件，并在同步变化时重建；不向服务端发送任何内容。
 * 每行都按书页文字区（x + 36、y 32、114 × 126）换行，并在 14 行处裁剪，长名字绝不溢出。
 */
public final class ProphetNecrologyBookScreen extends Screen {
    public enum Tab {
        NAMES,
        PROPHECIES
    }

    private static final String KEY = "gui.sparkwitch.prophet_necrology.";
    private static final int BOOK_SIZE = 192;
    private static final int BOOK_TOP = 2;
    private static final int TEXT_LEFT = 36;
    private static final int TEXT_TOP = 32;
    private static final int TEXT_WIDTH = 114;
    private static final int LINE_HEIGHT = 9;
    private static final int PAGE_INDICATOR_Y = 18;
    private static final int PAGE_TURN_Y = 159;
    private static final int TAB_Y = 196;
    private static final int DONE_Y = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int INK = 0x000000;
    private static final int MUTED_INK = 0x5F5F5F;
    /** Darker than {@code Formatting.GOLD} so it stays legible on the book paper. / 比 GOLD 更深，在书页纸色上依然清晰。 */
    private static final int GOLD_INK = 0xB07A00;

    private Tab tab;
    private int namesPage;
    private int propheciesPage;
    private List<List<List<OrderedText>>> namePages = List.of(List.of());
    private List<List<List<OrderedText>>> prophecyPages = List.of(List.of());
    private @Nullable List<NecrologyEntry> builtNecrology;
    private @Nullable Map<UUID, ProphecyRecord> builtProphecies;
    private @Nullable PageTurnWidget nextPage;
    private @Nullable PageTurnWidget previousPage;
    private @Nullable ButtonWidget namesTabButton;
    private @Nullable ButtonWidget propheciesTabButton;

    public ProphetNecrologyBookScreen(Tab initialTab) {
        super(Text.translatable("item.sparkwitch.prophet_necrology"));
        this.tab = initialTab;
    }

    /**
     * Opens on the Names tab after the server's empty authorization packet; the client re-checks eligibility too.
     * 收到服务端的空授权包后在「名录」页打开；客户端同样会再次检查资格。
     */
    public static void open(MinecraftClient client) {
        if (client.player == null || !isEligible(client.player)) {
            return;
        }
        client.setScreen(new ProphetNecrologyBookScreen(Tab.NAMES));
    }

    /** Living, playing, non-spectating Prophet in a running round on a confirmed server. / 已确认服务器上、进行中的对局里存活且非旁观的先知。 */
    public static boolean isEligible(@Nullable PlayerEntity player) {
        if (!SparkWitchServerConnection.isConfirmedServer() || player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.isRunning()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && ProphetRules.isProphet(game.getRole(player));
    }

    @Override
    protected void init() {
        int left = bookLeft();
        namesTabButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable(KEY + "tab.names"), button -> selectTab(Tab.NAMES))
                .dimensions(width / 2 - 100, TAB_Y, 98, BUTTON_HEIGHT)
                .build());
        propheciesTabButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable(KEY + "tab.prophecies"), button -> selectTab(Tab.PROPHECIES))
                .dimensions(width / 2 + 2, TAB_Y, 98, BUTTON_HEIGHT)
                .build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
                .dimensions(width / 2 - 100, DONE_Y, 200, BUTTON_HEIGHT)
                .build());
        nextPage = addDrawableChild(new PageTurnWidget(left + 116, PAGE_TURN_Y, true, button -> turnPage(1), true));
        previousPage = addDrawableChild(new PageTurnWidget(left + 43, PAGE_TURN_Y, false, button -> turnPage(-1), true));
        // Re-wrap after a resize. / 窗口尺寸变化后重新换行。
        builtNecrology = null;
        builtProphecies = null;
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (client == null) {
            return;
        }
        // Auto-close once the local player is no longer a living Prophet in a running round.
        // 本地玩家不再是本局存活的先知时自动关闭。
        if (client.player == null || !isEligible(client.player)) {
            close();
            return;
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
        List<List<List<OrderedText>>> pages = tab == Tab.NAMES ? namePages : prophecyPages;
        List<List<OrderedText>> page = pages.get(Math.clamp(currentPage(), 0, pages.size() - 1));
        if (page.isEmpty()) {
            Text empty = Text.translatable(KEY + (tab == Tab.NAMES ? "names.empty" : "prophecies.empty"));
            drawLines(context, left, List.of(textRenderer.wrapLines(empty, TEXT_WIDTH)), MUTED_INK);
            return;
        }
        drawLines(context, left, page, INK);
    }

    /** Draws rows top-down with a one-line gap, never past the last book line. / 自上而下绘制各行并空一行，绝不超出书页末行。 */
    private void drawLines(DrawContext context, int left, List<List<OrderedText>> rows, int color) {
        int line = 0;
        for (List<OrderedText> row : rows) {
            if (line > 0) {
                line += ProphetNecrologyPages.ROW_GAP_LINES;
            }
            for (OrderedText text : row) {
                if (line >= ProphetNecrologyPages.MAX_PAGE_LINES) {
                    return;
                }
                context.drawText(textRenderer, text, left + TEXT_LEFT, TEXT_TOP + line * LINE_HEIGHT, color, false);
                line++;
            }
        }
    }

    private void selectTab(Tab next) {
        tab = next;
        refresh();
    }

    private void turnPage(int delta) {
        if (tab == Tab.NAMES) {
            namesPage = Math.clamp((long) namesPage + delta, 0, namePages.size() - 1);
        } else {
            propheciesPage = Math.clamp((long) propheciesPage + delta, 0, prophecyPages.size() - 1);
        }
        refresh();
    }

    /** Rebuilds pages only when the owner component's synced records change. / 仅在所有者组件同步的记录变化时重建书页。 */
    private void refresh() {
        if (client != null && client.player != null) {
            ProphetPlayerComponent component = ProphetPlayerComponent.KEY.get(client.player);
            List<NecrologyEntry> necrology = component.necrology();
            Map<UUID, ProphecyRecord> prophecies = component.prophecies();
            if (!necrology.equals(builtNecrology) || !prophecies.equals(builtProphecies)) {
                builtNecrology = necrology;
                builtProphecies = prophecies;
                namePages = paginate(ProphetNecrologyPages.nameRows(necrology, prophecies).stream()
                        .map(this::wrapName)
                        .toList());
                prophecyPages = paginate(ProphetNecrologyPages.prophecyRows(prophecies).stream()
                        .map(this::wrapProphecy)
                        .toList());
            }
        }
        namesPage = Math.clamp(namesPage, 0, namePages.size() - 1);
        propheciesPage = Math.clamp(propheciesPage, 0, prophecyPages.size() - 1);
        int page = currentPage();
        int count = pageCount();
        if (nextPage != null) {
            nextPage.visible = page < count - 1;
        }
        if (previousPage != null) {
            previousPage.visible = page > 0;
        }
        if (namesTabButton != null) {
            namesTabButton.active = tab != Tab.NAMES;
        }
        if (propheciesTabButton != null) {
            propheciesTabButton.active = tab != Tab.PROPHECIES;
        }
    }

    private static List<List<List<OrderedText>>> paginate(List<List<OrderedText>> rows) {
        return ProphetNecrologyPages.paginate(
                rows, List::size, ProphetNecrologyPages.MAX_PAGE_LINES, ProphetNecrologyPages.ROW_GAP_LINES);
    }

    private List<OrderedText> wrapName(ProphetNecrologyPages.NameRow row) {
        MutableText text = Text.translatable(KEY + "names.entry", row.number(), row.name());
        if (row.solved()) {
            text.append(Text.literal(" "))
                    .append(Text.translatable(KEY + "names.solved").styled(style -> style.withColor(GOLD_INK)));
        }
        return wrap(text);
    }

    private List<OrderedText> wrapProphecy(ProphetNecrologyPages.ProphecyRow row) {
        List<OrderedText> lines = new ArrayList<>(wrap(Text.literal(row.victimName())));
        Text outcome = switch (row.outcome()) {
            case REVEALED_KILLER -> Text.translatable(KEY + "prophecy.killer",
                            row.killerName() == null ? "?" : row.killerName())
                    .styled(style -> style.withColor(GOLD_INK));
            case NO_KILLER -> Text.translatable(KEY + "prophecy.no_killer")
                    .styled(style -> style.withColor(GOLD_INK));
            case PENDING -> row.excluded().isEmpty()
                    ? Text.translatable(KEY + "prophecy.pending").styled(style -> style.withColor(MUTED_INK))
                    : Text.translatable(KEY + "prophecy.excluded", excludedCauses(row.excluded()))
                            .styled(style -> style.withColor(MUTED_INK));
        };
        lines.addAll(wrap(outcome));
        return List.copyOf(lines);
    }

    private static Text excludedCauses(List<ProphetDeathCauseGroup> groups) {
        List<Text> names = new ArrayList<>(groups.size());
        for (ProphetDeathCauseGroup group : groups) {
            names.add(Text.translatable(group.translationKey()));
        }
        return Texts.join(names, Text.translatable(KEY + "separator"));
    }

    private List<OrderedText> wrap(Text text) {
        return List.copyOf(textRenderer.wrapLines(text, TEXT_WIDTH));
    }

    private int currentPage() {
        return tab == Tab.NAMES ? namesPage : propheciesPage;
    }

    private int pageCount() {
        return Math.max(1, (tab == Tab.NAMES ? namePages : prophecyPages).size());
    }

    private int bookLeft() {
        return (width - BOOK_SIZE) / 2;
    }
}
