package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinLayout.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The skin list: "Default (Wither)" and one row per skin folder, each with a swatch, its name and the folder id. The
 * applied skin has a magenta bar and wash. A click applies at once; with keyboard focus, Up/Down move a white cursor and
 * Enter or Space applies it. Wheel scrolling and a thin scrollbar (draggable) when the rows overflow.
 * 皮肤列表：「默认（凋灵）」与每个皮肤文件夹各一行，每行有色块、名称与文件夹 id。已应用的皮肤带洋红竖条与底色。点击立即应用；
 * 键盘聚焦时上下键移动白色光标，回车或空格应用。行数超出时可用滚轮滚动，并显示可拖动的细滚动条。
 */
final class MiniWitherSkinListWidget extends ClickableWidget {
    static final int ROW_HEIGHT = 22;
    private static final int PAD = 2;
    private static final int BAR = 3;
    private static final int SWATCH = 12;
    private static final int SCROLLBAR = 2;
    private static final int SCROLLBAR_HIT = 6;

    private final Consumer<String> onApply;
    private List<Row> rows = List.of();
    private String applied = MiniWitherSkinManager.DEFAULT_ID;
    private List<OrderedText> hint = List.of();
    private int cursor;
    private double scroll;
    private boolean draggingThumb;

    MiniWitherSkinListWidget(Rect at, Consumer<String> onApply) {
        super(at.x(), at.y(), at.width(), at.height(), Text.empty());
        this.onApply = onApply;
    }

    record Row(String id, Text name) {
    }

    /** Replaces the rows; {@code emptyHint} shows under the default row when there are no skins. / 替换各行。 */
    void setRows(List<MiniWitherSkinManager.Entry> entries, String appliedId, Text emptyHint) {
        List<Row> built = new ArrayList<>();
        built.add(new Row(MiniWitherSkinManager.DEFAULT_ID, MiniWitherSkinUi.displayName(null)));
        for (MiniWitherSkinManager.Entry entry : entries) {
            built.add(new Row(entry.id(), Text.literal(entry.displayName())));
        }
        rows = List.copyOf(built);
        hint = MinecraftClient.getInstance().textRenderer.wrapLines(emptyHint, Math.max(1, width - 16));
        setApplied(appliedId);
        cursor = Math.max(0, appliedIndex());
        scroll = MathHelper.clamp(scroll, 0.0, maxScroll());
        ensureVisible(cursor);
    }

    void setApplied(String id) {
        applied = id;
    }

