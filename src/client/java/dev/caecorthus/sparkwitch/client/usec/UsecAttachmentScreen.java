package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentLayout.Column;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentLayout.Hit;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentLayout.Rect;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.BoltStatus;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Button;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Card;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Label;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Part;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.PartKind;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Room;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Row;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentPaint.ButtonState;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecMagazineContents;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecMagazineItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecSuppressorItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecAttachmentC2SPacket;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static dev.caecorthus.sparkwitch.client.usec.UsecAttachmentLayout.*;

/**
 * Client only. The USEC rifle attachment screen (D16), drawn in the owner-picked U2 "field manual blueprint" style:
 * the only place to change magazines (Q4), plus the suppressor, single-round chambering (Q4+) and loading or unloading
 * rounds, one button per {@code UsecAttachmentAction}. A plain {@link Screen} with no screen handler (like the Seeker
 * Console and Judge selection): each frame it reads the rifle at {@code rifleSlot} and the inventory from the client
 * player, offers the {@link UsecAttachmentModel} buttons, and every press sends one {@code sparkwitch:usec_attachment}
 * packet that the server validates. Buttons are a prediction only; after a press all buttons wait until the synced
 * inventory changes or a short timeout passes, so a double click never sends a swap twice. Esc and the inventory key
 * return to the parent inventory screen; like Wathe's own inventory it closes outright during a fade or on death, and
 * it returns to the parent when the rifle leaves its slot. The rifle is always named by its item display name.
 * Geometry is {@link UsecAttachmentLayout}, colours and shapes {@link UsecAttachmentPaint}; all text uses
 * {@code gui.sparkwitch.usec_attachment.*}.
 * 仅客户端。USEC 步枪配件界面（D16），采用所有者选定的 U2「野战手册蓝图」样式：唯一可以更换弹匣的地方（Q4），另含消音器、
 * 单发压膛（Q4+）以及子弹的装填与退出，每个 {@code UsecAttachmentAction} 都有一个按钮。普通 {@link Screen}，没有界面处理器
 * （同搜寻者控制台与法官选择）：每帧从客户端玩家读取 {@code rifleSlot} 上的步枪与背包，提供 {@link UsecAttachmentModel} 的
 * 按钮，每次按下发送一个由服务端校验的 {@code sparkwitch:usec_attachment} 数据包。按钮只是预测；按下后所有按钮会等到已同步
 * 的背包发生变化或短暂超时，因此双击绝不会把交换发送两次。Esc 与物品栏键返回父背包界面；与 Wathe 自己的背包一样，淡入淡出
 * 期间或死亡时直接关闭；步枪离开所在栏位时返回父界面。步枪名称始终取自物品显示名。几何见 {@link UsecAttachmentLayout}，
 * 颜色与形状见 {@link UsecAttachmentPaint}；所有文字使用 {@code gui.sparkwitch.usec_attachment.*}。
 */
public final class UsecAttachmentScreen extends Screen {
    static final String KEY = "gui.sparkwitch.usec_attachment.";
    /** Ticks the buttons wait for the server's answer before re-enabling. 按钮等待服务端回应的刻数，超时后重新启用。 */
    static final int PENDING_TICKS = 10;
    private static final String ELLIPSIS = "…";
    private static final int TOOLTIP_Z = 400;

    private final @Nullable Screen parent;
    private final int rifleSlot;
    private UsecAttachmentLayout layout = UsecAttachmentLayout.of(PANEL_W, PANEL_H);
    private View view = View.EMPTY;
    private @Nullable View pendingFrom;
    private int pendingTicks;
    private int scroll;
    private final Map<UsecAmmoType, ItemStack> chamberIcons = new EnumMap<>(UsecAmmoType.class);
    private @Nullable ItemStack magazineIcon;
    private @Nullable ItemStack suppressorIcon;

    /**
     * One frame's inputs: the rifle and its name, the normalised parts with their live stacks, and the room
     * approximation. 一帧的输入：步枪及其名称、规范化后的零件及其实时物品堆、空位近似。
     */
    private record View(UsecRifleState rifle, Text rifleName, List<Part> parts, List<ItemStack> stacks, Room room) {
        static final View EMPTY = new View(UsecRifleState.EMPTY, Text.empty(), List.of(), List.of(), Room.NONE);

        /** Pending-latch identity: state only, never stack instances. 等待锁的比较依据：只比较状态，不比较物品堆实例。 */
        boolean sameState(@Nullable View other) {
            return other != null && rifle.equals(other.rifle) && parts.equals(other.parts) && room.equals(other.room);
        }
    }

    public UsecAttachmentScreen(@Nullable Screen parent, int rifleSlot) {
        super(Text.translatable(KEY + "screen"));
        this.parent = parent;
        this.rifleSlot = rifleSlot;
    }

    public int rifleSlot() {
        return rifleSlot;
    }

