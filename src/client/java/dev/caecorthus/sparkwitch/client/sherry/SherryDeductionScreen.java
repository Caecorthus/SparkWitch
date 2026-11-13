package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkfactionapi.client.api.card.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.Footer;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.Grid;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.Hit;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.HitKind;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.Pager;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.Panel;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.Rect;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.TitleBar;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionPaint.Palette;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.net.ConfirmSherryDeductionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The 推演 candidate panel, in the Harpy Express house grammar (owner 2026-11-11: redesigned): a centred framed
 * panel with a title band and the price, one rule line, the living players the server found inside reach as ruled
 * rows (face + name), a footer with the current choice and the Cancel / Confirm buttons. Pick a row, then confirm
 * (the button, Enter, or a second click on the chosen row); the confirm is the single paid submit and nothing is
 * predicted locally (the result arrives as a server message and, on a killer, as her private outline). A target
 * already on the 20 s reveal cycle carries a 追踪中 chip and cannot be picked (the server refuses it before any
 * charge); a badge killer on the slower cycle carries a lead chip and can still be deduced. The panel closes by
 * itself once the player is no longer an active 橘雪莉. Geometry and hit-testing belong to
 * {@link SherryDeductionLayout}, colours and fills to {@link SherryDeductionPaint}. Everything is re-checked
 * server-side.
 * 推演候选面板，采用哈比特快面板语法（所有者 2026-11-11：重新设计）：居中的带框面板，含标题带与价格、一行规则说明、
 * 以带横线的行（头像 + 名字）列出服务端在射程内找到的存活玩家，页脚显示当前选择与「取消 / 确认」按钮。选中一行后确认
 * （按钮、回车，或再次点击已选中的行）；确认才是唯一的付费提交，本地不做任何预判（结果以服务端消息返回，选中杀手时还会
 * 出现她私有的描边）。已处于 20 秒揭示周期的目标带有「追踪中」徽记且不可选择（服务端会在扣费前拒绝）；处于较慢周期的
 * 徽章凶手带有「线索」徽记，仍可被推演。玩家不再是活动中的橘雪莉时界面自动关闭。几何与命中判定归
 * {@link SherryDeductionLayout}，颜色与填充归 {@link SherryDeductionPaint}。一切由服务端重新校验。
 */
public final class SherryDeductionScreen extends Screen {
    /** Wathe's coin, a private-use font glyph. Wathe 金币，字体私用区图标。 */
    private static final String COIN_GLYPH = "";
    /** Above the faces and every label. 位于头像与所有文字之上。 */
    private static final float TOOLTIP_Z = 400.0F;

    /** How a candidate stands with her trackers. 候选与她的追踪状态。 */
    private enum Lead {
        NONE,
        /** A badge killer on the slower cycle: marked, still selectable. 处于较慢周期的徽章凶手：有标记，仍可选择。 */
        BADGE,
        /** Already on the deduction cycle: marked, not selectable. 已处于推演周期：有标记，不可选择。 */
        TRACKED
    }

    /**
     * One candidate as drawn. {@code index} is the roster index; {@code shown} is the name cut to its cell and
     * {@code cut} tells whether it lost characters (then the row carries a tooltip).
     * 一名候选的绘制信息。{@code index} 为名单下标；{@code shown} 为截断到单元宽度的名字，{@code cut} 表示是否被截断
     * （被截断的行带提示框）。
     */
    private record Row(int index, UUID uuid, String name, Lead lead, String chip, int chipWidth, String shown,
                       boolean cut) {
        boolean selectable() {
            return lead != Lead.TRACKED;
        }
    }

    private final List<UUID> targets;
    private final List<String> names;
    private final int price;

    // Rebuilt by init() (resize, GUI scale) and when her trackers change. 由 init() 以及追踪状态变化时重建。
    private Panel panel;
    private List<Row> rows = List.of();
    private List<OrderedText> hintLines = List.of();
    private Lead[] leads = new Lead[0];
    private Text priceText = Text.empty();
    private Text cancelLabel = Text.empty();
    private Text confirmLabel = Text.empty();

    private int selected = -1;
    private int page;
    private boolean submitted;
    /** Wheel input, in notches, not yet spent on a page. 尚未生效的滚轮量（单位为格）。 */
    private double wheelCarry;
    private Hit hover = Hit.NONE;

