package dev.caecorthus.sparkwitch.client.armor;

import com.mojang.datafixers.util.Pair;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.CELL_LINE_ABOVE_V;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.CELL_LINE_BELOW_V;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.CELL_ROW_V;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.FIRST_ARMOR_SLOT_ID;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.FRAME;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.HOTBAR_END;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.HOTBAR_START;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.SECOND_ROW_END;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.SECOND_ROW_START;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.SLOT_SIZE;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.STRIP_HEIGHT;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.STRIP_LEFT_WIDTH;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.STRIP_RIGHT_CAP_U;
import static dev.caecorthus.sparkwitch.client.armor.LimitedInventoryArmorLayout.TOP_PART_HEIGHT;

/**
 * Client presentation of the armor block on Wathe's in-round inventory for every player (D10). It only draws and
 * hit-tests the real {@code PlayerScreenHandler} armor slots; every move is an ordinary vanilla slot click that the
 * server validates with the vanilla armor-slot rules, so the client never decides what may be worn.
 * Wathe 局内背包为所有玩家显示的护甲块（D10）的客户端展示。它只绘制并命中检测原版 {@code PlayerScreenHandler}
 * 护甲槽；每次移动都是普通的原版槽位点击，由服务器按原版护甲槽规则校验，客户端从不决定能穿什么。
 */
public final class LimitedInventoryArmorSlots {
    private LimitedInventoryArmorSlots() {
    }

    /**
     * True only for Wathe's player inventory screen on a confirmed SparkWitch server; other Wathe screens and
     * non-SparkWitch servers keep Wathe's hotbar-only screen.
     * 仅在已确认的 SparkWitch 服务器上的 Wathe 玩家背包界面为真；其他界面与非 SparkWitch 服务器保持 Wathe 原样。
     */
    public static boolean applies(Screen screen, @Nullable ScreenHandler handler) {
        return screen instanceof LimitedInventoryScreen
                && handler instanceof PlayerScreenHandler
                && SparkWitchServerConnection.isConfirmedServer();
    }

    /** The handler slot shown at armor index {@code index} (0 = head .. 3 = feet). / 护甲序号对应的槽位。 */
    public static Slot armorSlot(ScreenHandler handler, int index) {
        return handler.getSlot(FIRST_ARMOR_SLOT_ID + index);
    }

    /**
     * The layout anchor for Wathe's strip top {@code stripY}: the armor block centres on the inventory block, which
     * includes SparkFactionAPI's second row when that is shown.
     * Wathe 热栏顶边 {@code stripY} 对应的布局锚点：护甲块以物品栏块为中心，显示第二行时物品栏块包括 SparkFactionAPI 的第二行。
     */
    public static int anchorY(int stripY) {
        return LimitedInventoryArmorLayout.anchorY(stripY, SparkFactionSecondRowCompat.isShown());
    }

    /** {@code screenY} is the layout anchor ({@link #anchorY}). / {@code screenY} 为布局锚点。 */
    public static @Nullable Slot slotAt(ScreenHandler handler, int screenX, int screenY, double mouseX, double mouseY) {
        int index = LimitedInventoryArmorLayout.armorIndexAt(screenX, screenY, mouseX, mouseY);
        return index < 0 ? null : armorSlot(handler, index);
    }

    /**
     * Draws the block frame from Wathe's own {@code limited_inventory.png} (no copied art, so resource packs that
     * restyle Wathe's strip restyle this frame too): two strip rows of two cells, joined by the strip's inner lines.
     * 用 Wathe 自己的 {@code limited_inventory.png} 绘制边框（不复制美术资源，改动 Wathe 热栏的资源包也会作用于此）：
     * 两行各两格的热栏条片段，由热栏内线拼接。
     */
    public static void drawFrame(DrawContext context, int panelX, int panelY) {
        Identifier texture = LimitedInventoryScreen.BACKGROUND_TEXTURE;
        int capX = panelX + STRIP_LEFT_WIDTH;
        int interior = STRIP_LEFT_WIDTH - FRAME;
        context.drawTexture(texture, panelX, panelY, 0, 0, STRIP_LEFT_WIDTH, TOP_PART_HEIGHT);
        context.drawTexture(texture, capX, panelY, STRIP_RIGHT_CAP_U, 0, FRAME, TOP_PART_HEIGHT);
        int lineY = panelY + TOP_PART_HEIGHT;
        int[] lineV = {CELL_LINE_BELOW_V, CELL_LINE_ABOVE_V};
        for (int line = 0; line < lineV.length; line++) {
            context.drawTexture(texture, panelX, lineY + line, 0, CELL_ROW_V + line, FRAME, 1);
            context.drawTexture(texture, panelX + FRAME, lineY + line, FRAME, lineV[line], interior, 1);
            context.drawTexture(texture, capX, lineY + line, STRIP_RIGHT_CAP_U, CELL_ROW_V + line, FRAME, 1);
        }
        int bottomY = lineY + lineV.length;
        int bottomHeight = STRIP_HEIGHT - CELL_ROW_V;
        context.drawTexture(texture, panelX, bottomY, 0, CELL_ROW_V, STRIP_LEFT_WIDTH, bottomHeight);
        context.drawTexture(texture, capX, bottomY, STRIP_RIGHT_CAP_U, CELL_ROW_V, FRAME, bottomHeight);
    }

