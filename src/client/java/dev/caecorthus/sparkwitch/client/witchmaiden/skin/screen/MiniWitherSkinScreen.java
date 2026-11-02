package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinPreview;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Options → Skin Customization → Mini Wither Appearance (also opened by SparkAssist through
 * {@code api.client.MiniWitherSkinSettingsApi#createScreen}). Choosing a row applies and saves at once; the status
 * line, the turning preview and the sound buttons all follow the manager live. The sound buttons play the slot's own
 * event at the listener, so they go through the skin's sound override exactly like the game. Owner 2026-11-02: local
 * only; nothing here reaches the server.
 * 「选项 → 皮肤自定义 → 小凋灵外观」（SparkAssist 也可通过 {@code api.client.MiniWitherSkinSettingsApi#createScreen} 打开）。
 * 选中一行即立即应用并保存；状态行、旋转预览与试听按钮都实时跟随管理器。试听按钮在听者处播放该槽位自己的声音事件，因此与游戏中
 * 一样经过皮肤的声音替换。所有者 2026-11-02：仅本地，这里的任何内容都不会发送到服务器。
 */
public final class MiniWitherSkinScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkWitch/MiniWitherSkin");
    private static final int ROW_HEIGHT = 16;
    private static final int PREVIEW_BACKGROUND = 0x66000000;
    private static final int PREVIEW_BORDER = 0xFF5A5A5A;
    private static final int LABEL_COLOR = 0xA0A0A0;
    private static final float PREVIEW_VOLUME = 1.0F;
    private static final Text ELLIPSIS = Text.literal("...");

    @Nullable
    private final Screen parent;
    private MiniWitherSkinLayout layout;
    private SkinList list;
    @Nullable
    private SoundInstance lastSound;
    private boolean previewFailureLogged;

    public MiniWitherSkinScreen(@Nullable Screen parent) {
        super(Text.translatable(MiniWitherSkinLangKeys.TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MiniWitherSoundSlot[] slots = MiniWitherSoundSlot.values();
        layout = MiniWitherSkinLayout.compute(width, height, slots.length);
        MiniWitherSkinLayout.Rect area = layout.list();
        list = addDrawableChild(new SkinList(client, area));
        refreshList();
        for (int i = 0; i < slots.length; i++) {
            MiniWitherSoundSlot slot = slots[i];
            MiniWitherSkinLayout.Rect cell = layout.sounds().get(i);
            SoundButton button = new SoundButton(cell, Text.translatable(MiniWitherSkinLangKeys.sound(slot)),
                    ignored -> playPreview(slot));
            button.setTooltip(Tooltip.of(Text.translatable(MiniWitherSkinLangKeys.soundTooltip(slot))
                    .append("\n")
                    .append(Text.translatable(MiniWitherSkinLangKeys.SOUND_FILE, slot.key()))));
            addDrawableChild(button);
        }
        List<MiniWitherSkinLayout.Rect> footer = layout.footer();
        addDrawableChild(button(footer.get(0), Text.translatable(MiniWitherSkinLangKeys.OPEN_FOLDER),
                MiniWitherSkinLangKeys.OPEN_FOLDER_TOOLTIP, MiniWitherSkinUi::openSkinsFolder));
        addDrawableChild(button(footer.get(1), Text.translatable(MiniWitherSkinLangKeys.RELOAD),
                MiniWitherSkinLangKeys.RELOAD_TOOLTIP, this::reload));
        addDrawableChild(button(footer.get(2), ScreenTexts.DONE, null, this::close));
    }

    private static ButtonWidget button(MiniWitherSkinLayout.Rect at, Text message, @Nullable String tooltipKey,
                                       Runnable action) {
        ButtonWidget.Builder builder = ButtonWidget.builder(message, ignored -> action.run())
                .dimensions(at.x(), at.y(), at.width(), at.height());
        if (tooltipKey != null) {
            builder.tooltip(Tooltip.of(Text.translatable(tooltipKey)));
        }
        return builder.build();
    }

    private void reload() {
        MiniWitherSkinManager.reload();
        refreshList();
    }

    /** Rebuilds the rows from the last scan and highlights the selected one. / 按上次扫描重建各行并高亮选中项。 */
    private void refreshList() {
        String selected = MiniWitherSkinManager.selectedId();
        list.setRows(MiniWitherSkinUi.entries(), selected);
    }

    private void playPreview(MiniWitherSoundSlot slot) {
        if (client == null) {
            return;
        }
        SoundManager sounds = client.getSoundManager();
        if (lastSound != null) {
            sounds.stop(lastSound);
        }
        // The event id, not a registry entry: SoundManager resolves it like the game does, custom files included.
        // 使用事件 id 而非注册表条目：SoundManager 与游戏中一样解析它，包括自定义文件。
        lastSound = PositionedSoundInstance.master(SoundEvent.of(slot.eventId()), 1.0F, PREVIEW_VOLUME);
        sounds.play(lastSound);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, layout.titleY(), 0xFFFFFF);
        renderStatus(context, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, Text.translatable(MiniWitherSkinLangKeys.SOUNDS),
                layout.soundsLabelX(), layout.soundsLabelY(), LABEL_COLOR);
        renderPreview(context, delta);
    }

    /** One centred line; a long one is cut with "..." and shown whole on hover. / 单行居中；过长时截断并在悬停时完整显示。 */
    private void renderStatus(DrawContext context, int mouseX, int mouseY) {
        MiniWitherSkinManager.Status.Kind kind = MiniWitherSkinManager.status().kind();
        Text status = MiniWitherSkinUi.statusText();
        int maxWidth = layout.statusWidth();
        OrderedText line;
        if (textRenderer.getWidth(status) <= maxWidth) {
            line = status.asOrderedText();
        } else {
            StringVisitable head = textRenderer.trimToWidth(status,
                    Math.max(0, maxWidth - textRenderer.getWidth(ELLIPSIS)));
            line = Language.getInstance().reorder(StringVisitable.concat(head, ELLIPSIS));
            int lineWidth = textRenderer.getWidth(line);
            int left = (width - lineWidth) / 2;
            if (mouseX >= left && mouseX < left + lineWidth && mouseY >= layout.statusY()
                    && mouseY < layout.statusY() + textRenderer.fontHeight) {
                setTooltip(textRenderer.wrapLines(status, Math.max(120, width / 2)));
            }
        }
        context.drawCenteredTextWithShadow(textRenderer, line, width / 2, layout.statusY(),
                MiniWitherSkinUi.statusColor(kind));
    }

    private void renderPreview(DrawContext context, float delta) {
        MiniWitherSkinLayout.Rect box = layout.preview();
        if (box.width() <= 2) {
            return;
        }
        context.fill(box.x(), box.y(), box.right(), box.bottom(), PREVIEW_BACKGROUND);
        context.drawBorder(box.x(), box.y(), box.width(), box.height(), PREVIEW_BORDER);
        boolean drawn;
        context.enableScissor(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1);
        try {
            MiniWitherSkinPreview.render(context, box.centerX(), box.centerY(), box.width(), delta);
            drawn = true;
        } catch (RuntimeException failure) {
            // A preview failure must never close the settings: log once per screen and keep the placeholder frame.
            // 预览失败绝不能关闭设置界面：每个界面只记录一次日志，并保留占位方框。
            if (!previewFailureLogged) {
                previewFailureLogged = true;
                LOGGER.warn("Mini Wither preview failed; showing a placeholder", failure);
            }
            drawn = false;
        } finally {
            context.disableScissor();
        }
        if (!drawn) {
            List<OrderedText> lines = textRenderer.wrapLines(
                    Text.translatable(MiniWitherSkinLangKeys.PREVIEW_UNAVAILABLE), Math.max(1, box.width() - 8));
            int y = box.centerY() - lines.size() * textRenderer.fontHeight / 2;
            for (OrderedText text : lines) {
                context.drawCenteredTextWithShadow(textRenderer, text, box.centerX(), y, LABEL_COLOR);
                y += textRenderer.fontHeight;
            }
        }
    }

    @Override
    public void removed() {
        if (lastSound != null && client != null) {
            client.getSoundManager().stop(lastSound);
            lastSound = null;
        }
        super.removed();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    /** A button whose press plays a preview, so the vanilla click is left out. / 按下即试听的按钮，因此不播放原版点击声。 */
    private static final class SoundButton extends ButtonWidget {
        private SoundButton(MiniWitherSkinLayout.Rect at, Text message, PressAction onPress) {
            super(at.x(), at.y(), at.width(), at.height(), message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
        }
    }

    /**
     * The skin rows. Selecting a row, by mouse or keyboard, selects that skin at once unless it already is the
     * manager's choice, so rebuilding the rows never reloads anything.
     * 皮肤行。用鼠标或键盘选中一行即立即选择该皮肤，除非它已是管理器的当前选择，因此重建各行不会重新加载任何东西。
     */
    private static final class SkinList extends AlwaysSelectedEntryListWidget<SkinList.Row> {
        private SkinList(MinecraftClient client, MiniWitherSkinLayout.Rect area) {
            super(client, area.width(), area.height(), area.y(), ROW_HEIGHT);
            setX(area.x());
        }

        private void setRows(List<MiniWitherSkinManager.Entry> entries, String selectedId) {
            clearEntries();
            Row selected = null;
            Row fallback = new Row(MiniWitherSkinManager.DEFAULT_ID, MiniWitherSkinUi.displayName(null));
            addEntry(fallback);
            if (fallback.id.equals(selectedId)) {
                selected = fallback;
            }
            for (MiniWitherSkinManager.Entry entry : entries) {
                Row row = new Row(entry.id(), Text.literal(entry.displayName()));
                addEntry(row);
                if (row.id.equals(selectedId)) {
                    selected = row;
                }
            }
            // A missing selected folder has no row: nothing is highlighted and the status line says why.
            // 选中的文件夹不存在时没有对应行：不高亮任何行，由状态行说明原因。
            setSelected(selected);
            refreshScroll();
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            if (row != null && !row.id.equals(MiniWitherSkinManager.selectedId())) {
                MiniWitherSkinUi.selectListed(row.id);
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, width - 16);
        }

        @Override
        protected int getScrollbarX() {
            return getX() + width - 6;
        }

        private final class Row extends AlwaysSelectedEntryListWidget.Entry<Row> {
            private final String id;
            private final Text name;

            private Row(String id, Text name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float tickDelta) {
                var renderer = client.textRenderer;
                StringVisitable shown = renderer.getWidth(name) <= entryWidth - 4 ? name
                        : StringVisitable.concat(renderer.trimToWidth(name,
                        Math.max(0, entryWidth - 4 - renderer.getWidth(ELLIPSIS))), ELLIPSIS);
                context.drawTextWithShadow(renderer, Language.getInstance().reorder(shown), x + 2,
                        y + (entryHeight - renderer.fontHeight) / 2 + 1, 0xFFFFFF);
            }

            @Override
            public Text getNarration() {
                return Text.translatable("narrator.select", name);
            }
        }
    }
}
