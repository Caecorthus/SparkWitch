package dev.caecorthus.sparkwitch.client.criminalreasoner;

import dev.caecorthus.sparkwitch.net.OpenCriminalReasonerS2CPacket;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 犯罪推理菜单, laid out like NoellesRoles' original: pick a victim from a grid of avatar tiles first, then pick a
 * suspect from the alive/dead sections. One action row at the bottom.
 * 犯罪推理菜单，按 NoellesRoles 原版的布局：
 * 先在一屏头像格子里选死者，再在「存活玩家 / 死亡玩家」两节里选凶手，底部一行放按钮。
 */
public final class CriminalReasonerScreen extends Screen {

    private static final int COLUMNS = 6;
    private static final int CELL_WIDTH = 36;
    private static final int CELL_HEIGHT = 45;
    private static final int ICON = CriminalReasonerPlayerButton.SIZE;
    private static final int TITLE_Y = 20;
    private static final int SUBTITLE_Y = 38;
    private static final int GRID_TOP = 58;
    private static final int FOOTER = 34;
    /**
     * Room above the footer for the suspect page's {@code current_pair} line (drawn at {@code height - 46}), so the
     * grid never runs under it.
     * 嫌疑人页 {@code current_pair} 行（绘制于 {@code height - 46}）在底栏上方占用的空间，网格不会伸到它下面。
     */
    private static final int SUMMARY_SPACE = 16;
    /** A tile's drawn height: the avatar plus the name under it. / 一个格子的绘制高度：头像加下方名字。 */
    private static final int TILE_HEIGHT = ICON + 12;
    /** Label row plus the gap to the grid under it. / 小节标题行及其与下方网格的间距。 */
    private static final int SECTION_LABEL_SPACE = 20;
    private static final int SECTION_GAP = 6;
    /**
     * The server keeps a roster session for {@link CriminalReasonerRules#MENU_SESSION_TICKS} and silently drops a later
     * confirm, so the menu closes one second before that; reopening fetches a fresh roster.
     * 服务端只保留名单会话 {@link CriminalReasonerRules#MENU_SESSION_TICKS}，之后的确认会被静默丢弃，因此菜单提前一秒关闭；
     * 重新打开会取得新名单。
     */
    private static final int CLOSE_AFTER_TICKS = CriminalReasonerRules.MENU_SESSION_TICKS - 20;
    private static final double SCROLL_STEP = 24.0;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    private final OpenCriminalReasonerS2CPacket roster;
    private final List<CriminalReasonerPlayerButton> buttons = new ArrayList<>();
    private final List<SectionLabel> sectionLabels = new ArrayList<>();

    private UUID selectedVictim;
    private UUID selectedSuspect;
    private int scroll;
    private int maxScroll;
    private int viewBottom;
    private int openTicks;

    public CriminalReasonerScreen(OpenCriminalReasonerS2CPacket roster) {
        super(Text.translatable("screen.sparkwitch.criminal_reasoner.title"));
        this.roster = roster;
    }

