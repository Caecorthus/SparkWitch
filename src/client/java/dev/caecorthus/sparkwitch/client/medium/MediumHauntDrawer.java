package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkfactionapi.client.api.card.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout;
import dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorSlots;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerLayout;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.SubmitMediumHauntTargetC2SPacket;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The 招魂 drawer inside Wathe's inventory screen (owner pick 2026-11-13: face tiles, replacing the old key-opened
 * menu): a Harpy Express panel under the hotbar block with the price chip and bound count in its title band, the
 * candidates as paged face tiles, and a footer with the pager, the message and the confirm button. A click on a face
 * only selects (free); the paid act is the confirm button, which sends {@code submit_medium_haunt_target} once and then
 * stays disabled until the binding syncs back or {@link #PENDING_TIMEOUT_MS} passes, so a double click can never pay
 * twice. The candidates are every online round member but the local player ({@link MediumRules#mayHaunt}), sorted by
 * name; nothing is filtered, ordered or tinted by death, so the drawer leaks no deaths. Bound faces stay listed, dimmed
 * with the teal wisp badge, and inert. The whole drawer is ONE widget: SparkFactionAPI's inventory info card treats it
 * as a blocker and lays itself out around it, and every click inside it is consumed so none reaches Wathe's slot or
 * drop handling. Presentation only; the server re-checks the gate, the price, the target and the binding.
 * Wathe 背包界面内的招魂抽屉（所有者 2026-11-13 选定：头像格，取代原先按键打开的菜单）：热栏块下方的哈比特快面板，标题带内有
 * 价签与已招魂人数，候选人以分页头像格列出，页脚为翻页、提示消息与确认按钮。点击头像只是选中（免费）；付费行为是确认按钮，
 * 它只发送一次 {@code submit_medium_haunt_target}，随后保持禁用，直到绑定同步回来或超过 {@link #PENDING_TIMEOUT_MS}，因此连点
 * 绝不会重复扣费。候选人为除本地玩家外的所有在线本局成员（{@link MediumRules#mayHaunt}），按名字排序；不按生死过滤、排序或
 * 着色，抽屉因此不会泄露谁已死亡。已招魂的头像仍然列出，压暗并带青绿鬼火角标，且不可选。整个抽屉是「一个」控件：
 * SparkFactionAPI 的背包信息卡把它当作障碍物并绕开排版，抽屉内的每次点击都会被消费，不会落到 Wathe 的物品格或丢弃逻辑上。
 * 仅用于展示；服务端会重新校验门槛、价格、目标与绑定。
 */
public final class MediumHauntDrawer extends ClickableWidget {
    /** How long the confirm button waits for the binding before it re-arms. / 确认按钮等待绑定同步的时长，超时后重新可用。 */
    static final long PENDING_TIMEOUT_MS = 2000L;
    private static final int HEADSET_GAP = 4;
    private static final int FACE = 16;
    private static final int FACE_INSET = 2;
    private static final int TOOLTIP_Z = 400;
    private static final String COIN = "";

    private final LimitedInventoryScreen screen;
    private final ClientPlayerEntity player;
    private List<Candidate> candidates = List.of();
    private MediumHauntLayout.Layout layout = MediumHauntLayout.layout(0, 0, 0, MediumHauntLayout.SCREEN_MARGIN);
    private int page;
    @Nullable
    private UUID selected;
    @Nullable
    private UUID pending;
    private long pendingAtMs;

    /** One candidate: tab-list UUID and profile name. / 一名候选人：玩家列表中的 UUID 与档案名。 */
    private record Candidate(UUID uuid, String name) {
    }

    private MediumHauntDrawer(LimitedInventoryScreen screen, ClientPlayerEntity player) {
        super(0, 0, 0, 0, Text.translatable("screen.sparkwitch.medium.haunt.title"));
        this.screen = screen;
        this.player = player;
    }

    /**
     * Adds the drawer to the screen when the local player's raw synced role is the Medium; every other player's
     * inventory is untouched. Whether it shows is re-decided every frame by {@link #refresh()}.
     * 本地玩家的真实同步职业为通灵师时把抽屉加入界面；其他玩家的背包不受影响。是否显示由 {@link #refresh()} 每帧重新判定。
     */
    public static @Nullable MediumHauntDrawer attach(LimitedInventoryScreen screen, Consumer<ClickableWidget> addChild) {
        ClientPlayerEntity player = screen.player;
        if (player == null || player.networkHandler == null || !MediumRules.isMedium(player)) {
            return null;
        }
        MediumHauntDrawer drawer = new MediumHauntDrawer(screen, player);
        drawer.refresh();
        addChild.accept(drawer);
        return drawer;
    }

    /**
     * Called at the head of every screen frame: re-checks the gate (the corner HUD's: a live, playing, unswallowed
     * Medium in a running round), rebuilds the candidate list, settles a pending confirm and moves the widget to the
     * current layout. Hidden and inert whenever the gate fails.
     * 每个界面帧开头调用：重新检查门槛（与角落 HUD 相同：进行中对局里存活、参与、未被吞下的通灵师）、重建候选列表、结算待确认的
     * 提交并把控件移到当前布局。门槛不满足时隐藏且不响应。
     */
    public void refresh() {
        boolean shown = player.networkHandler != null
                && MediumHudRenderer.isActiveMedium(player)
                && GameWorldComponent.KEY.get(player.getWorld()).isRunning();
        this.visible = shown;
        this.active = shown;
        if (!shown) {
            selected = null;
            pending = null;
            return;
        }
        candidates = candidates();
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(player);
        if (pending != null && (component.isBound(pending) || Util.getMeasuringTimeMs() - pendingAtMs > PENDING_TIMEOUT_MS)) {
            if (component.isBound(pending) && pending.equals(selected)) {
                selected = null;
            }
            pending = null;
        }
        if (selected != null && (component.isBound(selected) || indexOf(selected) < 0)) {
            selected = null;
        }
        layout = MediumHauntLayout.layout(screen.width, screen.height, candidates.size(), minLeft());
        page = layout.clampPage(page);
        MediumHauntLayout.Rect frame = layout.frame();
        setX(frame.x());
        setY(frame.y());
        // The 1 px panel shadow belongs to the drawer's footprint. / 1 像素面板投影属于抽屉占地。
        setWidth(frame.width() + 1);
        setHeight(frame.height() + 1);
    }

    /** The BEAR headset cell reaches into this band; when it shows, the drawer starts right of it. / 耳机格显示时抽屉从其右侧开始。 */
    private int minLeft() {
        if (!LimitedInventoryArmorSlots.headsetShown(screen, player.playerScreenHandler, player)) {
            return MediumHauntLayout.SCREEN_MARGIN;
        }
        int stripX = (screen.width - LimitedInventoryArmorLayout.STRIP_WIDTH) / 2;
        return LimitedInventoryArmorLayout.headsetPanelX(stripX) + LimitedInventoryArmorLayout.HEADSET_PANEL_WIDTH
                + HEADSET_GAP;
    }

    /**
     * Every online round member but the local player, by name (case-insensitive, then UUID for a stable order). Never
     * filtered, ordered or tinted by death (leak guard).
     * 除本地玩家外的所有在线本局成员，按名字排序（不区分大小写，再按 UUID 保证稳定顺序）。绝不按生死过滤、排序或着色（防泄露）。
     */
    private List<Candidate> candidates() {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        List<Candidate> list = new ArrayList<>();
        for (PlayerListEntry entry : player.networkHandler.getPlayerList()) {
            UUID uuid = entry.getProfile().getId();
            if (MediumRules.mayHaunt(player.getUuid(), uuid, game)) {
                list.add(new Candidate(uuid, entry.getProfile().getName()));
            }
        }
        list.sort(Comparator.comparing((Candidate c) -> c.name().toLowerCase(Locale.ROOT))
                .thenComparing(Candidate::uuid));
        return list;
    }

    private int indexOf(UUID uuid) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).uuid().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------------ painting

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        MediumHauntLayout.Layout l = layout;
        MediumHauntLayout.Rect frame = l.frame();
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(player);
        int price = MediumRules.hauntPrice();
        boolean poor = PlayerShopComponent.KEY.get(player).getBalance() < price;
        int start = page * l.perPage();
        int end = Math.min(candidates.size(), start + l.perPage());
        int hoveredSlot = frame.contains(mouseX, mouseY) ? l.slotAt(mouseX, mouseY) : -1;
        int hoveredIndex = hoveredSlot >= 0 && start + hoveredSlot < end ? start + hoveredSlot : -1;

        String chipLabel = price + COIN;
        int chipWidth = MediumHauntLayout.CHIP_PAD + font.getWidth(chipLabel) + MediumHauntLayout.CHIP_PAD;
        int chipX = frame.right() - MediumHauntLayout.FOOTER_INSET - chipWidth;
        String pageText = (page + 1) + "/" + l.pages();
        int pageTextWidth = font.getWidth(pageText);
        Text buttonLabel = Text.translatable(pending != null
                ? "screen.sparkwitch.medium.haunt.pending"
                : "screen.sparkwitch.medium.haunt.confirm");
        MediumHauntLayout.Rect button = l.button(font.getWidth(buttonLabel));
        boolean canConfirm = canConfirm(component, poor);
        boolean buttonHot = canConfirm && button.contains(mouseX, mouseY);

        InventoryCardPaint.panel(context, frame.x(), frame.y(), frame.width(), frame.height());
        // Fills, then faces (textures flush on their own), then overlays, then text.
        // 先填充，再画头像（贴图自行提交），再画覆盖层，最后画文字。
        InventoryCardPaint.batch(context, () -> {
            TarotLedgerPaint.titleBand(context, frame.x(), frame.y(), frame.width());
            MediumPaint.wisp(context, frame.x() + MediumHauntLayout.TITLE_ICON_X, frame.y() + MediumHauntLayout.TITLE_Y,
                    MediumPaint.TEAL);
            MediumPaint.priceChip(context, chipX, frame.y() + MediumHauntLayout.CHIP_Y, chipWidth);
            for (int i = start; i < end; i++) {
                MediumHauntLayout.Rect tile = l.tile(i - start);
                Candidate candidate = candidates.get(i);
                boolean bound = component.isBound(candidate.uuid());
                MediumPaint.faceTileFrame(context, tile.x(), tile.y(), !bound && i == hoveredIndex,
                        candidate.uuid().equals(selected), bound);
            }
            TarotLedgerPaint.etched(context, frame.x() + MediumHauntLayout.FOOTER_INSET,
                    frame.right() - MediumHauntLayout.FOOTER_INSET, l.ruleY());
            if (l.pages() > 1) {
                MediumHauntLayout.Rect previous = l.previousArrow();
                MediumHauntLayout.Rect next = l.nextArrow(pageTextWidth);
                MediumPaint.pageArrow(context, previous.x(), previous.y(), true, page > 0);
                MediumPaint.pageArrow(context, next.x(), next.y(), false, page < l.pages() - 1);
            }
            TarotLedgerPaint.tagButton(context, button.x(), button.y(), button.width(), canConfirm, buttonHot);
        });
        for (int i = start; i < end; i++) {
            MediumHauntLayout.Rect tile = l.tile(i - start);
            drawFace(context, candidates.get(i).uuid(), tile.x() + FACE_INSET, tile.y() + FACE_INSET);
        }
        InventoryCardPaint.batch(context, () -> {
            for (int i = start; i < end; i++) {
                MediumHauntLayout.Rect tile = l.tile(i - start);
                boolean bound = component.isBound(candidates.get(i).uuid());
                MediumPaint.faceTileOverlay(context, tile.x(), tile.y(), i == hoveredIndex, bound);
            }
        });

        drawTitleText(context, font, frame, chipX, chipLabel, component.getBoundTargets().size());
        if (candidates.isEmpty()) {
            Text empty = Text.translatable("screen.sparkwitch.medium.haunt.empty");
            context.drawText(font, empty, frame.x() + (frame.width() - font.getWidth(empty)) / 2,
                    l.gridY() + (MediumHauntLayout.TILE - font.fontHeight) / 2 + 1, InventoryCardPaint.MUTED, true);
        }
        if (l.pages() > 1) {
            context.drawText(font, pageText, l.pageTextX(), l.footerTextY(), InventoryCardPaint.MUTED, true);
        }
        context.drawText(font, buttonLabel, button.x() + MediumHauntLayout.BUTTON_LABEL_X, l.footerTextY(),
                TarotLedgerPaint.tagButtonLabelColor(canConfirm, buttonHot), false);
        drawMessage(context, font, l.messageLeft(pageTextWidth), button.x() - MediumHauntLayout.MESSAGE_GAP, poor,
                price);
        if (hoveredIndex >= 0) {
            Candidate candidate = candidates.get(hoveredIndex);
            drawTooltip(context, font, l.tile(hoveredSlot), candidate.name(), component.isBound(candidate.uuid()));
        }
    }

    /** The face (skin + hat layer) from the tab list; a grey square while the skin is unknown. / 头像（皮肤 + 帽子层）。 */
    private void drawFace(DrawContext context, UUID uuid, int x, int y) {
        PlayerListEntry entry = player.networkHandler == null ? null : player.networkHandler.getPlayerListEntry(uuid);
        if (entry != null) {
            PlayerSkinDrawer.draw(context, entry.getSkinTextures(), x, y, FACE);
        } else {
            context.fill(x, y, x + FACE, y + FACE, InventoryCardPaint.WELL);
        }
    }

    /**
     * 招魂 in the title band, the "they won't notice" note when it fits, the bound count and the price chip label.
     * 标题带中的「招魂」、放得下时的「对方不会察觉」、已招魂人数与价签文字。
     */
    private void drawTitleText(DrawContext context, TextRenderer font, MediumHauntLayout.Rect frame, int chipX,
                               String chipLabel, int boundCount) {
        int textY = frame.y() + MediumHauntLayout.TITLE_Y;
        int titleX = frame.x() + MediumHauntLayout.TITLE_X;
        context.drawText(font, this.getMessage(), titleX, textY, InventoryCardPaint.TITLE, false);
        InventoryCardPaint.iconText(context, font, Text.literal(chipLabel), chipX + MediumHauntLayout.CHIP_PAD, textY,
                InventoryCardPaint.TEXT_HI, false);
        Text count = Text.translatable("screen.sparkwitch.medium.haunt.count", boundCount);
        int countX = chipX - MediumHauntLayout.TITLE_GAP - font.getWidth(count);
        context.drawText(font, count, countX, textY, InventoryCardPaint.MUTED, true);
        Text note = Text.translatable("screen.sparkwitch.medium.haunt.unseen");
        int noteX = titleX + font.getWidth(this.getMessage()) + MediumHauntLayout.TITLE_GAP;
        if (noteX + font.getWidth(note) + MediumHauntLayout.PAGER_MESSAGE_GAP <= countX) {
            context.drawText(font, note, noteX, textY, InventoryCardPaint.FAINT, true);
        }
    }

    /**
     * Right-aligned at {@code right}: short of coins (red), else the paid question for the selection (bullet + price
     * + name), else the hint. An instruction that does not fit is dropped whole; only the name is ever ellipsized.
     * 右对齐于 {@code right}：金币不足（红色），否则为选中对象的付费问句（圆点 + 价格 + 名字），否则为提示。放不下的说明整条
     * 省去；只有名字会被省略。
     */
    private void drawMessage(DrawContext context, TextRenderer font, int left, int right, boolean poor, int price) {
        int room = right - left;
        int textY = layout.footerTextY();
        if (poor) {
            Text message = Text.translatable("screen.sparkwitch.medium.haunt.poor", price + COIN);
            int width = font.getWidth(message);
            if (width <= room) {
                InventoryCardPaint.iconText(context, font, message, right - width, textY, InventoryCardPaint.ALERT_TEXT,
                        true);
            }
            return;
        }
        int index = selected == null ? -1 : indexOf(selected);
        if (index >= 0) {
            int fixed = font.getWidth(Text.translatable("screen.sparkwitch.medium.haunt.ask", price + COIN, ""));
            int nameRoom = room - TarotLedgerLayout.MARK_ADVANCE - fixed;
            if (nameRoom <= 0) {
                return;
            }
            String name = InventoryCardPaint.ellipsize(font, candidates.get(index).name(), nameRoom);
            Text message = Text.translatable("screen.sparkwitch.medium.haunt.ask", price + COIN,
                    Text.literal(name).withColor(InventoryCardPaint.TEXT_HI & 0xFFFFFF));
            int width = font.getWidth(message);
            int x = right - width;
            InventoryCardPaint.batch(context, () -> TarotLedgerPaint.playerBullet(context,
                    x - TarotLedgerLayout.MARK_ADVANCE, layout.footerRowY() + TarotLedgerLayout.FOOTER_MARK_Y, true));
            InventoryCardPaint.iconText(context, font, message, x, textY, InventoryCardPaint.TEXT, true);
            return;
        }
        Text hint = Text.translatable("screen.sparkwitch.medium.haunt.hint");
        int width = font.getWidth(hint);
        if (width <= room) {
            context.drawText(font, hint, right - width, textY, InventoryCardPaint.MUTED, true);
        }
    }

    /**
     * The name (and "already bound") in a brass tooltip hanging above the tile, lifted over the inventory's items.
     * 名字（及「已招魂」）显示在头像格上方的黄铜提示框中，层级高于背包物品。
     */
    private void drawTooltip(DrawContext context, TextRenderer font, MediumHauntLayout.Rect tile, String name,
                             boolean bound) {
        Text second = bound ? Text.translatable("screen.sparkwitch.medium.haunt.bound") : null;
        int width = Math.max(font.getWidth(name), second == null ? 0 : font.getWidth(second));
        int height = second == null ? 8 : 20;
        int x = Math.max(MediumPaint.FRAME, Math.min(screen.width - MediumPaint.FRAME - width,
                tile.x() + tile.width() / 2 - width / 2));
        int y = tile.y() - MediumPaint.FRAME - 1 - height;
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, TOOLTIP_Z);
        InventoryCardPaint.brassTooltip(context, x, y, width, height);
        context.drawText(font, name, x, y, InventoryCardPaint.TEXT_HI, true);
        if (second != null) {
            context.drawText(font, second, x, y + 12, InventoryCardPaint.MUTED, true);
        }
        context.getMatrices().pop();
    }

    // ------------------------------------------------------------------------------------------------ input

    private boolean canConfirm(MediumPlayerComponent component, boolean poor) {
        return selected != null && pending == null && !poor && !component.isBound(selected) && indexOf(selected) >= 0;
    }

    /**
     * Every click inside the drawer is consumed (so none reaches Wathe's slot or drop handling); only the left button
     * acts. Faces select or deselect (bound faces are inert), the arrows page, and only the enabled confirm button
     * pays.
     * 抽屉内的每次点击都会被消费（不会落到 Wathe 的物品格或丢弃逻辑）；只有左键生效。头像选中或取消选中（已招魂的头像不响应），
     * 箭头翻页，只有可用的确认按钮会付费。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || !layout.frame().contains(mouseX, mouseY)) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        MediumPlayerComponent component = MediumPlayerComponent.KEY.get(player);
        if (layout.pages() > 1) {
            int pageTextWidth = font.getWidth((page + 1) + "/" + layout.pages());
            if (layout.previousArrow().contains(mouseX, mouseY)) {
                turnPage(-1);
                return true;
            }
            if (layout.nextArrow(pageTextWidth).contains(mouseX, mouseY)) {
                turnPage(1);
                return true;
            }
        }
        Text label = Text.translatable(pending != null
                ? "screen.sparkwitch.medium.haunt.pending"
                : "screen.sparkwitch.medium.haunt.confirm");
        if (layout.button(font.getWidth(label)).contains(mouseX, mouseY)) {
            boolean poor = PlayerShopComponent.KEY.get(player).getBalance() < MediumRules.hauntPrice();
            if (canConfirm(component, poor) && ClientPlayNetworking.canSend(SubmitMediumHauntTargetC2SPacket.ID)) {
                ClientPlayNetworking.send(new SubmitMediumHauntTargetC2SPacket(selected));
                pending = selected;
                pendingAtMs = Util.getMeasuringTimeMs();
                playDownSound(MinecraftClient.getInstance().getSoundManager());
            }
            return true;
        }
        int slot = layout.slotAt(mouseX, mouseY);
        int index = slot < 0 ? -1 : page * layout.perPage() + slot;
        if (index >= 0 && index < candidates.size() && pending == null) {
            UUID uuid = candidates.get(index).uuid();
            if (!component.isBound(uuid)) {
                selected = uuid.equals(selected) ? null : uuid;
                playDownSound(MinecraftClient.getInstance().getSoundManager());
            }
        }
        return true;
    }

    private void turnPage(int delta) {
        int next = layout.clampPage(page + delta);
        if (next != page) {
            page = next;
            playDownSound(MinecraftClient.getInstance().getSoundManager());
        }
    }

    /** The wheel turns pages while the pointer is over the drawer. / 指针位于抽屉上时滚轮翻页。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.active || !this.visible || !layout.frame().contains(mouseX, mouseY)) {
            return false;
        }
        if (verticalAmount != 0.0) {
            page = layout.clampPage(page - (int) Math.signum(verticalAmount));
        }
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