    @Override
    protected void init() {
        layout = UsecAttachmentLayout.of(width, height);
        refresh();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- lifecycle / 生命周期 ----

    @Override
    public void tick() {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            return;
        }
        // Same rule as Wathe's LimitedHandledScreen.tick: close outright during a fade or once dead.
        // 与 Wathe 的 LimitedHandledScreen.tick 相同：淡入淡出期间或死亡后直接关闭。
        if (GameWorldComponent.KEY.get(player.getWorld()).getFade() > 0 || !player.isAlive() || player.isRemoved()) {
            player.closeHandledScreen();
            return;
        }
        if (!(player.getInventory().getStack(rifleSlot).getItem() instanceof UsecRifleItem)) {
            close();
            return;
        }
        refresh();
        if (pendingTicks > 0) {
            pendingTicks = pendingFrom != null && !view.sameState(pendingFrom) ? 0 : pendingTicks - 1;
        }
        scroll = clampScroll(scroll, view.parts().size());
    }

    /** Returns to the parent inventory screen (Esc, inventory key). 返回父背包界面。 */
    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void refresh() {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            view = View.EMPTY;
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack rifle = inventory.getStack(rifleSlot);
        List<Part> raw = new ArrayList<>();
        for (int slot = 0; slot <= UsecAttachmentRules.OFFHAND_SLOT; slot++) {
            if (!UsecAttachmentRules.isActionSlot(slot)) {
                continue;
            }
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() instanceof UsecMagazineItem) {
                raw.add(Part.magazine(slot, UsecMagazineItem.contents(stack)));
            } else if (stack.getItem() instanceof UsecAmmoItem ammo) {
                raw.add(Part.rounds(slot, ammo.ammoType(), stack.getCount()));
            } else if (stack.getItem() instanceof UsecSuppressorItem) {
                raw.add(Part.suppressor(slot));
            }
        }
        List<Part> parts = UsecAttachmentModel.collect(raw, rifleSlot);
        List<ItemStack> stacks = new ArrayList<>(parts.size());
        for (Part part : parts) {
            stacks.add(inventory.getStack(part.slot()));
        }
        view = new View(UsecRifleState.read(rifle), rifle.getName(), parts, List.copyOf(stacks), room(inventory));
    }

    /**
     * Room prediction through the server's own shown-slot rule ({@link UsecAttachmentRules#releaseSlot}, A7): an empty
     * shown slot or empty offhand, and per round type a shown stack with room.
     * 通过服务端同一显示栏位规则（{@link UsecAttachmentRules#releaseSlot}，A7）预测空位：空的显示栏位或空的副手，以及每种弹种
     * 是否有尚有空余的显示中物品堆。
     */
    private static Room room(PlayerInventory inventory) {
        boolean secondRow = SparkFactionSecondRowCompat.isShown();
        boolean freeSlot = UsecAttachmentRules.releaseSlot(slot -> false,
                slot -> inventory.getStack(slot).isEmpty(), secondRow) != UsecAttachmentRules.NO_ROOM;
        Set<UsecAmmoType> roundRoom = EnumSet.noneOf(UsecAmmoType.class);
        for (UsecAmmoType type : UsecAmmoType.values()) {
            if (UsecAttachmentRules.releaseSlot(slot -> {
                ItemStack stack = inventory.getStack(slot);
                return stack.getItem() instanceof UsecAmmoItem ammo && ammo.ammoType() == type
                        && stack.getCount() < stack.getMaxCount();
            }, slot -> false, secondRow) != UsecAttachmentRules.NO_ROOM) {
                roundRoom.add(type);
            }
        }
        return new Room(freeSlot, roundRoom);
    }

    // ---- input / 输入 ----

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = layout.localX(mouseX);
        double y = layout.localY(mouseY);
        boolean inside = panel().contains(x, y);
        if (button != 0) {
            return inside || super.mouseClicked(mouseX, mouseY, button);
        }
        Hit boxHit = hitBoxButton(x, y, boxWidths());
        if (boxHit != null) {
            press(cardButtons(Card.values()[boxHit.group()]).get(boxHit.index()));
            return true;
        }
        List<Row> rows = rows();
        Hit rowHit = hitRowButton(x, y, scroll, kinds(rows), rowWidths(rows));
        if (rowHit != null) {
            press(rows.get(rowHit.group()).buttons().get(rowHit.index()));
            return true;
        }
        return inside || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (UsecAttachmentLayout.rows().contains(layout.localX(mouseX), layout.localY(mouseY))) {
            scroll = clampScroll(scroll - (int) Math.round(verticalAmount * ROW_H), view.parts().size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** Sends one action for the server to validate, then latches until the inventory answers. 发送一个动作并等待回应。 */
    private void press(Button button) {
        if (!button.enabled() || pendingTicks > 0 || !ClientPlayNetworking.canSend(UsecAttachmentC2SPacket.ID)) {
            return;
        }
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        ClientPlayNetworking.send(new UsecAttachmentC2SPacket(button.action(), button.primarySlot(),
                button.itemSlot()));
        pendingFrom = view;
        pendingTicks = PENDING_TICKS;
    }

    // ---- model / 模型 ----

    /** The rifle cooldown from the shared exact classifier. 共用精确分类器给出的步枪冷却。 */
    private UsecCooldowns.Status cooldown() {
        return client == null || client.player == null ? UsecCooldowns.Status.NONE
                : UsecCooldowns.status(client.player);
    }

    private BoltStatus status() {
        return UsecAttachmentModel.boltStatus(view.rifle(), cooldown());
    }

    private List<Button> cardButtons(Card card) {
        return UsecAttachmentModel.cardButtons(card, view.rifle(), rifleSlot, view.parts(), view.room());
    }

    private List<Row> rows() {
        return UsecAttachmentModel.rows(view.rifle(), rifleSlot, view.parts(), view.room());
    }

    private static PartKind[] kinds(List<Row> rows) {
        PartKind[] kinds = new PartKind[rows.size()];
        for (int index = 0; index < kinds.length; index++) {
            kinds[index] = rows.get(index).part().kind();
        }
        return kinds;
    }

    private int buttonWidth(Button button) {
        return textRenderer.getWidth(Text.translatable(button.label().key())) + 2 * BUTTON_PAD;
    }

    private int[] widths(List<Button> buttons) {
        int[] widths = new int[buttons.size()];
        for (int index = 0; index < widths.length; index++) {
            widths[index] = buttonWidth(buttons.get(index));
        }
        return widths;
    }

    private int[][] boxWidths() {
        int[][] widths = new int[Card.values().length][];
        for (Card card : Card.values()) {
            widths[card.ordinal()] = widths(cardButtons(card));
        }
        return widths;
    }

    private int[][] rowWidths(List<Row> rows) {
        int[][] widths = new int[rows.size()][];
        for (int index = 0; index < rows.size(); index++) {
            widths[index] = widths(rows.get(index).buttons());
        }
        return widths;
    }

    // ---- render / 绘制 ----

    /**
     * The parent inventory stays visible behind the sheet, as in the mockup: it is drawn with an off-screen mouse (no
     * hover or tooltip), the depth buffer is cleared so its item icons never show through, and the scrim goes on top.
     * Without a parent the in-game dim stands in. Vanilla {@code Screen.render} calls this once.
     * 与样稿一致，父背包保留在图纸后方：以屏幕外的鼠标位置绘制（无悬停与提示框），清空深度缓冲使其物品图标不会透出，再盖上
     * 遮罩。没有父界面时改用游戏内暗化。原版 {@code Screen.render} 只调用一次。
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.render(context, -1, -1, delta);
            context.draw();
            RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        } else {
            renderInGameBackground(context);
        }
        UsecAttachmentPaint.scrim(context, width, height);
    }

    /** The parent re-lays out with this screen, so returning to it needs no extra step. 父界面随本界面重新布局。 */
    @Override
    public void resize(MinecraftClient client, int width, int height) {
        if (parent != null) {
            parent.resize(client, width, height);
        }
        super.resize(client, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        double x = layout.localX(mouseX);
        double y = layout.localY(mouseY);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(layout.originX(), layout.originY(), 0.0F);
        matrices.scale((float) layout.scale(), (float) layout.scale(), 1.0F);
        BoltStatus status = status();
        Card focus = boxAt(x, y);
        UsecAttachmentPaint.sheet(context);
        renderTitle(context);
        renderDrawing(context, focus);
        List<ItemStack> tooltip = new ArrayList<>(1);
        for (Card card : Card.values()) {
            renderBox(context, card, card == focus, status, x, y, tooltip);
        }
        renderTable(context, x, y, tooltip);
        renderNotes(context);
        renderTitleBlock(context);
        renderFooter(context, status);
        if (!tooltip.isEmpty()) {
            renderTooltip(context, tooltip.getFirst(), (int) Math.floor(x), (int) Math.floor(y));
        }
        matrices.pop();
    }

    private void renderTitle(DrawContext context) {
        Text role = Text.translatable(KEY + "role");
        int roleX = PANEL_W - ROLE_RIGHT - textRenderer.getWidth(role);
        text(context, role, roleX, TITLE_Y, UsecAttachmentPaint.TEXT);
        UsecAttachmentPaint.gem(context, roleX - 8, TITLE_Y + 1, UsecRules.COLOR);
        text(context, fit(Text.translatable(KEY + "title", view.rifleName()), roleX - 14 - TITLE_X), TITLE_X,
                TITLE_Y, UsecAttachmentPaint.TEXT_HI);
    }

    private void renderDrawing(DrawContext context, @Nullable Card focus) {
        UsecRifleState rifle = view.rifle();
        text(context, Text.translatable(KEY + "view"), VIEW_LABEL_X, VIEW_LABEL_Y, UsecAttachmentPaint.MUTED);
        UsecAttachmentPaint.rifle(context, rifle, focus);
        UsecAttachmentPaint.boreLine(context, rifle);
        UsecAttachmentPaint.hiddenChamber(context, rifle.chamber());
        Text length = Text.translatable(KEY + "dimension.length");
        Text barrel = Text.translatable(KEY + "dimension.barrel");
        int lengthWidth = textRenderer.getWidth(length);
        int barrelWidth = textRenderer.getWidth(barrel);
        UsecAttachmentPaint.dimensions(context, rifle.suppressor(), lengthWidth, barrelWidth);
        text(context, length, UsecAttachmentPaint.dimensionLabelX(true) - lengthWidth / 2,
                UsecAttachmentPaint.dimensionLabelY(true), UsecAttachmentPaint.LINE);
        text(context, barrel, UsecAttachmentPaint.dimensionLabelX(false) - barrelWidth / 2,
                UsecAttachmentPaint.dimensionLabelY(false), UsecAttachmentPaint.LINE);
        for (Card card : Card.values()) {
            UsecAttachmentPaint.callout(context, card, live(card), card == focus, rifle.suppressor());
        }
    }

    private boolean live(Card card) {
        UsecRifleState rifle = view.rifle();
        return switch (card) {
            case CHAMBER -> rifle.chamber() != null;
            case MAGAZINE -> rifle.hasMagazine();
            case MUZZLE -> rifle.suppressor();
        };
    }

    private void renderBox(DrawContext context, Card card, boolean hovered, BoltStatus status, double mouseX,
                           double mouseY, List<ItemStack> tooltip) {
        Rect box = box(card);
        UsecAttachmentPaint.box(context, box, hovered);
        UsecAttachmentPaint.balloon(context, box.x() + BOX_BALLOON, box.y() + BOX_BALLOON, number(card), live(card),
                hovered);
        text(context, fit(Text.translatable(card.labelKey()), BOX_HEADER_W - BOX_LABEL_X - 1),
                box.x() + BOX_LABEL_X, box.y() + BOX_LABEL_Y, UsecAttachmentPaint.TEXT);
        text(context, fit(Text.translatable(card.codeKey()), BOX_HEADER_W - BOX_CODE_X - 2), box.x() + BOX_CODE_X,
                box.y() + BOX_CODE_Y, UsecAttachmentPaint.FAINT);
        Rect well = boxWell(card);
        UsecAttachmentPaint.well(context, well);
        ItemStack icon = boxIcon(card);
        int iconX = well.x() + 2;
        int iconY = well.y() + 2;
        if (icon.isEmpty()) {
            UsecAttachmentPaint.socket(context, iconX, iconY);
        } else {
            context.drawItem(icon, iconX, iconY);
            if (well.contains(mouseX, mouseY)) {
                tooltip.add(icon);
            }
        }
        List<Button> buttons = cardButtons(card);
        int[] widths = widths(buttons);
        List<Rect> rects = boxButtons(card, widths);
        for (int index = 0; index < buttons.size(); index++) {
            renderButton(context, rects.get(index), buttons.get(index), mouseX, mouseY);
        }
        int tx = boxTextX(card);
        int line1 = box.y() + BOX_LINE1_Y;
        int line2 = box.y() + BOX_LINE2_Y;
        int width1 = box.right() - BOX_GRID_RIGHT - tx;
        int width2 = boxTextWidth(card, widths);
        UsecRifleState rifle = view.rifle();
        switch (card) {
            case CHAMBER -> {
                UsecAmmoType chamber = rifle.chamber();
                if (chamber == null) {
                    text(context, Text.translatable(KEY + "chamber.empty"), tx, line1, UsecAttachmentPaint.TEXT);
                    text(context, fit(Text.translatable(KEY + "chamber.empty.hint"), width2), tx, line2,
                            UsecAttachmentPaint.FAINT);
                } else {
                    text(context, fit(chamber.label().append(Text.literal(" "))
                            .append(Text.translatable(KEY + "chamber.kind." + chamber.id())
                                    .withColor(UsecAttachmentPaint.TEXT & 0xFFFFFF)), width1), tx, line1,
                            UsecAttachmentPaint.TEXT);
                    boolean bolting = status == BoltStatus.BOLTING;
                    text(context, fit(Text.translatable(KEY + (bolting ? "chamber.bolting" : "chamber.ready")),
                            width2), tx, line2, bolting ? UsecAttachmentPaint.COOL : UsecAttachmentPaint.MUTED);
                }
            }
            case MAGAZINE -> {
                UsecMagazineContents magazine = rifle.magazine();
                if (magazine == null) {
                    text(context, fit(Text.translatable(KEY + "magazine.none"), width2), tx, line1,
                            UsecAttachmentPaint.TEXT);
                    text(context, fit(Text.translatable(KEY + "magazine.none.hint"), width2), tx, line2,
                            UsecAttachmentPaint.FAINT);
                } else {
                    text(context, fit(tokens(magazine, true, width2), width2), tx, line1, UsecAttachmentPaint.TEXT);
                    int pips = UsecAttachmentPaint.pips(context, tx, box.y() + BOX_PIPS_Y, magazine.firingOrder(),
                            UsecRules.MAGAZINE_CAPACITY);
                    text(context, Text.translatable(KEY + "count.magazine", magazine.size(),
                            UsecRules.MAGAZINE_CAPACITY), tx + pips + 4, line2, UsecAttachmentPaint.MUTED);
                }
            }
            case MUZZLE -> {
                boolean fitted = rifle.suppressor();
                text(context, Text.translatable(KEY + (fitted ? "muzzle.fitted" : "muzzle.none")), tx, line1,
                        UsecAttachmentPaint.TEXT);
                text(context, fit(Text.translatable(KEY + (fitted ? "muzzle.fitted.hint" : "muzzle.none.hint")),
                        width2), tx, line2, fitted ? UsecAttachmentPaint.MUTED : UsecAttachmentPaint.FAINT);
            }
        }
    }

    /** Display stacks for the box wells; empty when the slot is empty. 详图框物品井的显示物品；槽位为空时为空堆。 */
    private ItemStack boxIcon(Card card) {
        UsecRifleState rifle = view.rifle();
        return switch (card) {
            case CHAMBER -> rifle.chamber() == null ? ItemStack.EMPTY
                    : chamberIcons.computeIfAbsent(rifle.chamber(), type -> new ItemStack(type.item()));
            case MAGAZINE -> {
                if (rifle.magazine() == null) {
                    yield ItemStack.EMPTY;
                }
                if (magazineIcon == null) {
                    magazineIcon = new ItemStack(SparkWitchItems.usecMagazine());
                }
                if (!UsecMagazineItem.contents(magazineIcon).equals(rifle.magazine())) {
                    UsecMagazineItem.setContents(magazineIcon, rifle.magazine());
                }
                yield magazineIcon;
            }
            case MUZZLE -> {
                if (!rifle.suppressor()) {
                    yield ItemStack.EMPTY;
                }
                if (suppressorIcon == null) {
                    suppressorIcon = new ItemStack(SparkWitchItems.usecSuppressor());
                }
                yield suppressorIcon;
            }
        };
    }

    private void renderTable(DrawContext context, double mouseX, double mouseY, List<ItemStack> tooltip) {
        Text label = Text.translatable(KEY + "parts");
        text(context, label, BOM_X1 + 4, BOM_LABEL_Y, UsecAttachmentPaint.TEXT);
        text(context, Text.translatable(KEY + "parts.sub"), BOM_X1 + 4 + textRenderer.getWidth(label) + 5,
                BOM_LABEL_Y, UsecAttachmentPaint.FAINT);
        Text count = Text.translatable(KEY + "parts.count", view.parts().size());
        text(context, count, BOM_X2 - 4 - textRenderer.getWidth(count), BOM_LABEL_Y, UsecAttachmentPaint.FAINT);
        List<Row> rows = rows();
        int bottom = tableBottom(rows.size());
        UsecAttachmentPaint.table(context, bottom);
        for (Column column : Column.values()) {
            Text header = Text.translatable(KEY + "header." + column.name().toLowerCase(Locale.ROOT));
            centered(context, header, column, HEADER_TEXT_Y, UsecAttachmentPaint.MUTED);
        }
        if (rows.isEmpty()) {
            int room = BOM_X2 - BOM_X1 - 8;
            Text empty = Text.translatable(KEY + "parts.empty");
            int width = Math.min(room, textRenderer.getWidth(empty));
            text(context, fit(empty, room), BOM_X1 + (BOM_X2 - BOM_X1 - width) / 2, ROWS_TOP + 9,
                    UsecAttachmentPaint.FAINT);
            return;
        }
        int hovered = rowAt(mouseX, mouseY, scroll, rows.size());
        // Rows clip above the closing line, which is drawn after them. / 各行裁剪在收尾线之上，收尾线随后绘制。
        context.enableScissor(layout.screenLeft(BOM_X1), layout.screenTop(ROWS_TOP), layout.screenRight(BOM_X2),
                layout.screenBottom(bottom - 1));
        for (int index = 0; index < rows.size(); index++) {
            Rect row = row(index, scroll);
            if (row.bottom() <= ROWS_TOP || row.y() >= ROWS_BOTTOM) {
                continue;
            }
            renderRow(context, index, row, rows.get(index), index == hovered, mouseX, mouseY, tooltip);
        }
        context.disableScissor();
        UsecAttachmentPaint.tableClose(context, bottom);
        Rect rod = rod(rows.size());
        Rect thumb = thumb(scroll, rows.size());
        if (rod != null && thumb != null) {
            UsecAttachmentPaint.scrollbar(context, rod, thumb);
        }
    }

    private void renderRow(DrawContext context, int index, Rect row, Row model, boolean hovered, double mouseX,
                           double mouseY, List<ItemStack> tooltip) {
        UsecAttachmentPaint.row(context, row, hovered);
        Part part = model.part();
        ItemStack stack = view.stacks().get(index);
        centered(context, Text.literal(Integer.toString(index + 1)), Column.NUMBER, row.y() + ROW_NUMBER_Y,
                UsecAttachmentPaint.TEXT);
        Rect icon = rowIcon(row);
        context.drawItem(stack, icon.x(), icon.y());
        if (hovered && icon.contains(mouseX, mouseY)) {
            tooltip.add(stack);
        }
        int nameX = columnFrom(Column.PART) + ROW_NAME_DX;
        int contentX = columnFrom(Column.CONTENT) + 1;
        int contentWidth = columnTo(Column.CONTENT) - contentX;
        int line1 = row.y() + ROW_LINE1_Y;
        int line2 = row.y() + ROW_LINE2_Y;
        switch (part.kind()) {
            case MAGAZINE -> {
                UsecMagazineContents contents = part.contents() == null ? UsecMagazineContents.EMPTY : part.contents();
                text(context, Text.translatable(KEY + "row.magazine"), nameX, line1, UsecAttachmentPaint.TEXT);
                text(context, Text.translatable(KEY + "row.magazine.sub", UsecRules.MAGAZINE_CAPACITY), nameX, line2,
                        UsecAttachmentPaint.FAINT);
                if (contents.isEmpty()) {
                    text(context, Text.translatable(KEY + "content.empty"), contentX, line1, UsecAttachmentPaint.FAINT);
                } else {
                    text(context, fit(tokens(contents, false, contentWidth), contentWidth), contentX, line1,
                            UsecAttachmentPaint.MUTED);
                }
                UsecAttachmentPaint.pips(context, contentX + ROW_PIPS_DX - 1, row.y() + ROW_PIPS_Y,
                        contents.firingOrder(), UsecRules.MAGAZINE_CAPACITY);
            }
            case ROUNDS -> {
                UsecAmmoType ammo = part.ammo();
                if (ammo != null) {
                    text(context, ammo.label(), nameX, line1, UsecAttachmentPaint.TEXT);
                    text(context, Text.translatable(KEY + "row.round.sub"), nameX, line2, UsecAttachmentPaint.FAINT);
                    text(context, Text.translatable(KEY + "content." + ammo.id()), contentX, line1,
                            UsecAttachmentPaint.TEXT);
                    text(context, Text.translatable(KEY + "content." + ammo.id() + ".sub"), contentX, line2,
                            UsecAttachmentPaint.FAINT);
                }
            }
            case SUPPRESSOR -> {
                text(context, Text.translatable(KEY + "row.suppressor"), nameX, line1, UsecAttachmentPaint.TEXT);
                text(context, Text.translatable(KEY + "row.suppressor.sub"), nameX, line2, UsecAttachmentPaint.FAINT);
                text(context, Text.translatable(KEY + "content.suppressor"), contentX, line1,
                        UsecAttachmentPaint.TEXT);
                text(context, Text.translatable(KEY + "content.suppressor.sub"), contentX, line2,
                        UsecAttachmentPaint.FAINT);
            }
        }
        Text quantity = part.kind() == PartKind.MAGAZINE
                ? Text.translatable(KEY + "count.magazine", part.contents() == null ? 0 : part.contents().size(),
                        UsecRules.MAGAZINE_CAPACITY)
                : Text.translatable(KEY + "count.items", part.count());
        centered(context, quantity, Column.QUANTITY, row.y() + ROW_NUMBER_Y, UsecAttachmentPaint.TEXT);
        List<Button> buttons = model.buttons();
        List<Rect> rects = rowButtons(part.kind(), row, widths(buttons));
        for (int button = 0; button < buttons.size(); button++) {
            renderButton(context, rects.get(button), buttons.get(button), mouseX, mouseY);
        }
    }

    private void renderNotes(DrawContext context) {
        int y = NOTES_Y;
        int noteWidth = text(context, Text.translatable(KEY + "notes"), NOTES_X, y, UsecAttachmentPaint.MUTED);
        int x0 = NOTES_X + Math.max(13, noteWidth + 5);
        int x = x0 + text(context, Text.literal("1."), x0, y, UsecAttachmentPaint.FAINT) + 4;
        for (UsecAmmoType type : new UsecAmmoType[]{UsecAmmoType.AP, UsecAmmoType.FMJ}) {
            if (type == UsecAmmoType.FMJ) {
                x += 3 + text(context, Text.literal("·"), x + 3, y, UsecAttachmentPaint.FAINT) + 5;
            }
            UsecAttachmentPaint.gem(context, x, y + 1, type.color());
            x += 8 + text(context, type.label().append(Text.translatable(KEY + "notes." + type.id())
                    .withColor(UsecAttachmentPaint.TEXT & 0xFFFFFF)), x + 8, y, UsecAttachmentPaint.TEXT);
        }
        int x2 = x0 + text(context, Text.literal("2."), x0, y + NOTES_LINE, UsecAttachmentPaint.FAINT) + 4;
        text(context, fit(Text.translatable(KEY + "notes.order"), BOM_X2 - 4 - x2), x2, y + NOTES_LINE,
                UsecAttachmentPaint.TEXT);
    }

    private void renderTitleBlock(DrawContext context) {
        UsecAttachmentPaint.titleBlock(context);
        int row1 = BLOCK_TOP + 4;
        int row2 = row1 + BLOCK_ROW;
        int row3 = row2 + BLOCK_ROW + 1;
        int valueWidth = BLOCK_COL2_X - BLOCK_VALUE_X - 2;
        int label2Width = BLOCK_COL3_X - BLOCK_LABEL2_X - 1;
        text(context, fit(Text.translatable(KEY + "block.title"), BLOCK_SPLIT_X - BLOCK_LABEL_X + 1), BLOCK_LABEL_X,
                row1, UsecAttachmentPaint.FAINT);
        text(context, fit(Text.translatable(KEY + "block.title.value"), valueWidth), BLOCK_VALUE_X, row1,
                UsecAttachmentPaint.TEXT_HI);
        text(context, fit(Text.translatable(KEY + "block.scale"), label2Width), BLOCK_LABEL2_X, row1,
                UsecAttachmentPaint.FAINT);
        text(context, Text.translatable(KEY + "block.scale.value"), BLOCK_VALUE2_X, row1, UsecAttachmentPaint.TEXT);
        text(context, fit(Text.translatable(KEY + "block.number"), BLOCK_SPLIT_X - BLOCK_LABEL_X + 1), BLOCK_LABEL_X,
                row2, UsecAttachmentPaint.FAINT);
        // Owner (2026-10-07): the drawing number reads "AXMC .338 LM". Its own key, because the long item name
        // ("AXMC Sniper Rifle .338 LM") cut ".338 LM" off. / 所有者（2026-10-07）：图号为「AXMC .338 LM」。使用独立的键，
        // 因为较长的物品名（「AXMC 狙击步枪 .338 LM」）会把「.338 LM」截掉。
        text(context, fit(Text.translatable(KEY + "drawing_number"), valueWidth), BLOCK_VALUE_X, row2,
                UsecAttachmentPaint.TEXT);
        text(context, fit(Text.translatable(KEY + "block.sheet"), label2Width), BLOCK_LABEL2_X, row2,
                UsecAttachmentPaint.FAINT);
        text(context, Text.translatable(KEY + "block.sheet.value"), BLOCK_VALUE2_X, row2, UsecAttachmentPaint.TEXT);
        text(context, fit(Text.translatable(KEY + "block.note"), BLOCK_SPLIT_X - BLOCK_LABEL_X + 1), BLOCK_LABEL_X,
                row3, UsecAttachmentPaint.FAINT);
        text(context, fit(Text.translatable(KEY + "block.note.value"), BOM_X2 - 4 - BLOCK_VALUE_X), BLOCK_VALUE_X,
                row3, UsecAttachmentPaint.MUTED);
    }

    private void renderFooter(DrawContext context, BoltStatus status) {
        Text label = Text.translatable(KEY + "status." + status.name().toLowerCase(Locale.ROOT));
        int color = UsecAttachmentPaint.statusColor(status);
        int width = UsecAttachmentPaint.pill(context, PILL_X, PILL_Y, textRenderer.getWidth(label), status);
        text(context, label, UsecAttachmentPaint.pillTextX(PILL_X), PILL_Y + BUTTON_TEXT_Y, color);
        if (status != BoltStatus.BOLTING && status != BoltStatus.LOCKED) {
            return;
        }
        UsecCooldowns.Status cooldown = cooldown();
        int remaining = cooldown.remaining();
        int gaugeX = PILL_X + width + 5;
        UsecAttachmentPaint.gauge(context, gaugeX, PILL_Y,
                UsecAttachmentModel.progress(remaining, UsecAttachmentModel.gaugeTotal(cooldown)));
        int tenths = UsecAttachmentModel.tenthsCeil(remaining);
        Text seconds = status == BoltStatus.BOLTING
                ? Text.translatable(KEY + "status.tenths", tenths / 10, tenths % 10)
                : Text.translatable(KEY + "status.seconds", UsecAttachmentModel.secondsCeil(remaining));
        text(context, seconds, gaugeX + GAUGE_W + 5, PILL_Y + BUTTON_TEXT_Y, UsecAttachmentPaint.COOL);
    }

    private void renderButton(DrawContext context, Rect rect, Button button, double mouseX, double mouseY) {
        boolean enabled = button.enabled() && pendingTicks <= 0;
        ButtonState state = !enabled ? ButtonState.OFF
                : rect.contains(mouseX, mouseY) ? ButtonState.HOT : ButtonState.ON;
        UsecAttachmentPaint.button(context, rect, state);
        Text label = buttonLabel(button.label(), state);
        int width = textRenderer.getWidth(label);
        text(context, label, rect.x() + (rect.w() - width + 1) / 2, rect.y() + BUTTON_TEXT_Y,
                UsecAttachmentPaint.buttonTextColor(state));
    }

    /** "+FMJ" / "+AP" keep the round colour; other labels take the state colour. +FMJ/+AP 保留弹种颜色。 */
    private static Text buttonLabel(Label label, ButtonState state) {
        MutableText text = Text.translatable(label.key());
        UsecAmmoType ammo = label == Label.LOAD_FMJ ? UsecAmmoType.FMJ : label == Label.LOAD_AP ? UsecAmmoType.AP : null;
        if (ammo == null) {
            return text;
        }
        return Text.literal("+").append(Text.translatable(ammo.labelKey())
                .withColor(UsecAttachmentPaint.ammoColor(ammo, state) & 0xFFFFFF));
    }

    private void renderTooltip(DrawContext context, ItemStack stack, int mouseX, int mouseY) {
        if (client == null) {
            return;
        }
        List<Text> lines = getTooltipFromItem(client, stack);
        if (lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (Text line : lines) {
            width = Math.max(width, textRenderer.getWidth(line));
        }
        int height = lines.size() == 1 ? 8 : 12 + (lines.size() - 1) * 10 - 2;
        int x = Math.max(8, Math.min(PANEL_W - 8 - width, mouseX + 12));
        int y = Math.max(8, Math.min(PANEL_H - 8 - height, mouseY - 12));
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0.0F, 0.0F, TOOLTIP_Z);
        UsecAttachmentPaint.tooltip(context, x, y, width, height, lines.size() > 1);
        for (int index = 0; index < lines.size(); index++) {
            int lineY = index == 0 ? y : y + 12 + (index - 1) * 10;
            Text line = index == 0 ? lines.get(index) : lines.get(index).copy().styled(style -> style.getColor() == null
                    ? style : style.withColor(UsecAttachmentPaint.loreColor(style.getColor().getRgb())));
            text(context, line, x, lineY, index == 0 ? UsecAttachmentPaint.TEXT : UsecAttachmentPaint.MUTED);
        }
        matrices.pop();
    }

    // ---- text / 文字 ----

    /**
     * Firing-order round tokens; {@code brackets} wraps them ("[AP·FMJ]", "[ ]" when empty). When the full labels do
     * not fit, each round shortens to its first letter so all five stay visible.
     * 按发射顺序的子弹标记；{@code brackets} 时加方括号（空弹匣为「[ ]」）。完整标签放不下时，每发缩写为首字母，五发都能显示。
     */
    private Text tokens(UsecMagazineContents contents, boolean brackets, int width) {
        if (contents.isEmpty()) {
            return Text.translatable(KEY + "tokens.empty");
        }
        Text full = UsecMagazineItem.roundsText(contents.firingOrder());
        Text shown = full;
        if (textRenderer.getWidth(wrap(full, brackets)) > width) {
            MutableText compact = Text.empty();
            List<UsecAmmoType> order = contents.firingOrder();
            for (int index = 0; index < order.size(); index++) {
                if (index > 0) {
                    compact.append(Text.translatable("item.sparkwitch.usec_magazine.separator"));
                }
                UsecAmmoType type = order.get(index);
                compact.append(Text.literal(type.name().substring(0, 1)).withColor(type.color()));
            }
            shown = compact;
        }
        return wrap(shown, brackets);
    }

    private static Text wrap(Text rounds, boolean brackets) {
        return brackets ? Text.translatable(KEY + "tokens", rounds) : rounds;
    }

    private void centered(DrawContext context, Text text, Column column, int y, int color) {
        int from = columnFrom(column);
        int to = columnTo(column);
        int width = textRenderer.getWidth(text);
        text(context, text, from + (to - from - width + 1) / 2, y, color);
    }

    private int text(DrawContext context, Text text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
        return textRenderer.getWidth(text);
    }

    private void text(DrawContext context, OrderedText text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
    }

    /** Trims styled text to {@code width}, ending in an ellipsis when cut. 把带样式文字裁到 width，被截断时以省略号结尾。 */
    private OrderedText fit(Text text, int width) {
        if (width <= 0) {
            return OrderedText.EMPTY;
        }
        if (textRenderer.getWidth(text) <= width) {
            return text.asOrderedText();
        }
        int ellipsis = textRenderer.getWidth(ELLIPSIS);
        if (ellipsis > width) {
            return OrderedText.EMPTY;
        }
        StringVisitable head = textRenderer.trimToWidth(text, width - ellipsis);
        return Language.getInstance().reorder(StringVisitable.concat(head, StringVisitable.plain(ELLIPSIS)));
    }
}