    public SherryDeductionScreen(List<UUID> targets, List<String> names, int price) {
        super(Text.translatable("screen.sparkwitch.sherry.deduction.title"));
        // The server bounds the roster; clamp again so a malformed list can never flood the panel.
        // 服务端已限制名单长度；此处再次截断，畸形名单也无法撑爆面板。
        this.targets = List.copyOf(targets.subList(0, Math.min(targets.size(), SherryRules.MAX_CANDIDATES)));
        this.names = List.copyOf(names);
        this.price = price;
    }

    @Override
    protected void init() {
        leads = readLeads();
        rebuild();
    }

    /**
     * Her owner-synced trackers decide the chips: a target on the deduction cycle (or faster) is refused by the
     * server before any charge, so it is not selectable here either; a slower badge tracker stays selectable.
     * 徽记取决于仅同步给她本人的追踪状态：已处于推演周期（或更快）的目标会被服务端在扣费前拒绝，因此这里同样不可选择；
     * 较慢的徽章追踪目标仍可选择。
     */
    private Lead[] readLeads() {
        Lead[] result = new Lead[targets.size()];
        SherryComponent component = this.client == null || this.client.player == null
                ? null : SherryComponent.KEY.get(this.client.player);
        for (int i = 0; i < result.length; i++) {
            int interval = component == null ? 0 : component.getTrackedIntervalTicks(targets.get(i));
            result[i] = interval <= 0 ? Lead.NONE
                    : interval <= SherryRules.DEDUCTION_REVEAL_INTERVAL_TICKS ? Lead.TRACKED : Lead.BADGE;
        }
        return result;
    }

