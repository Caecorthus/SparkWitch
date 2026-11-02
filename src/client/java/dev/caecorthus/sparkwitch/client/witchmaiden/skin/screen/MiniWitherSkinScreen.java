package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinLayout.Rect;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Options → Skin Customization → Mini Wither Appearance (also opened by SparkAssist through
 * {@code api.client.MiniWitherSkinSettingsApi#createScreen}). Owner pick 2026-11-02: mockup A "暗夜面板", drawn with
 * this package's own flat widgets over the vanilla translucent background. Choosing a skin applies and saves at once;
 * the status pill, the stage and the sound buttons follow the manager live. The sound buttons play the slot's own event
 * at the listener, so they go through the skin's sound override exactly like the game. Local only: nothing here
 * reaches the server.
 * 「选项 → 皮肤自定义 → 小凋灵外观」（SparkAssist 也可通过 {@code api.client.MiniWitherSkinSettingsApi#createScreen} 打开）。
 * 所有者 2026-11-02 选定方案 A「暗夜面板」，在原版半透明背景上用本包自绘的扁平控件绘制。选择皮肤即立即应用并保存；状态胶囊、
 * 舞台与试听按钮都实时跟随管理器。试听按钮在听者处播放该槽位自己的声音事件，因此与游戏中一样经过皮肤的声音替换。仅本地生效：
 * 这里的任何内容都不会发送到服务器。
 */
public final class MiniWitherSkinScreen extends Screen {
    /** The skins folder as players see it, for the empty-list hint. / 空列表提示中显示的皮肤文件夹。 */
    static final String FOLDER_HINT = "config/sparkwitch/mini_wither/";
    private static final float PREVIEW_VOLUME = 1.0F;
    /** A three-quarter view to start from. / 起始为四分之三侧视角。 */
    private static final float START_YAW = -30.0F;
    private static final int MAX_TOOLTIP_WARNINGS = 8;
    private static final int PILL_PAD = 6;
    private static final int DOT_GAP = 5;

    @Nullable
    private final Screen parent;
    private final MiniWitherPreviewSpin spin = new MiniWitherPreviewSpin(START_YAW, Util.getMeasuringTimeMs());
    private MiniWitherSkinLayout layout;
    private MiniWitherSkinListWidget list;
    @Nullable
    private SoundInstance lastSound;

    public MiniWitherSkinScreen(@Nullable Screen parent) {
        super(Text.translatable(MiniWitherSkinLangKeys.TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MiniWitherSoundSlot[] slots = MiniWitherSoundSlot.values();
        layout = MiniWitherSkinLayout.compute(width, height, slots.length);
        list = addDrawableChild(new MiniWitherSkinListWidget(layout.list(), this::choose));
        refreshList();
        addDrawableChild(new MiniWitherSkinStageWidget(layout.stage(),
                Text.translatable(MiniWitherSkinLangKeys.CAPTION), spin));
        for (int i = 0; i < slots.length; i++) {
            MiniWitherSoundSlot slot = slots[i];
            MiniWitherSkinSoundButton button = new MiniWitherSkinSoundButton(layout.sounds().get(i),
                    Text.translatable(MiniWitherSkinLangKeys.sound(slot)), () -> playing(slot),
                    ignored -> playPreview(slot));
            button.setTooltip(Tooltip.of(Text.translatable(MiniWitherSkinLangKeys.soundTooltip(slot))
                    .append("\n")
                    .append(Text.translatable(MiniWitherSkinLangKeys.SOUND_FILE, slot.key()))));
            addDrawableChild(button);
        }
        List<Rect> footer = layout.footer();
        addDrawableChild(flat(footer.get(0), Text.translatable(MiniWitherSkinLangKeys.OPEN_FOLDER), false,
                MiniWitherSkinLangKeys.OPEN_FOLDER_TOOLTIP, MiniWitherSkinUi::openSkinsFolder));
        addDrawableChild(flat(footer.get(1), Text.translatable(MiniWitherSkinLangKeys.RELOAD), false,
                MiniWitherSkinLangKeys.RELOAD_TOOLTIP, this::reload));
        addDrawableChild(flat(footer.get(2), ScreenTexts.DONE, true, null, this::close));
    }

    /** The list takes focus first, so Up/Down and Enter work at once. / 列表优先获得焦点，上下键与回车可立即使用。 */
    @Override
    protected void setInitialFocus() {
        setInitialFocus(list);
    }

    private static MiniWitherSkinFlatButton flat(Rect at, Text message, boolean accent, @Nullable String tooltipKey,
                                                 Runnable action) {
        MiniWitherSkinFlatButton button = new MiniWitherSkinFlatButton(at, message, accent, ignored -> action.run());
        if (tooltipKey != null) {
            button.setTooltip(Tooltip.of(Text.translatable(tooltipKey)));
        }
        return button;
    }

    private void choose(String id) {
        if (!id.equals(MiniWitherSkinManager.selectedId())) {
            MiniWitherSkinUi.selectListed(id);
        }
        list.setApplied(MiniWitherSkinManager.selectedId());
    }

    private void reload() {
        MiniWitherSkinManager.reload();
        refreshList();
    }

    /** Rebuilds the rows from the last scan and marks the applied one. / 按上次扫描重建各行并标出已应用项。 */
    private void refreshList() {
        List<MiniWitherSkinManager.Entry> entries = MiniWitherSkinUi.entries();
        list.setRows(entries, MiniWitherSkinManager.selectedId(),
                Text.translatable(MiniWitherSkinLangKeys.EMPTY, FOLDER_HINT));
    }

    private boolean playing(MiniWitherSoundSlot slot) {
        return lastSound != null && client != null && lastSound.getId().equals(slot.eventId())
                && client.getSoundManager().isPlaying(lastSound);
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
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, layout.titleY(),
                MiniWitherSkinPalette.TEXT);
        renderStatus(context, mouseX, mouseY);
        Rect caption = layout.caption();
        context.drawCenteredTextWithShadow(textRenderer,
                MiniWitherSkinDraw.fit(textRenderer, Text.translatable(MiniWitherSkinLangKeys.CAPTION), caption.width()),
                caption.centerX(), caption.y(), MiniWitherSkinPalette.TEXT_MUTED);
    }

    /**
     * The status pill: a dark chip with a coloured dot and the status line, cut with "…" when long. Hovering shows the
     * whole line and the load warnings.
     * 状态胶囊：带彩色圆点与状态文字的深色小块，过长时以「…」截断。悬停显示完整文字与加载警告。
     */
    private void renderStatus(DrawContext context, int mouseX, int mouseY) {
        MiniWitherSkinManager.Status status = MiniWitherSkinManager.status();
        List<String> warnings = MiniWitherSkinManager.warnings();
        Text text = MiniWitherSkinUi.statusText();
        Rect band = layout.statusBand();
        int chrome = 2 * PILL_PAD + 5 + DOT_GAP;
        int textRoom = Math.max(0, band.width() - chrome);
        OrderedText shown = MiniWitherSkinDraw.fit(textRenderer, text, textRoom);
        boolean cut = !MiniWitherSkinDraw.fits(textRenderer, text, textRoom);
        int pillWidth = Math.min(band.width(), chrome + textRenderer.getWidth(shown));
        int x = band.x() + (band.width() - pillWidth) / 2;
        int y = band.y();
        MiniWitherSkinDraw.chip(context, x, y, pillWidth, band.height(), MiniWitherSkinPalette.PANEL);
        MiniWitherSkinDraw.dot(context, x + PILL_PAD, y + (band.height() - 5) / 2,
                MiniWitherSkinPalette.dot(status.kind(), !warnings.isEmpty()));
        context.drawTextWithShadow(textRenderer, shown, x + PILL_PAD + 5 + DOT_GAP,
                y + (band.height() - textRenderer.fontHeight) / 2 + 1, MiniWitherSkinPalette.TEXT_BODY);
        boolean hovered = mouseX >= x && mouseX < x + pillWidth && mouseY >= y && mouseY < y + band.height();
        if (hovered && (cut || !warnings.isEmpty())) {
            setTooltip(statusTooltip(textRenderer, text, warnings, Math.max(160, width / 2)));
        }
    }

    private static List<OrderedText> statusTooltip(TextRenderer font, Text status, List<String> warnings, int wrap) {
        List<OrderedText> lines = new ArrayList<>(font.wrapLines(status, wrap));
        if (!warnings.isEmpty()) {
            lines.addAll(font.wrapLines(Text.translatable(MiniWitherSkinLangKeys.WARNINGS, warnings.size())
                    .formatted(Formatting.GOLD), wrap));
            for (int i = 0; i < Math.min(MAX_TOOLTIP_WARNINGS, warnings.size()); i++) {
                lines.addAll(font.wrapLines(Text.literal("- " + warnings.get(i)).formatted(Formatting.GRAY), wrap));
            }
            if (warnings.size() > MAX_TOOLTIP_WARNINGS) {
                lines.addAll(font.wrapLines(Text.translatable(MiniWitherSkinLangKeys.MORE_WARNINGS,
                        warnings.size() - MAX_TOOLTIP_WARNINGS).formatted(Formatting.GRAY), wrap));
            }
        }
        return lines;
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
}
