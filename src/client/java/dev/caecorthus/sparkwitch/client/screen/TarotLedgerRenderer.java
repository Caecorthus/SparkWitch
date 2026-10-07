package dev.caecorthus.sparkwitch.client.screen;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.hud.TarotDivinationHudRenderer;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationClientState;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries.Entry;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries.Section;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Block;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Content;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Footer;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Hit;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.HitKind;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Panel;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.Rect;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.RunningHead;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout.TitleBar;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotReadingLog;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Draws {@link TarotDivinationSelectorScreen}'s ledger from one {@link View} of the screen state per frame. It keeps
 * the labels and colours built for one {@code init()} but no input or session state: selecting, scrolling and the
 * paid submission stay in the screen. Leak guard: the only per-row marks are the self tag and the stamps from this
 * reader's own {@link TarotReadingLog}.
 * 按界面每帧提供的 {@link View} 绘制 {@link TarotDivinationSelectorScreen} 的账簿。它保存一次 {@code init()}
 * 生成的文字与颜色，但不持有输入或会话状态：选中、滚动与付费提交仍由界面处理。防泄露：唯一的逐行标记是“你”标签与
 * 本人 {@link TarotReadingLog} 中的印记。
 */
final class TarotLedgerRenderer {
    private static final String ESC_LABEL = "Esc";
    /** Wathe's coin glyph, drawn untinted. Wathe 金币字形，保持原色绘制。 */
    private static final String COIN_GLYPH = "\uE781";
    private static final int TOOLTIP_Z = 400;

    private final TextRenderer textRenderer;
    private final Text title;
    private final int mode;
    private final boolean identity;
    private final Panel panel;
    private final int totalCount;
    private final Map<Section, Integer> sectionColors = new EnumMap<>(Section.class);
    private final String paidLabel;
    private final String priceLabel;
    private final String placeholder;
    private final String selfTag;
    private final int selfTagWidth;
    private final Text forfeit;
    private final Text divine;

    /**
     * Built by every {@code init()}: the panel and the roster size stay fixed until the next one.
     * 由每次 {@code init()} 构建：面板与名单大小在下次 init 之前保持不变。
     */
    TarotLedgerRenderer(TextRenderer textRenderer, Text title, int mode, Panel panel, int totalCount) {
        this.textRenderer = textRenderer;
        this.title = title;
        this.mode = mode;
        this.identity = mode == OpenTarotDivinationSelectorS2CPacket.MODE_IDENTITY;
        this.panel = panel;
        this.totalCount = totalCount;

        sectionColors.put(Section.CIVILIAN, TarotDivinationHudRenderer.factionColor(FactionIds.CIVILIAN));
        sectionColors.put(Section.KILLER, TarotDivinationHudRenderer.factionColor(FactionIds.KILLER));
        sectionColors.put(Section.NEUTRAL, TarotDivinationHudRenderer.factionColor(FactionIds.NEUTRAL));
        sectionColors.put(Section.WITCH, TarotDivinationHudRenderer.factionColor(SparkWitchFactions.WITCH));
        sectionColors.put(Section.SPECIAL, TarotLedgerPaint.SPECIAL & 0xFFFFFF);

        paidLabel = Text.translatable("screen.sparkwitch.tarot.paid").getString();
        priceLabel = String.valueOf(identity ? TarotReaderRules.IDENTITY_PRICE : TarotReaderRules.SURVIVAL_PRICE);
        placeholder = ellipsize(TarotLedgerText.searchPrompt(identity).getString(), panel.placeholderWidth());
        selfTag = Text.translatable("screen.sparkwitch.tarot.self").getString();
        selfTagWidth = textRenderer.getWidth(selfTag);
        forfeit = Text.translatable("screen.sparkwitch.tarot.forfeit");
        divine = Text.translatable("screen.sparkwitch.tarot.divine");
    }

    /**
     * The footer as it is drawn for {@code selected}, so the screen hit-tests the same button it shows.
     * 按 {@code selected} 绘制时的页脚，界面据此进行命中判定，与显示的按钮一致。
     */
    Footer footer(@Nullable Entry selected) {
        return footer(footerLine(selected));
    }