    private void rebuild() {
        priceText = Text.literal(price + COIN_GLYPH);
        cancelLabel = Text.translatable("screen.sparkwitch.sherry.deduction.cancel");
        confirmLabel = Text.translatable("screen.sparkwitch.sherry.deduction.confirm", price);
        String tracked = Text.translatable("screen.sparkwitch.sherry.deduction.tracked").getString();
        String badge = Text.translatable("screen.sparkwitch.sherry.deduction.badge_lead").getString();
        int trackedWidth = SherryDeductionLayout.chipWidth(this.textRenderer.getWidth(tracked));
        int badgeWidth = SherryDeductionLayout.chipWidth(this.textRenderer.getWidth(badge));

        int widestName = 0;
        int widestMarked = 0;
        for (int i = 0; i < targets.size(); i++) {
            int nameWidth = this.textRenderer.getWidth(nameOf(i));
            widestName = Math.max(widestName, nameWidth);
            if (leads[i] != Lead.NONE) {
                widestMarked = Math.max(widestMarked, SherryDeductionLayout.neededWidth(nameWidth,
                        chipWidth(leads[i], trackedWidth, badgeWidth)));
            }
        }
        int panelWidth = SherryDeductionLayout.panelWidth(this.width);
        List<OrderedText> wrapped = this.textRenderer.wrapLines(
                Text.translatable("screen.sparkwitch.sherry.deduction.hint"),
                SherryDeductionLayout.hintBudget(panelWidth));
        hintLines = wrapped.size() > SherryDeductionLayout.HINT_MAX_LINES
                ? wrapped.subList(0, SherryDeductionLayout.HINT_MAX_LINES) : wrapped;
        panel = SherryDeductionLayout.panel(this.width, this.height, targets.size(), widestName, widestMarked,
                hintLines.size());

        List<Row> built = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            Lead lead = leads[i];
            int chipWidth = chipWidth(lead, trackedWidth, badgeWidth);
            String name = nameOf(i);
            String shown = SherryDeductionLayout.trim(name, panel.grid().nameMax(chipWidth),
                    this.textRenderer::getWidth);
            built.add(new Row(i, targets.get(i), name, lead,
                    lead == Lead.TRACKED ? tracked : lead == Lead.BADGE ? badge : "", chipWidth, shown,
                    !shown.equals(name)));
        }
        rows = built;
        if (selected >= 0 && (selected >= rows.size() || !rows.get(selected).selectable())) {
            selected = -1;
        }
        page = SherryDeductionLayout.clampPage(panel.grid(), page);
    }

    private static int chipWidth(Lead lead, int trackedWidth, int badgeWidth) {
        return lead == Lead.TRACKED ? trackedWidth : lead == Lead.BADGE ? badgeWidth : 0;
    }

    @Override
    public void tick() {
        // The HUD's gate: confirmed server, raw role, ACTIVE round, alive, not spectating and not swallowed, so the
        // panel closes in every state in which the server would refuse the paid confirm anyway.
        // 与 HUD 相同的门槛：已确认服务器、真实职业、ACTIVE 回合、存活、非旁观、未被吞下；凡是服务端会拒绝付费确认的状态，
        // 界面都会自动关闭。
        if (this.client == null || !SherryClient.isActiveSherry(this.client.player)) {
            close();
            return;
        }
        // A badge holder may die while the panel is open: the new tracker shows at once.
        // 面板打开期间徽章持有者可能死亡：新的追踪状态立即显示。
        Lead[] now = readLeads();
        if (!Arrays.equals(now, leads)) {
            leads = now;
            rebuild();
        }
    }

    // ================================================================ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (submitted) {
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                // The paid confirm; a no-op until a row is chosen. 付费确认；选中一行之前无效。
                submit();
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_LEFT -> {
                turnPage(-1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_RIGHT -> {
                turnPage(1);
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (submitted || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Hit hit = hitAt(mouseX, mouseY);
        switch (hit.kind()) {
            case ROW -> {
                Row row = rowAt(hit.index());
                if (row == null || !row.selectable()) {
                    return true;
                }
                if (row.index() == selected) {
                    // A second click on the chosen row confirms (Tarot behaviour). 再次点击已选中的行即确认（同塔罗）。
                    submit();
                } else {
                    select(row.index());
                }
            }
            case PREV -> turnPage(-1);
            case NEXT -> turnPage(1);
            case CANCEL -> close();
            case CONFIRM -> submit();
            default -> {
                // A click anywhere else, inside or outside the panel, neither picks nor closes.
                // 点击其他位置（面板内外）既不选择也不关闭。
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Trackpads send many sub-notch amounts: carry them until they add up to one page. A change of direction
        // drops what was carried. 触控板会发送大量不足一格的滚动量：累计到一页再生效；改变方向时丢弃已累计的量。
        if (verticalAmount * wheelCarry < 0) {
            wheelCarry = 0;
        }
        wheelCarry += verticalAmount;
        if (Math.abs(wheelCarry) >= 1) {
            turnPage(wheelCarry > 0 ? -1 : 1);
            wheelCarry = 0;
        }
        return true;
    }

    private void select(int index) {
        if (index < 0 || index >= targets.size()) {
            return;
        }
        selected = index;
        playSound(SoundEvents.UI_BUTTON_CLICK.value());
    }

    private void turnPage(int delta) {
        int before = page;
        page = SherryDeductionLayout.clampPage(panel.grid(), before + delta);
        if (page != before) {
            playSound(SoundEvents.ITEM_BOOK_PAGE_TURN);
        }
    }

    private void submit() {
        if (submitted || selected < 0 || selected >= targets.size() || !rows.get(selected).selectable()
                || !ClientPlayNetworking.canSend(ConfirmSherryDeductionC2SPacket.ID)) {
            return;
        }
        submitted = true;
        ClientPlayNetworking.send(new ConfirmSherryDeductionC2SPacket(targets.get(selected)));
        playSound(SoundEvents.UI_BUTTON_CLICK.value());
        close();
    }

    /**
     * Restores the vanilla widget feedback the hand-drawn controls lost: a click for a pick and the paid confirm, the
     * book page-turn for paging. 补回手绘控件缺失的原版反馈音：选择与付费确认播放点击音，翻页播放书页声。
     */
    private void playSound(SoundEvent sound) {
        if (this.client != null) {
            this.client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F));
        }
    }

    private String nameOf(int index) {
        return index < names.size() ? names.get(index) : "?";
    }

    // ================================================================ frame parts

    private int slotsOnPage() {
        return SherryDeductionLayout.slotsOnPage(panel.grid(), rows.size(), page);
    }

    /** The row in page slot {@code slot}, or null. 页内第 slot 个位置的行；没有时为 null。 */
    private @Nullable Row rowAt(int slot) {
        int index = SherryDeductionLayout.firstIndex(panel.grid(), page) + slot;
        return slot >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    private Text pageText() {
        return Text.literal((page + 1) + "/" + panel.grid().pageCount());
    }

    private TitleBar titleBar() {
        return SherryDeductionLayout.titleBar(panel, this.textRenderer.getWidth(priceText));
    }

    private Pager pager() {
        return SherryDeductionLayout.pager(panel, this.textRenderer.getWidth(pageText()));
    }

    private Text statusText() {
        return selected >= 0 && selected < rows.size()
                ? Text.translatable("screen.sparkwitch.sherry.deduction.selected", rows.get(selected).name())
                : Text.translatable("screen.sparkwitch.sherry.deduction.none");
    }

    private Footer footer(Text status) {
        return SherryDeductionLayout.footer(panel, this.textRenderer.getWidth(status),
                this.textRenderer.getWidth(cancelLabel), this.textRenderer.getWidth(confirmLabel));
    }

    private Hit hitAt(double mouseX, double mouseY) {
        return SherryDeductionLayout.hit(panel, titleBar(), pager(), footer(statusText()), slotsOnPage(), mouseX,
                mouseY);
    }

    // ================================================================ render

    /**
     * Scrim only, without vanilla's blur: the round keeps running behind the panel. {@code Screen.render} calls this
     * exactly once per frame.
     * 只画遮罩，不使用原版模糊：面板打开时对局仍在进行。{@code Screen.render} 每帧只调用一次。
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SherryDeductionPaint.PALETTE.scrim);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Text status = statusText();
        TitleBar bar = titleBar();
        Pager pager = pager();
        Footer footer = footer(status);
        int slots = slotsOnPage();
        hover = submitted ? Hit.NONE
                : SherryDeductionLayout.hit(panel, bar, pager, footer, slots, mouseX, mouseY);
        boolean canConfirm = selected >= 0;
        Rect frame = panel.frame();

        // Every fill in one batch, then faces, then text: batched vertices flush per layer, not in call order.
        // 所有填充放入一个批次，然后绘制头像与文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            SherryDeductionPaint.frame(context, frame);
            SherryDeductionPaint.titleBand(context, frame);
            SherryDeductionPaint.magnifier(context, frame.x() + SherryDeductionLayout.TITLE_ICON_X,
                    frame.y() + SherryDeductionLayout.TITLE_ICON_Y);
            SherryDeductionPaint.priceChip(context, bar.price());
            SherryDeductionPaint.hintStrip(context, frame, panel.hintRuleY());
            for (int slot = 0; slot < slots; slot++) {
                Row row = rowAt(slot);
                Rect cell = SherryDeductionLayout.cell(panel, slot);
                boolean chosen = row.index() == selected;
                boolean hot = rowHovered(slot) && row.selectable();
                SherryDeductionPaint.rowState(context, cell, hot, chosen);
                SherryDeductionPaint.rowRule(context, cell);
                SherryDeductionPaint.face(context, cell, hot, chosen, entry(row.uuid()) == null);
                if (row.lead() == Lead.NONE) {
                    continue;
                }
                boolean tracked = row.lead() == Lead.TRACKED;
                if (panel.grid().chipsBelow()) {
                    SherryDeductionPaint.mark(context, cell.x() + SherryDeductionLayout.NAME_X,
                            cell.y() + SherryDeductionLayout.MARK_ICON_Y, tracked);
                } else if (tracked) {
                    SherryDeductionPaint.trackedChip(context, SherryDeductionLayout.chip(cell, row.chipWidth()));
                } else {
                    SherryDeductionPaint.leadChip(context, SherryDeductionLayout.chip(cell, row.chipWidth()));
                }
            }
            if (pager.shown()) {
                SherryDeductionPaint.arrow(context, pager.prev(), true, page > 0, hover.kind() == HitKind.PREV);
                SherryDeductionPaint.arrow(context, pager.next(), false,
                        page < panel.grid().pageCount() - 1, hover.kind() == HitKind.NEXT);
            }
            SherryDeductionPaint.etched(context, frame.x() + SherryDeductionLayout.FOOTER_X,
                    frame.right() - SherryDeductionLayout.FOOTER_X, panel.footerRuleY());
            if (!footer.pill().isEmpty()) {
                SherryDeductionPaint.pill(context, footer.pill(), canConfirm);
            }
            SherryDeductionPaint.cancelButton(context, footer.cancel(), hover.kind() == HitKind.CANCEL);
            SherryDeductionPaint.confirmButton(context, footer.confirm(), canConfirm, hover.kind() == HitKind.CONFIRM);
        });

        boolean anyDimmed = false;
        for (int slot = 0; slot < slots; slot++) {
            Row row = rowAt(slot);
            PlayerListEntry entry = entry(row.uuid());
            if (entry != null) {
                Rect cell = SherryDeductionLayout.cell(panel, slot);
                // Face, then the hat layer. / 先画脸，再画帽子层。
                PlayerSkinDrawer.draw(context, entry.getSkinTextures(), cell.x() + SherryDeductionLayout.FACE_X,
                        cell.y() + SherryDeductionLayout.FACE_Y, SherryDeductionLayout.FACE_SIZE);
            }
            anyDimmed |= !row.selectable();
        }
        if (anyDimmed) {
            // Over the faces: a candidate who cannot be picked is washed out. 位于头像之上：不可选的候选被淡化。
            InventoryCardPaint.batch(context, () -> {
                for (int slot = 0; slot < slots; slot++) {
                    if (!rowAt(slot).selectable()) {
                        SherryDeductionPaint.faceDim(context, SherryDeductionLayout.cell(panel, slot));
                    }
                }
            });
        }

        drawText(context, bar, pager, footer, status, slots, canConfirm);
        drawTooltip(context, tooltipAt(), mouseX, mouseY);
    }

    private boolean rowHovered(int slot) {
        return hover.kind() == HitKind.ROW && hover.index() == slot;
    }

    private @Nullable PlayerListEntry entry(UUID uuid) {
        ClientPlayNetworkHandler handler = this.client == null ? null : this.client.getNetworkHandler();
        return handler == null ? null : handler.getPlayerListEntry(uuid);
    }

    private void drawText(DrawContext context, TitleBar bar, Pager pager, Footer footer, Text status, int slots,
                          boolean canConfirm) {
        Palette palette = SherryDeductionPaint.PALETTE;
        Rect frame = panel.frame();
        context.drawText(this.textRenderer,
                InventoryCardPaint.ellipsize(this.textRenderer, this.title.getString(), bar.titleMaxWidth()),
                bar.titleX(), bar.textY(), palette.title, false);
        InventoryCardPaint.iconText(context, this.textRenderer, priceText, bar.priceTextX(),
                bar.price().y() + SherryDeductionLayout.PRICE_TEXT_Y, palette.priceText, false);
        for (int line = 0; line < hintLines.size(); line++) {
            context.drawText(this.textRenderer, hintLines.get(line), frame.x() + SherryDeductionLayout.HINT_X,
                    frame.y() + SherryDeductionLayout.HINT_Y + line * SherryDeductionLayout.HINT_LINE_H,
                    palette.muted, true);
        }
        for (int slot = 0; slot < slots; slot++) {
            Row row = rowAt(slot);
            Rect cell = SherryDeductionLayout.cell(panel, slot);
            boolean bright = row.index() == selected || (rowHovered(slot) && row.selectable());
            int color = !row.selectable() ? palette.greyed : bright ? palette.textHi : palette.text;
            boolean marked = row.lead() != Lead.NONE;
            boolean below = marked && panel.grid().chipsBelow();
            context.drawText(this.textRenderer, row.shown(), cell.x() + SherryDeductionLayout.NAME_X,
                    cell.y() + (below ? SherryDeductionLayout.NAME_Y_STACKED : SherryDeductionLayout.NAME_Y), color,
                    true);
            if (marked) {
                int markColor = SherryDeductionPaint.markColor(row.lead() == Lead.TRACKED);
                int labelOffset = SherryDeductionLayout.CHIP_ICON_W + SherryDeductionLayout.CHIP_ICON_GAP;
                if (below) {
                    context.drawText(this.textRenderer, row.chip(),
                            cell.x() + SherryDeductionLayout.NAME_X + labelOffset,
                            cell.y() + SherryDeductionLayout.MARK_Y, markColor, true);
                } else {
                    Rect chip = SherryDeductionLayout.chip(cell, row.chipWidth());
                    context.drawText(this.textRenderer, row.chip(),
                            chip.x() + SherryDeductionLayout.CHIP_PAD + labelOffset,
                            chip.y() + SherryDeductionLayout.CHIP_TEXT_Y, markColor, false);
                }
            }
        }
        if (pager.shown()) {
            context.drawText(this.textRenderer, pageText(), pager.textX(), pager.textY(), palette.muted,
                    true);
        }
        if (!footer.pill().isEmpty()) {
            Rect pill = footer.pill();
            context.drawText(this.textRenderer,
                    InventoryCardPaint.ellipsize(this.textRenderer, status.getString(), footer.pillTextMax()),
                    pill.x() + SherryDeductionLayout.PILL_PAD + SherryDeductionLayout.PILL_ICON_W
                            + SherryDeductionLayout.PILL_ICON_GAP,
                    pill.y() + SherryDeductionLayout.PILL_TEXT_Y, canConfirm ? palette.pillText : palette.idleText,
                    false);
        }
        context.drawText(this.textRenderer, cancelLabel, footer.cancel().x() + SherryDeductionLayout.BUTTON_PAD,
                footer.cancel().y() + SherryDeductionLayout.BUTTON_TEXT_Y,
                SherryDeductionPaint.cancelLabelColor(hover.kind() == HitKind.CANCEL), false);
        InventoryCardPaint.iconText(context, this.textRenderer, confirmLabel,
                footer.confirm().x() + SherryDeductionLayout.BUTTON_LABEL_X,
                footer.confirm().y() + SherryDeductionLayout.BUTTON_TEXT_Y,
                SherryDeductionPaint.confirmLabelColor(canConfirm, hover.kind() == HitKind.CONFIRM), false);
    }

    // ================================================================ tooltip

    /**
     * The price chip explains the charge and the cooldown; a tracked row says why it cannot be picked; a badge-lead
     * row explains its chip; any other row shows its full name only when the name was cut.
     * 价格徽章说明花费与冷却；追踪中的行说明为何不可选；徽章线索行解释其徽记；其他行仅在名字被截断时显示完整名字。
     */
    private List<Text> tooltipAt() {
        if (hover.kind() == HitKind.PRICE) {
            return List.of(Text.translatable("screen.sparkwitch.sherry.deduction.price", price));
        }
        if (hover.kind() != HitKind.ROW) {
            return List.of();
        }
        Row row = rowAt(hover.index());
        if (row == null) {
            return List.of();
        }
        return switch (row.lead()) {
            case TRACKED -> List.of(Text.translatable("message.sparkwitch.sherry.deduction.tracked", row.name()));
            case BADGE -> row.cut()
                    ? List.of(Text.literal(row.name()),
                            Text.translatable("screen.sparkwitch.sherry.deduction.badge_lead.tooltip"))
                    : List.of(Text.translatable("screen.sparkwitch.sherry.deduction.badge_lead.tooltip"));
            case NONE -> row.cut() ? List.of(Text.literal(row.name())) : List.of();
        };
    }

    private void drawTooltip(DrawContext context, List<Text> text, int mouseX, int mouseY) {
        List<OrderedText> lines = new ArrayList<>();
        for (Text part : text) {
            lines.addAll(this.textRenderer.wrapLines(part, SherryDeductionLayout.TIP_MAX_W));
        }
        if (lines.isEmpty()) {
            return;
        }
        int tipWidth = 0;
        for (OrderedText line : lines) {
            tipWidth = Math.max(tipWidth, this.textRenderer.getWidth(line));
        }
        Rect box = SherryDeductionLayout.tooltip(this.width, this.height, mouseX, mouseY, tipWidth,
                SherryDeductionLayout.tooltipHeight(lines.size()));
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, TOOLTIP_Z);
        InventoryCardPaint.batch(context, () -> SherryDeductionPaint.tooltip(context, box));
        for (int line = 0; line < lines.size(); line++) {
            context.drawText(this.textRenderer, InventoryCardPaint.untintIcons(lines.get(line)), box.x(),
                    box.y() + line * SherryDeductionLayout.TIP_LINE_H, SherryDeductionPaint.PALETTE.tipText, true);
        }
        matrices.pop();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
