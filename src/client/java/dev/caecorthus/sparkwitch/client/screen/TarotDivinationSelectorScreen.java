package dev.caecorthus.sparkwitch.client.screen;

import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import dev.caecorthus.sparkwitch.net.SubmitTarotDivinationSelectionC2SPacket;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Sends one paid selector choice back to the server; closing the screen intentionally forfeits it.
 * 每次付费选择只向服务端提交一次；关闭界面会按约定放弃本次选择。
 */
public final class TarotDivinationSelectorScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_PADDING = 10;
    private static final int TITLE_HEIGHT = 24;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int MUTED_COLOR = 0xA0A0A0;
    private static final int PANEL_COLOR = 0xF0181818;
    private static final int BORDER_COLOR = 0xFF545454;
    private static final int HOVER_COLOR = 0xFF383838;

    private final int mode;
    private final List<Entry> entries;
    private int panelX;
    private int panelY;
    private int panelHeight;
    private int listY;
    private int listHeight;
    private int scroll;
    private boolean submitted;

    public TarotDivinationSelectorScreen(int mode, List<UUID> playerIds, List<String> playerNames) {
        super(Text.translatable(titleKey(mode)));
        this.mode = mode;
        this.entries = mode == OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY
                ? identityEntries()
                : playerEntries(playerIds, playerNames);
    }

    @Override
    protected void init() {
        super.init();
        int width = Math.min(PANEL_WIDTH, Math.max(120, this.width - 24));
        int rowsForHeight = Math.max(
                1,
                (this.height - 24 - TITLE_HEIGHT - PANEL_PADDING * 2) / ROW_HEIGHT
        );
        int visibleRows = Math.min(
                Math.min(MAX_VISIBLE_ROWS, rowsForHeight),
                Math.max(1, entries.size())
        );
        listHeight = visibleRows * ROW_HEIGHT;
        panelHeight = TITLE_HEIGHT + listHeight + PANEL_PADDING * 2;
        panelX = (this.width - width) / 2;
        panelY = Math.max(12, (this.height - panelHeight) / 2);
        listY = panelY + PANEL_PADDING + TITLE_HEIGHT;
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int panelRight = panelX + panelWidth();
        context.fill(panelX - 1, panelY - 1, panelRight + 1, panelY + panelHeight + 1, BORDER_COLOR);
        context.fill(panelX, panelY, panelRight, panelY + panelHeight, PANEL_COLOR);
        String visibleTitle = this.textRenderer.trimToWidth(this.title.getString(), panelWidth() - 16);
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                visibleTitle,
                panelX + panelWidth() / 2,
                panelY + PANEL_PADDING,
                TEXT_COLOR
        );

        context.enableScissor(panelX + 1, listY, panelRight - 1, listY + listHeight);
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("screen.sparkwitch.tarot.empty"),
                    panelX + panelWidth() / 2,
                    listY + 7,
                    MUTED_COLOR
            );
        } else {
            for (int index = 0; index < entries.size(); index++) {
                int y = listY + index * ROW_HEIGHT - scroll;
                if (y + ROW_HEIGHT < listY || y > listY + listHeight) {
                    continue;
                }
                boolean hovered = inside(mouseX, mouseY, panelX + 2, y, panelWidth() - 4, ROW_HEIGHT);
                if (hovered) {
                    context.fill(panelX + 2, y, panelRight - 2, y + ROW_HEIGHT, HOVER_COLOR);
                }
                Entry entry = entries.get(index);
                String label = this.textRenderer.trimToWidth(entry.label().getString(), panelWidth() - 16);
                context.drawTextWithShadow(
                        this.textRenderer,
                        label,
                        panelX + 8,
                        y + (ROW_HEIGHT - this.textRenderer.fontHeight) / 2,
                        entry.color()
                );
            }
        }
        context.disableScissor();
        renderScrollbar(context, panelRight);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !submitted && inside(mouseX, mouseY, panelX, listY, panelWidth(), listHeight)) {
            int index = ((int) mouseY - listY + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < entries.size()) {
                submitted = true;
                ClientPlayNetworking.send(new SubmitTarotDivinationSelectionC2SPacket(
                        mode,
                        entries.get(index).target()
                ));
                close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, panelX, listY, panelWidth(), listHeight)) {
            int amount = (int) Math.round(-verticalAmount * ROW_HEIGHT);
            scroll = MathHelper.clamp(scroll + amount, 0, maxScroll());
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
        if (client != null) {
            client.setScreen(null);
        }
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, Math.max(120, this.width - 24));
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_HEIGHT - listHeight);
    }

    private void renderScrollbar(DrawContext context, int panelRight) {
        int contentHeight = entries.size() * ROW_HEIGHT;
        if (contentHeight <= listHeight) {
            return;
        }
        int thumbHeight = Math.max(12, listHeight * listHeight / contentHeight);
        int thumbTravel = listHeight - thumbHeight;
        int thumbY = listY + scroll * thumbTravel / maxScroll();
        context.fill(panelRight - 4, listY, panelRight - 2, listY + listHeight, 0xFF303030);
        context.fill(panelRight - 4, thumbY, panelRight - 2, thumbY + thumbHeight, 0xFF909090);
    }

    private static List<Entry> identityEntries() {
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Role role : WatheRoles.ROLES) {
            if (role == WatheRoles.NO_ROLE || role == WatheRoles.DISCOVERY_CIVILIAN) {
                continue;
            }
            String target = role.identifier().toString();
            if (!seen.add(target)) {
                continue;
            }
            result.add(new Entry(
                    target,
                    WitchRoleDisplayTexts.roleName(RoleDisplayTextRules.roleTranslationKey(role)),
                    role.color()
            ));
        }
        result.sort(Comparator
                .comparing((Entry entry) -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::target));
        return List.copyOf(result);
    }

    private static List<Entry> playerEntries(List<UUID> playerIds, List<String> playerNames) {
        int count = Math.min(playerIds.size(), playerNames.size());
        List<Entry> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new Entry(playerIds.get(index).toString(), Text.literal(playerNames.get(index)), TEXT_COLOR));
        }
        result.sort(Comparator
                .comparing((Entry entry) -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::target));
        return List.copyOf(result);
    }

    private static String titleKey(int mode) {
        return mode == OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY
                ? "screen.sparkwitch.tarot.identity.title"
                : "screen.sparkwitch.tarot.survival.title";
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private record Entry(String target, Text label, int color) {
    }
}