    private int appliedIndex() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(applied)) {
                return i;
            }
        }
        return -1;
    }

    private void apply(int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        cursor = index;
        onApply.accept(rows.get(index).id());
    }

    // ---- geometry / 几何 ----

    private int contentHeight() {
        int hintHeight = rows.size() <= 1 ? hint.size() * 10 + 8 : 0;
        return 2 * PAD + rows.size() * ROW_HEIGHT + hintHeight;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    private boolean scrollable() {
        return maxScroll() > 0;
    }

    private int rowTop(int index) {
        return getY() + PAD + index * ROW_HEIGHT - (int) Math.round(scroll);
    }

    private int rowAt(double mouseY) {
        int offset = (int) Math.floor(mouseY - getY() - PAD + scroll);
        int index = Math.floorDiv(offset, ROW_HEIGHT);
        return offset >= 0 && index < rows.size() ? index : -1;
    }

    private int rowRight() {
        return getRight() - (scrollable() ? SCROLLBAR_HIT : 0);
    }

    private void ensureVisible(int index) {
        int top = PAD + index * ROW_HEIGHT;
        if (top < scroll) {
            scroll = top;
        } else if (top + ROW_HEIGHT > scroll + height) {
            scroll = top + ROW_HEIGHT - height;
        }
        scroll = MathHelper.clamp(scroll, 0.0, maxScroll());
    }

    // ---- drawing / 绘制 ----

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int x = getX();
        int y = getY();
        context.fill(x, y, getRight(), getBottom(), MiniWitherSkinPalette.PANEL);
        boolean keyboard = MiniWitherSkinDraw.keyboardFocus(isFocused());
        int hovered = isHovered() && mouseX < rowRight() ? rowAt(mouseY) : -1;
        int right = rowRight();
        context.enableScissor(x, y, getRight(), getBottom());
        for (int i = 0; i < rows.size(); i++) {
            int top = rowTop(i);
            if (top + ROW_HEIGHT <= y || top >= getBottom()) {
                continue;
            }
            Row row = rows.get(i);
            boolean selected = row.id().equals(applied);
            if (selected) {
                context.fill(x, top, right, top + ROW_HEIGHT, MiniWitherSkinPalette.ACCENT_WASH);
                context.fill(x, top, x + BAR, top + ROW_HEIGHT, MiniWitherSkinPalette.ACCENT);
            } else if (i == hovered) {
                context.fill(x, top, right, top + ROW_HEIGHT, MiniWitherSkinPalette.ROW_HOVER);
            }
            if (keyboard && i == cursor) {
                MiniWitherSkinDraw.outline(context, x, top, right - x, ROW_HEIGHT, MiniWitherSkinPalette.TEXT);
            }
            drawSwatch(context, x + BAR + 5, top + (ROW_HEIGHT - SWATCH) / 2, row.id());
            drawLabels(context, font, row, selected, x + BAR + 5 + SWATCH + 6, right - 6,
                    top + (ROW_HEIGHT - font.fontHeight) / 2 + 1);
        }
        if (rows.size() <= 1) {
            int lineY = rowTop(rows.size()) + 6;
            for (OrderedText line : hint) {
                context.drawTextWithShadow(font, line, x + 8, lineY, MiniWitherSkinPalette.TEXT_MUTED);
                lineY += 10;
            }
        }
        context.disableScissor();
        if (scrollable()) {
            int trackX = getRight() - SCROLLBAR - 2;
            context.fill(trackX, y + 2, trackX + SCROLLBAR, getBottom() - 2, MiniWitherSkinPalette.SCROLL_TRACK);
            int track = height - 4;
            int thumb = Math.max(12, track * height / contentHeight());
            int thumbY = y + 2 + (int) Math.round((track - thumb) * scroll / maxScroll());
            context.fill(trackX, thumbY, trackX + SCROLLBAR, thumbY + thumb, MiniWitherSkinPalette.SCROLL_THUMB);
        }
    }

    private static void drawSwatch(DrawContext context, int x, int y, String id) {
        context.fill(x, y, x + SWATCH, y + SWATCH, MiniWitherSkinPalette.BUTTON_EDGE);
        if (id.equals(MiniWitherSkinManager.DEFAULT_ID)) {
            // The vanilla Wither: three dark heads on grey. / 原版凋灵：灰底上三个深色头。
            context.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, 0xFF5A5A5A);
            context.fill(x + 2, y + 5, x + 4, y + 7, 0xFF141414);
            context.fill(x + 5, y + 3, x + 7, y + 5, 0xFF141414);
            context.fill(x + 8, y + 5, x + 10, y + 7, 0xFF141414);
            return;
        }
        context.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, swatchColor(id));
        context.fill(x + 1, y + 1, x + SWATCH - 1, y + 3, 0x30FFFFFF);
    }

    /** A stable colour per folder id, so each skin keeps its swatch. / 每个文件夹 id 的固定颜色，皮肤色块保持不变。 */
    static int swatchColor(String id) {
        float hue = Math.floorMod(id.hashCode(), 360) / 360.0F;
        return 0xFF000000 | MathHelper.hsvToRgb(hue, 0.45F, 0.85F);
    }

    private static void drawLabels(DrawContext context, TextRenderer font, Row row, boolean selected, int left,
                                   int right, int textY) {
        int room = Math.max(0, right - left);
        int nameColor = selected ? MiniWitherSkinPalette.TEXT : MiniWitherSkinPalette.TEXT_BODY;
        Text id = Text.literal(row.id());
        int idWidth = row.id().isEmpty() ? 0 : font.getWidth(id);
        int nameWidth = font.getWidth(row.name());
        boolean sameAsName = row.name().getString().equals(row.id());
        if (idWidth > 0 && !sameAsName && nameWidth + 8 + idWidth <= room) {
            context.drawTextWithShadow(font, row.name(), left, textY, nameColor);
            context.drawTextWithShadow(font, id, right - idWidth, textY, MiniWitherSkinPalette.TEXT_MUTED);
        } else if (idWidth > 0 && !sameAsName && room - idWidth - 8 >= 40) {
            context.drawTextWithShadow(font, MiniWitherSkinDraw.fit(font, row.name(), room - idWidth - 8), left, textY,
                    nameColor);
            context.drawTextWithShadow(font, id, right - idWidth, textY, MiniWitherSkinPalette.TEXT_MUTED);
        } else {
            context.drawTextWithShadow(font, MiniWitherSkinDraw.fit(font, row.name(), room), left, textY, nameColor);
        }
    }

    // ---- input / 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (scrollable() && mouseX >= getRight() - SCROLLBAR_HIT) {
            draggingThumb = true;
            scrollToThumb(mouseY);
            return true;
        }
        int index = rowAt(mouseY);
        if (index >= 0) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            apply(index);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingThumb) {
            scrollToThumb(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = draggingThumb;
        draggingThumb = false;
        return was;
    }

    private void scrollToThumb(double mouseY) {
        double fraction = (mouseY - getY() - 2) / Math.max(1, height - 4);
        scroll = MathHelper.clamp(fraction * maxScroll(), 0.0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!scrollable() || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll = MathHelper.clamp(scroll - verticalAmount * ROW_HEIGHT, 0.0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active || rows.isEmpty()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                int next = cursor + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
                if (next < 0 || next >= rows.size()) {
                    // Let arrow navigation leave the list at its ends. / 到达两端时让方向键导航离开列表。
                    return false;
                }
                cursor = next;
                ensureVisible(cursor);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                playDownSound(MinecraftClient.getInstance().getSoundManager());
                apply(cursor);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused && (cursor < 0 || cursor >= rows.size())) {
            cursor = Math.max(0, appliedIndex());
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        if (cursor >= 0 && cursor < rows.size()) {
            builder.put(NarrationPart.TITLE, Text.translatable("narrator.select", rows.get(cursor).name()));
        }
        if (isFocused()) {
            builder.put(NarrationPart.USAGE, Text.translatable("narration.selection.usage"));
        }
    }
}