    @Override
    protected void init() {
        buttons.clear();
        sectionLabels.clear();
        boolean selectingVictim = selectedVictim == null;
        viewBottom = height - FOOTER - (selectingVictim ? 0 : SUMMARY_SPACE);
        int centerX = width / 2;

        // Footer buttons are added before the tiles, so they come first for clicks and focus.
        // 底栏按钮先于格子添加，因此点击与焦点都优先落在按钮上。
        if (selectingVictim) {
            addDrawableChild(ButtonWidget.builder(
                            Text.translatable("screen.sparkwitch.criminal_reasoner.cancel"),
                            b -> close())
                    .dimensions(centerX - BUTTON_WIDTH / 2, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            initVictimGrid(centerX);
            return;
        }

        ButtonWidget confirm = ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.criminal_reasoner.confirm"),
                        b -> confirm())
                .dimensions(centerX + 4, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        confirm.active = selectedSuspect != null;
        addDrawableChild(confirm);
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.criminal_reasoner.back"),
                        b -> {
                            selectedVictim = null;
                            selectedSuspect = null;
                            scroll = 0;
                            clearAndInit();
                        })
                .dimensions(centerX - 4 - BUTTON_WIDTH, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        initSuspectSections(centerX);
    }

    private void initVictimGrid(int centerX) {
        List<UUID> victims = roster.victimUuids();
        List<String> names = roster.victimNames();
        int rows = (victims.size() + COLUMNS - 1) / COLUMNS;
        clampScroll(gridHeight(rows));
        int contentTop = GRID_TOP - scroll;
        int columns = Math.min(COLUMNS, Math.max(1, victims.size()));
        int startX = centerX - columns * CELL_WIDTH / 2;
        for (int index = 0; index < victims.size(); index++) {
            int row = index / COLUMNS;
            int column = index % COLUMNS;
            UUID uuid = victims.get(index);
            addTile(startX + column * CELL_WIDTH, contentTop + row * CELL_HEIGHT,
                    uuid, names.get(index), uuid.equals(selectedVictim), () -> {
                        selectedVictim = uuid;
                        selectedSuspect = null;
                        // A new page starts at its top, like Back. / 新页面从顶部开始，与“返回”一致。
                        scroll = 0;
                        clearAndInit();
                    });
        }
    }

    private void initSuspectSections(int centerX) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            clampScroll(0);
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        List<UUID> alive = new ArrayList<>();
        List<String> aliveNames = new ArrayList<>();
        List<UUID> dead = new ArrayList<>();
        List<String> deadNames = new ArrayList<>();
        for (int index = 0; index < roster.suspectUuids().size(); index++) {
            UUID uuid = roster.suspectUuids().get(index);
            if (game.isPlayerDead(uuid)) {
                dead.add(uuid);
                deadNames.add(roster.suspectNames().get(index));
            } else {
                alive.add(uuid);
                aliveNames.add(roster.suspectNames().get(index));
            }
        }
        int aliveRows = Math.max(1, (alive.size() + COLUMNS - 1) / COLUMNS);
        int deadRows = Math.max(1, (dead.size() + COLUMNS - 1) / COLUMNS);
        // Same offsets as the layout below: two labels, one gap, both grids.
        // 与下方布局相同的偏移：两个标题、一个间距、两组网格。
        clampScroll(SECTION_LABEL_SPACE + aliveRows * CELL_HEIGHT + SECTION_GAP
                + SECTION_LABEL_SPACE + gridHeight(deadRows));

        int y = GRID_TOP - scroll;
        addSectionLabel(centerX, y, Text.translatable("screen.sparkwitch.criminal_reasoner.section_alive"));
        y += SECTION_LABEL_SPACE;
        addGrid(alive, aliveNames, centerX, y);
        y += aliveRows * CELL_HEIGHT + SECTION_GAP;
        addSectionLabel(centerX, y, Text.translatable("screen.sparkwitch.criminal_reasoner.section_dead"));
        y += SECTION_LABEL_SPACE;
        addGrid(dead, deadNames, centerX, y);
    }

    private void addGrid(List<UUID> uuids, List<String> names, int centerX, int top) {
        int columns = Math.min(COLUMNS, Math.max(1, uuids.size()));
        int startX = centerX - columns * CELL_WIDTH / 2;
        for (int index = 0; index < uuids.size(); index++) {
            int row = index / COLUMNS;
            int column = index % COLUMNS;
            UUID uuid = uuids.get(index);
            addTile(startX + column * CELL_WIDTH, top + row * CELL_HEIGHT,
                    uuid, names.get(index), uuid.equals(selectedSuspect), () -> {
                        selectedSuspect = uuid;
                        clearAndInit();
                    });
        }
    }

    /** Height of a grid of {@code rows} rows, down to the last name. / {@code rows} 行网格的高度，到最后一行名字为止。 */
    private static int gridHeight(int rows) {
        return rows <= 0 ? 0 : (rows - 1) * CELL_HEIGHT + TILE_HEIGHT;
    }

    /** Sets {@link #maxScroll} for this page's content and clamps {@link #scroll}. / 按本页内容设置最大滚动并夹住滚动值。 */
    private void clampScroll(int contentHeight) {
        maxScroll = Math.max(0, contentHeight - (viewBottom - GRID_TOP));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private boolean inView(int top, int extent) {
        return top >= GRID_TOP && top + extent <= viewBottom;
    }

    private void addTile(int x, int y, UUID uuid, String name, boolean selected, Runnable onPress) {
        CriminalReasonerPlayerButton button = new CriminalReasonerPlayerButton(
                x, y, uuid, name, b -> onPress.run());
        button.setSelected(selected);
        // A tile scrolled even partly out of the grid area is hidden, so it neither draws over the title or footer
        // nor takes a click (a hidden widget ignores clicks and focus).
        // 只要部分滚出网格区域的格子就隐藏，既不会画到标题或底栏上，也不会接收点击（隐藏的控件不响应点击和焦点）。
        button.visible = inView(y, TILE_HEIGHT);
        button.active = button.visible;
        buttons.add(button);
        addDrawableChild(button);
    }

    private void addSectionLabel(int centerX, int y, Text text) {
        sectionLabels.add(new SectionLabel(text, y, centerX));
    }

    private void confirm() {
        if (client == null || selectedVictim == null || selectedSuspect == null) {
            return;
        }
        CriminalReasonerClientModule.confirm(client, roster.sessionId(), selectedVictim, selectedSuspect);
        close();
    }

    @Override
    public void tick() {
        // Close in every state in which the server would refuse the confirm anyway (the reasoning gate, see
        // CriminalReasonerClientModule.isActiveReasoner), and before the server's roster session runs out.
        // 凡是服务端无论如何都会拒绝确认的状态都会关闭（推理门槛，见 CriminalReasonerClientModule.isActiveReasoner），
        // 并在服务端名单会话过期之前关闭。
        openTicks++;
        if (client == null || openTicks >= CLOSE_AFTER_TICKS
                || !CriminalReasonerClientModule.isActiveReasoner(client)) {
            close();
        }
    }

    /**
     * Vanilla {@code Screen.render} calls this once before the widgets: the dark backing and the role accent down the
     * left edge, as in the source menu. No blur, and the round keeps running behind the menu (JudgeSelectionScreen).
     * 原版 {@code Screen.render} 在绘制控件前只调用本方法一次：深色底与左缘身份色强调条，与原版菜单一致。不做模糊，
     * 菜单后方的回合照常进行（参照 JudgeSelectionScreen）。
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);
        context.fill(0, 0, 4, height, 0xFF000000 | CriminalReasonerRules.COLOR);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background, then the children (avatar tiles, buttons): everything drawn afterwards sits above them.
        // 先画背景，再画子控件（头像格子、按钮）；之后再画的内容都在它们之上。
        super.render(context, mouseX, mouseY, delta);

        boolean selectingVictim = selectedVictim == null;
        context.drawCenteredTextWithShadow(textRenderer,
                selectingVictim
                        ? Text.translatable("screen.sparkwitch.criminal_reasoner.title")
                        : Text.translatable("screen.sparkwitch.criminal_reasoner.title_select_suspect",
                                nameOf(selectedVictim)),
                width / 2, TITLE_Y, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable(selectingVictim
                        ? "screen.sparkwitch.criminal_reasoner.subtitle_select_victim"
                        : "screen.sparkwitch.criminal_reasoner.subtitle_select_suspect")
                        .formatted(Formatting.GRAY),
                width / 2, SUBTITLE_Y, 0xA0A0A0);

        if (selectingVictim && roster.victimUuids().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("screen.sparkwitch.criminal_reasoner.empty_victims")
                            .formatted(Formatting.GRAY),
                    width / 2, height / 2, 0x808080);
        }

        for (SectionLabel label : sectionLabels) {
            if (inView(label.y(), textRenderer.fontHeight)) {
                context.drawCenteredTextWithShadow(textRenderer, label.text(),
                        label.centerX(), label.y(), 0xA0A0A0);
            }
        }

        drawTileNames(context);

        if (!selectingVictim && selectedSuspect != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("screen.sparkwitch.criminal_reasoner.current_pair",
                            nameOf(selectedVictim), nameOf(selectedSuspect)),
                    width / 2, height - 46, 0xE0E0E0);
        }
    }

    /** Names under each avatar, trimmed to the cell. 头像下方的名字，按单元格宽度截断。 */
    private void drawTileNames(DrawContext context) {
        for (CriminalReasonerPlayerButton button : buttons) {
            if (!button.visible) {
                continue;
            }
            int nameY = button.getY() + ICON + 2;
            String name = button.getMessage().getString();
            String clipped = textRenderer.getWidth(name) > CELL_WIDTH - 4
                    ? textRenderer.trimToWidth(name, CELL_WIDTH - 8) + "…"
                    : name;
            // Hard clip to the cell so a long name can never run over its neighbour.
            // 硬裁到单元格内，长名字绝不会盖到旁边的格子。
            int clipLeft = button.getX() - (CELL_WIDTH - ICON) / 2;
            context.enableScissor(clipLeft, nameY - 1, clipLeft + CELL_WIDTH, nameY + 10);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(clipped),
                    button.getX() + ICON / 2, nameY, 0xFFFFFFFF);
            context.disableScissor();
        }
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        int index = roster.suspectUuids().indexOf(uuid);
        if (index >= 0) {
            return roster.suspectNames().get(index);
        }
        index = roster.victimUuids().indexOf(uuid);
        return index >= 0
                ? roster.victimNames().get(index)
                : Text.translatable("screen.sparkwitch.criminal_reasoner.unknown_player").getString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            int next = scroll - (int) Math.round(verticalAmount * SCROLL_STEP);
            next = Math.max(0, Math.min(next, maxScroll));
            if (next != scroll) {
                scroll = next;
                clearAndInit();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record SectionLabel(Text text, int y, int centerX) {
    }
}
