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

    public CriminalReasonerScreen(OpenCriminalReasonerS2CPacket roster) {
        super(Text.translatable("screen.sparkwitch.criminal_reasoner.title"));
        this.roster = roster;
    }

    @Override
    protected void init() {
        buttons.clear();
        sectionLabels.clear();
        viewBottom = height - FOOTER;
        int contentTop = GRID_TOP - scroll;
        int centerX = width / 2;

        if (selectedVictim == null) {
            initVictimGrid(centerX, contentTop);
            addDrawableChild(ButtonWidget.builder(
                            Text.translatable("screen.sparkwitch.criminal_reasoner.cancel"),
                            b -> close())
                    .dimensions(centerX - BUTTON_WIDTH / 2, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            return;
        }

        initSuspectSections(centerX);
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
    }

    private void initVictimGrid(int centerX, int contentTop) {
        List<UUID> victims = roster.victimUuids();
        List<String> names = roster.victimNames();
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
                        clearAndInit();
                    });
        }
    }

    private void initSuspectSections(int centerX) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
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
        int contentHeight = aliveRows * CELL_HEIGHT + 26 + deadRows * CELL_HEIGHT + 26;
        maxScroll = Math.max(0, contentHeight - (viewBottom - GRID_TOP));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int y = GRID_TOP - scroll;
        addSectionLabel(centerX, y, Text.translatable("screen.sparkwitch.criminal_reasoner.section_alive"));
        y += 20;
        addGrid(alive, aliveNames, centerX, y);
        y += aliveRows * CELL_HEIGHT + 6;
        addSectionLabel(centerX, y, Text.translatable("screen.sparkwitch.criminal_reasoner.section_dead"));
        y += 20;
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

    private void addTile(int x, int y, UUID uuid, String name, boolean selected, Runnable onPress) {
        CriminalReasonerPlayerButton button = new CriminalReasonerPlayerButton(
                x, y, uuid, name, b -> onPress.run());
        button.setSelected(selected);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        // Dark backing plus the role accent down the left edge, as in the source menu.
        // 深色底 + 左缘身份色强调条，与原版菜单一致。
        context.fill(0, 0, width, height, 0xC0101010);
        context.fill(0, 0, 4, height, 0xFF000000 | CriminalReasonerRules.COLOR);

        // Children (the avatar tiles) first: everything drawn afterwards sits above them.
        // 先画子控件（头像格子）；之后再画的内容都在它们之上。
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
            if (label.y() >= GRID_TOP - 20 && label.y() <= viewBottom) {
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
            int nameY = button.getY() + ICON + 2;
            if (nameY > viewBottom - 10) {
                continue;
            }
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