    /** Vanilla's empty-armor-slot sprite, drawn the way Wathe draws hotbar slot sprites. / 原版空护甲槽图标。 */
    public static void drawEmptyIcon(MinecraftClient client, DrawContext context, Slot slot, int x, int y) {
        Pair<Identifier, Identifier> sprite = slot.getBackgroundSprite();
        if (sprite != null) {
            context.drawSprite(x, y, 0, SLOT_SIZE, SLOT_SIZE,
                    client.getSpriteAtlas(sprite.getFirst()).apply(sprite.getSecond()));
        }
    }

    /**
     * Shift-click plan for {@code slot} (see {@link LimitedInventoryArmorLayout#planShiftClick}); the preferred armor
     * slot is vanilla's {@code getPreferredEquipmentSlot}, the same test {@code ArmorSlot.canInsert} uses.
     * Shift 点击规划；首选护甲槽取原版 {@code getPreferredEquipmentSlot}，与 {@code ArmorSlot.canInsert} 相同。
     */
    public static LimitedInventoryArmorLayout.@Nullable Swap planShiftClick(PlayerEntity player, ScreenHandler handler,
                                                                           Slot slot) {
        ItemStack stack = slot.getStack();
        int preferred = preferredArmorSlotId(player, stack);
        boolean preferredEmpty = preferred >= 0 && !handler.getSlot(preferred).hasStack();
        return LimitedInventoryArmorLayout.planShiftClick(
                slot.id, !stack.isEmpty(), preferred, preferredEmpty, firstEmptyHotbarButton(handler));
    }

    /**
     * The armor slot the cursor piece is put back into before the screen closes (see
     * {@link LimitedInventoryArmorLayout#cursorReturnArmorSlotId}), or -1.
     * 关闭界面前光标上的护甲应放回的护甲槽，没有则为 -1。
     */
    public static int cursorReturnSlotId(PlayerEntity player, ScreenHandler handler) {
        ItemStack cursor = handler.getCursorStack();
        int preferred = preferredArmorSlotId(player, cursor);
        boolean preferredEmpty = preferred >= 0 && !handler.getSlot(preferred).hasStack();
        return LimitedInventoryArmorLayout.cursorReturnArmorSlotId(
                !cursor.isEmpty(), preferred, preferredEmpty, shownSlotFree(handler));
    }

    private static int preferredArmorSlotId(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        EquipmentSlot equipment = player.getPreferredEquipmentSlot(stack);
        return LimitedInventoryArmorLayout.armorSlotIdForEntitySlot(
                equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR, equipment.getEntitySlotId());
    }

    /**
     * A plain press on a worn piece with an empty cursor is allowed only while a shown slot is free (see
     * {@link LimitedInventoryArmorLayout#mayPickUpArmor}); every other press is Wathe's.
     * 空光标点击已穿护甲仅在有空的显示栏位时允许；其他按下交给 Wathe。
     */
    public static boolean mayPickUp(ScreenHandler handler, Slot slot) {
        return LimitedInventoryArmorLayout.mayPickUpArmor(slot.id, slot.hasStack(), handler.getCursorStack().isEmpty(),
                shownSlotFree(handler));
    }

    /**
     * A hotbar slot is free, or, with SparkFactionAPI 0.1.5.13+ (client and server versions always match), a
     * second-row slot: where the server's close offer of a cursor stack lands before any hidden slot.
     * 有空快捷栏格，或在 SparkFactionAPI 0.1.5.13+ 下（客户端与服务端版本总是一致）有空的第二行格：服务器在关闭界面时
     * 放回光标物品会先落在这些位置，而不是隐藏栏位。
     */
    private static boolean shownSlotFree(ScreenHandler handler) {
        if (firstEmptyHotbarButton(handler) >= 0) {
            return true;
        }
        if (!SparkFactionSecondRowCompat.isShown()) {
            return false;
        }
        for (int id = SECOND_ROW_START; id < SECOND_ROW_END; id++) {
            if (!handler.getSlot(id).hasStack()) {
                return true;
            }
        }
        return false;
    }

    private static int firstEmptyHotbarButton(ScreenHandler handler) {
        for (int id = HOTBAR_START; id < HOTBAR_END; id++) {
            if (!handler.getSlot(id).hasStack()) {
                return id - HOTBAR_START;
            }
        }
        return -1;
    }
}