    /**
     * Everything but the search field and the tooltip: chrome, list and footer.
     * 除搜索框与提示框外的全部内容：外框、列表与页脚。
     */
    void draw(DrawContext c, View v) {
        FooterLine line = footerLine(v.selected());
        drawChrome(c, v);
        drawList(c, v);
        drawFooter(c, v, line, footer(line));
    }

    /**
     * Tooltips at z 400, placed by the layout so they never cover their anchor, the footer or the rod.
     * 提示框位于 z 400，由布局定位，绝不遮住锚点、页脚或滑杆。
     */
    void drawTooltip(DrawContext c, View v) {
        Hit hover = v.hover();
        List<Text> lines;
        Rect anchor;
        if (hover.kind() == HitKind.CELL) {
            Rect cell = TarotLedgerLayout.cell(panel, v.content(), hover.cell(), v.scroll());
            Entry entry = v.cells().get(hover.cell());
            boolean nameCut = textRenderer.getWidth(entry.label()) > nameMaxWidth(entry, cell.width(), false);
            lines = TarotLedgerText.cellTooltip(entry, reading(entry).orElse(null), nameCut,
                    v.armed() && v.isSelected(entry));
            anchor = TarotLedgerLayout.nameAnchor(cell);
        } else if (hover.kind() == HitKind.TAB && hover.section() != null) {
            Block block = v.content().block(hover.section());
            if (block == null) {
                return;
            }
            lines = List.of(TarotLedgerText.sectionLabel(hover.section()),
                    TarotLedgerText.count(identity, block.count()));
            anchor = panel.tabs().get(TarotLedgerLayout.TAB_SECTIONS.indexOf(hover.section()));
        } else {
            return;
        }
        if (lines.isEmpty()) {
            return;
        }
        // Long lines (the full joke-role name, a verdict at 320x240) wrap to the list area, so the box never
        // reaches the rod or the screen edge. 过长的行按列表区域换行，提示框不会压到滑杆或超出屏幕。
        int maxWidth = TarotLedgerLayout.tooltipMaxWidth(panel);
        List<OrderedText> pieces = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? InventoryCardPaint.TEXT : InventoryCardPaint.FAINT;
            for (OrderedText piece : textRenderer.wrapLines(lines.get(i), maxWidth)) {
                pieces.add(piece);
                colors.add(color);
            }
        }
        int tipWidth = 0;
        for (OrderedText piece : pieces) {
            tipWidth = Math.max(tipWidth, textRenderer.getWidth(piece));
        }
        int tipHeight = TarotLedgerLayout.tooltipHeight(pieces.size());
        Rect box = TarotLedgerLayout.tooltip(panel, anchor, tipWidth, tipHeight);
        MatrixStack matrices = c.getMatrices();
        matrices.push();
        matrices.translate(0, 0, TOOLTIP_Z);
        InventoryCardPaint.brassTooltip(c, box.x(), box.y(), box.width(), box.height());
        for (int i = 0; i < pieces.size(); i++) {
            c.drawText(textRenderer, pieces.get(i), box.x(), box.y() + TarotLedgerLayout.tooltipLineY(i),
                    colors.get(i), true);
        }
        matrices.pop();
    }

    // ================================================================ chrome and list

    private void drawChrome(DrawContext c, View v) {
        Rect frame = panel.frame();
        InventoryCardPaint.panel(c, frame.x(), frame.y(), frame.width(), frame.height());
        TitleBar bar = TarotLedgerLayout.titleBar(panel, textRenderer.getWidth(paidLabel),
                textRenderer.getWidth(priceLabel));
        Rect well = panel.well();
        Hit hover = v.hover();
        boolean focused = v.searchFocused();
        boolean clearShown = !v.query().isEmpty();
        List<Rect> tabs = panel.tabs();
        Section active = TarotLedgerLayout.activeSection(v.content(), v.scroll());
        InventoryCardPaint.batch(c, () -> {
            TarotLedgerPaint.titleBand(c, frame.x(), frame.y(), frame.width());
            TarotDivinationHudRenderer.drawIcon(c, bar.iconX(), bar.iconY());
            TarotLedgerPaint.searchWell(c, well.x(), well.y(), well.width(), well.height(), focused);
            if (clearShown) {
                TarotLedgerPaint.clear5(c, panel.clearX(), panel.clearY(), hover.kind() == HitKind.CLEAR
                        ? InventoryCardPaint.TEXT_HI : InventoryCardPaint.FAINT);
            }
            for (int i = 0; i < tabs.size(); i++) {
                Section section = TarotLedgerLayout.TAB_SECTIONS.get(i);
                Rect tab = tabs.get(i);
                // A tab with no matches uses the HUD's empty-faction outline and stays inert (no wash, no click).
                // 无匹配的标签沿用占卜表空阵营的描边样式，且不响应（无底色、不可点击）。
                boolean present = v.content().block(section) != null;
                if (present && hover.kind() == HitKind.TAB && hover.section() == section) {
                    InventoryCardPaint.roundedFill(c, tab.x(), tab.y(), tab.width(), tab.height(),
                            TarotLedgerPaint.ICON_HOVER);
                } else if (present && section == active) {
                    InventoryCardPaint.roundedFill(c, tab.x(), tab.y(), tab.width(), tab.height(),
                            TarotLedgerPaint.ICON_ACTIVE);
                }
                TarotLedgerPaint.sectionMark(c, tab.x() + TarotLedgerLayout.TAB_MARK_X,
                        tab.y() + TarotLedgerLayout.TAB_MARK_Y, sectionColor(section), !present);
            }
        });

        c.drawText(textRenderer, ellipsize(title.getString(), bar.titleMaxWidth()), bar.titleX(), bar.titleY(),
                InventoryCardPaint.TITLE, false);
        c.drawText(textRenderer, paidLabel, bar.paidX(), bar.titleY(), InventoryCardPaint.FAINT, true);
        c.drawText(textRenderer, priceLabel, bar.priceX(), bar.titleY(), InventoryCardPaint.TEXT, true);
        InventoryCardPaint.iconText(c, textRenderer, Text.literal(COIN_GLYPH), bar.coinX(), bar.titleY(),
                InventoryCardPaint.GLYPH, false);
        if (v.query().isEmpty()) {
            int shift = focused ? TarotLedgerLayout.PLACEHOLDER_FOCUS_SHIFT : 0;
            c.drawText(textRenderer, placeholder, panel.fieldX() + shift, panel.fieldY(), InventoryCardPaint.FAINT,
                    true);
        } else {
            String count = v.cells().size() + "/" + totalCount;
            c.drawText(textRenderer, count, panel.countRight() - textRenderer.getWidth(count), panel.fieldY(),
                    InventoryCardPaint.FAINT, true);
        }
    }

    private void drawList(DrawContext c, View v) {
        Rect view = panel.viewport();
        int scroll = v.scroll();
        c.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        if (v.cells().isEmpty()) {
            drawEmpty(c);
        } else {
            List<Block> headers = new ArrayList<>();
            for (Block block : v.content().blocks()) {
                int y = TarotLedgerLayout.headerY(panel, block, scroll);
                if (y + TarotLedgerLayout.HEADER_H > view.y() && y < view.bottom()) {
                    headers.add(block);
                }
            }
            List<CellView> rows = new ArrayList<>();
            for (int i = 0; i < v.cells().size(); i++) {
                Rect rect = TarotLedgerLayout.cell(panel, v.content(), i, scroll);
                if (rect.bottom() > view.y() && rect.y() < view.bottom()) {
                    rows.add(cellView(v, i, rect));
                }
            }
            // Every fill in one batch, then text: batched vertices flush per layer, not in call order.
            // 所有填充放入一个批次，然后再绘制文字：批次按渲染层而非调用顺序提交。
            InventoryCardPaint.batch(c, () -> {
                for (Block block : headers) {
                    drawHeaderMark(c, block, TarotLedgerLayout.headerY(panel, block, scroll));
                }
                for (CellView row : rows) {
                    Rect rect = row.rect();
                    TarotLedgerPaint.rowState(c, rect.x(), rect.y(), rect.right(), rect.height(), row.hovered(),
                            row.selected());
                    drawRowMark(c, row.entry(), rect.x() + TarotLedgerLayout.CELL_GEM_X,
                            rect.y() + TarotLedgerLayout.CELL_GEM_Y, row.bright());
                    TarotReadingLog.Reading reading = row.reading();
                    if (reading != null) {
                        TarotLedgerPaint.stamp(c, row.stampX(), rect.y() + TarotLedgerLayout.STAMP_Y,
                                reading.positive(), TarotLedgerPaint.stampColor(reading.mode(), reading.positive()));
                    }
                }
            });
            for (Block block : headers) {
                drawHeaderText(c, block, TarotLedgerLayout.headerY(panel, block, scroll));
            }
            for (CellView row : rows) {
                int textY = row.rect().y() + TarotLedgerLayout.CELL_TEXT_Y;
                c.drawText(textRenderer, row.name(), row.rect().x() + TarotLedgerLayout.CELL_TEXT_X, textY,
                        row.bright() ? InventoryCardPaint.TEXT_HI : InventoryCardPaint.TEXT, true);
                if (row.tagX() >= 0) {
                    c.drawText(textRenderer, selfTag, row.tagX(), textY, InventoryCardPaint.FAINT, true);
                }
            }
        }
        drawOverlay(c, v);
        c.disableScissor();
        drawRod(c, v);
    }

    /**
     * The running head and the fades share ONE z+1 layer. Cell glyphs sit at +0.03, so a same-z fill would let CJK
     * pixels show through. The running head can sit above the viewport while pushed out, hence the scissor.
     * 置顶标题与渐隐共用同一个 z+1 图层。单元文字位于 +0.03，同层填充会透出汉字像素。置顶标题被推出时可能高于视口，
     * 因此在裁剪区内绘制。
     */
    private void drawOverlay(DrawContext c, View v) {
        int scroll = v.scroll();
        Optional<RunningHead> head = TarotLedgerLayout.runningHead(panel, v.content(), scroll);
        int max = TarotLedgerLayout.maxScroll(panel, v.content());
        boolean fadeTop = scroll > 0;
        boolean fadeBottom = scroll < max;
        if (head.isEmpty() && !fadeTop && !fadeBottom) {
            return;
        }
        int left = panel.viewport().x();
        int right = panel.rodX();
        int fadeTopY = TarotLedgerLayout.fadeTop(panel, v.content(), scroll);
        MatrixStack matrices = c.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 1);
        InventoryCardPaint.batch(c, () -> {
            head.ifPresent(h -> {
                c.fill(left, h.y(), right, h.bottom(), InventoryCardPaint.BODY);
                drawHeaderMark(c, h.block(), h.y());
            });
            if (fadeTop) {
                TarotLedgerPaint.fadeTop(c, left, fadeTopY, right);
            }
            if (fadeBottom) {
                TarotLedgerPaint.fadeBottom(c, left, panel.viewBottom(), right);
            }
        });
        head.ifPresent(h -> drawHeaderText(c, h.block(), h.y()));
        matrices.pop();
    }

    /**
     * The rod at z+2, above the fades; identity mode runs a faction-coloured tick under it at each section start.
     * 滑杆位于 z+2，高于渐隐；身份模式在其下方于每个分区起点绘制阵营色刻度。
     */
    private void drawRod(DrawContext c, View v) {
        Content content = v.content();
        Optional<Rect> thumb = TarotLedgerLayout.thumb(panel, content, v.scroll());
        if (thumb.isEmpty()) {
            return;
        }
        Rect rod = thumb.get();
        boolean hot = v.dragging() || v.hover().kind() == HitKind.GUTTER;
        List<TarotLedgerPaint.Tick> ticks = new ArrayList<>();
        if (identity) {
            for (Block block : content.blocks()) {
                ticks.add(new TarotLedgerPaint.Tick(block.headerY(), sectionColor(block.section())));
            }
        }
        MatrixStack matrices = c.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 2);
        InventoryCardPaint.batch(c, () -> TarotLedgerPaint.scrollbarDark(c, rod.x(), panel.viewTop(),
                panel.viewHeight(), rod.y(), rod.height(), hot, content.height(), ticks));
        matrices.pop();
    }

    /**
     * No match shows the magnifier, the "no match" line and how to get back; an empty roster shows the old
     * "no targets" line alone.
     * 无匹配时显示放大镜、“没有匹配”与返回提示；名单本身为空时只显示原有的“没有可选择的目标”。
     */
    private void drawEmpty(DrawContext c) {
        int centerX = panel.centerX();
        int top = panel.viewTop();
        if (totalCount == 0) {
            drawCentered(c, Text.translatable("screen.sparkwitch.tarot.empty"), centerX,
                    top + TarotLedgerLayout.EMPTY_TEXT_Y, InventoryCardPaint.MUTED);
            return;
        }
        InventoryCardPaint.batch(c, () -> TarotLedgerPaint.magnifier(c, centerX - 4,
                top + TarotLedgerLayout.EMPTY_MARK_Y, InventoryCardPaint.FAINT));
        drawCentered(c, TarotLedgerText.noMatch(identity), centerX, top + TarotLedgerLayout.EMPTY_TEXT_Y,
                InventoryCardPaint.MUTED);
        drawCentered(c, Text.translatable("screen.sparkwitch.tarot.none.hint"), centerX,
                top + TarotLedgerLayout.EMPTY_HINT_Y, InventoryCardPaint.FAINT);
    }

    private void drawCentered(DrawContext c, Text text, int centerX, int y, int color) {
        c.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, true);
    }

    /**
     * The Esc keycap and forfeit note, the paid question (or the hint) and the Divine button. A hint is an
     * instruction and is never truncated; only a question, which is data, may be shortened.
     * Esc 按键帽与放弃说明、付费问句（或提示）以及“占卜”按钮。提示属于操作说明，绝不截断；只有属于数据的问句可以缩短。
     */
    private void drawFooter(DrawContext c, View v, FooterLine line, Footer footer) {
        Rect view = panel.viewport();
        Rect keycap = footer.keycap();
        Rect button = footer.button();
        Entry entry = line.entry();
        boolean enabled = entry != null && !v.submitted();
        boolean hot = v.hover().kind() == HitKind.BUTTON;
        OrderedText message = null;
        if (footer.messageShown()) {
            message = entry != null && footer.messageEllipsized()
                    ? TarotLedgerText.fitQuestion(textRenderer, identity, entry.label(), footer.messageMaxWidth())
                    : line.text().asOrderedText();
        }
        int messageWidth = message == null ? 0 : textRenderer.getWidth(message);
        boolean markShown = message != null && entry != null;
        InventoryCardPaint.batch(c, () -> {
            TarotLedgerPaint.etched(c, view.x(), view.right(), panel.footerRuleY());
            TarotLedgerPaint.keycap(c, keycap.x(), keycap.y(), keycap.width());
            TarotLedgerPaint.tagButton(c, button.x(), button.y(), button.width(), enabled, hot);
            if (markShown) {
                drawRowMark(c, entry, footer.markX(messageWidth), footer.markY(), true);
            }
        });
        c.drawText(textRenderer, ESC_LABEL, footer.keyLabelX(), keycap.y() + TarotLedgerLayout.KEYCAP_LABEL_Y,
                InventoryCardPaint.FAINT, false);
        if (footer.forfeitShown()) {
            c.drawText(textRenderer, forfeit, footer.forfeitX(), footer.textY(), InventoryCardPaint.MUTED, true);
        }
        if (message != null) {
            c.drawText(textRenderer, message, footer.messageX(messageWidth), footer.textY(),
                    entry == null ? InventoryCardPaint.FAINT : InventoryCardPaint.TEXT, true);
        }
        c.drawText(textRenderer, divine, footer.buttonLabelX(), button.y() + TarotLedgerLayout.BUTTON_LABEL_Y,
                TarotLedgerPaint.tagButtonLabelColor(enabled, hot), false);
    }

    // ================================================================ cells

    private CellView cellView(View v, int index, Rect rect) {
        Entry entry = v.cells().get(index);
        TarotReadingLog.Reading reading = reading(entry).orElse(null);
        String name = ellipsize(entry.label(), nameMaxWidth(entry, rect.width(), reading != null));
        int trail = rect.x() + TarotLedgerLayout.CELL_TEXT_X + textRenderer.getWidth(name);
        int tagX = -1;
        if (entry.self()) {
            tagX = trail + TarotLedgerLayout.TRAIL_GAP;
            trail = tagX + selfTagWidth;
        }
        boolean hovered = v.hover().kind() == HitKind.CELL && v.hover().cell() == index;
        return new CellView(entry, rect, name, tagX, trail + TarotLedgerLayout.TRAIL_GAP, reading, hovered,
                v.isSelected(entry));
    }

    /** Only the reader's own received results; never derived from round state. 仅来自本人已收到的结果，从不由对局状态推断。 */
    private Optional<TarotReadingLog.Reading> reading(Entry entry) {
        return TarotDivinationClientState.readingLog().latest(mode, entry.target());
    }

    private int nameMaxWidth(Entry entry, int cellWidth, boolean stamped) {
        return TarotLedgerLayout.nameMaxWidth(cellWidth, entry.self() ? selfTagWidth : 0, stamped);
    }

    private void drawHeaderMark(DrawContext c, Block block, int headerY) {
        if (identity) {
            TarotLedgerPaint.sectionMark(c, panel.headerMarkX(), headerY + TarotLedgerLayout.HEADER_MARK_Y,
                    sectionColor(block.section()), false);
        }
    }

    private void drawHeaderText(DrawContext c, Block block, int headerY) {
        MutableText tail = TarotLedgerText.count(identity, block.count())
                .withColor(InventoryCardPaint.FAINT & 0xFFFFFF);
        InventoryCardPaint.sectionHeader(c, textRenderer, TarotLedgerText.sectionLabel(block.section()).getString(),
                panel.headerLabelX(), headerY + TarotLedgerLayout.HEADER_LABEL_Y, panel.headerRight(), tail, 0, true);
    }

    /** Role gem for roles; a colourless brass bullet for players (leak guard). 职业用宝石；玩家用无色黄铜菱形（防泄露）。 */
    private static void drawRowMark(DrawContext c, Entry entry, int x, int y, boolean bright) {
        if (entry.section() == Section.PLAYERS) {
            TarotLedgerPaint.playerBullet(c, x, y, bright);
        } else {
            TarotLedgerPaint.roleGem(c, x, y, entry.rgb(), bright);
        }
    }

    // ================================================================ text

    private FooterLine footerLine(@Nullable Entry entry) {
        Text text = entry == null ? TarotLedgerText.hint(identity) : TarotLedgerText.question(identity, entry.label());
        return new FooterLine(entry, text, textRenderer.getWidth(text));
    }

    private Footer footer(FooterLine line) {
        return TarotLedgerLayout.footer(panel, textRenderer.getWidth(ESC_LABEL), textRenderer.getWidth(forfeit),
                line.width(), line.entry() != null, textRenderer.getWidth(divine));
    }

    private String ellipsize(String text, int maxWidth) {
        return TarotDivinationHudRenderer.fit(textRenderer, text, maxWidth);
    }

    private int sectionColor(Section section) {
        return sectionColors.getOrDefault(section, InventoryCardPaint.BRASS_HI & 0xFFFFFF);
    }

    /**
     * The screen state one frame is drawn from. {@code armed} is true when {@code selected} was chosen by a click or
     * the arrow keys rather than by typing down to one match.
     * 绘制一帧所依据的界面状态。{@code selected} 由单击或方向键选中（而非输入到只剩一个匹配时自动选中）时
     * {@code armed} 为 true。
     */
    record View(Content content, List<Entry> cells, String query, @Nullable Entry selected, boolean armed,
                Hit hover, int scroll, boolean dragging, boolean submitted, boolean searchFocused) {
        boolean isSelected(Entry entry) {
            return selected != null && selected.target().equals(entry.target());
        }
    }

    /** The footer's question (a selection) or hint (none). 页脚的问句（有选中项）或提示（无选中项）。 */
    private record FooterLine(@Nullable Entry entry, Text text, int width) {
    }

    /** One visible cell, measured once per frame. 每帧测量一次的可见单元。 */
    private record CellView(Entry entry, Rect rect, String name, int tagX, int stampX,
                            @Nullable TarotReadingLog.Reading reading, boolean hovered, boolean selected) {
        boolean bright() {
            return hovered || selected;
        }
    }
}
