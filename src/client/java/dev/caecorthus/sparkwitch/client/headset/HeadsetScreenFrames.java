package dev.caecorthus.sparkwitch.client.headset;

import dev.caecorthus.sparkwitch.headset.HeadsetSlot;
import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;

/**
 * Client presentation of the headset slot (E1) on vanilla's survival inventory and creative inventory tab. The slot
 * itself is drawn and clicked by vanilla (it is a real handler slot); these screens' background textures simply have no
 * cell there, so a vanilla 18x18 slot frame ({@code minecraft:container/slot}) is drawn under it. Survival: the slot's
 * own position (77,44), above the offhand. Creative tab: (127,20), the mirror of the offhand cell across the player
 * preview (vanilla would otherwise stack it on hotbar cell 37). Drawn only while the slot is enabled (a confirmed
 * SparkWitch server).
 * 耳机栏位（E1）在原版生存背包与创造模式物品栏页的客户端展示。栏位本身由原版绘制与点击（它是真实的界面栏位）；这些界面的背景
 * 贴图在那里没有格子，因此在其下方绘制原版 18x18 栏位框（{@code minecraft:container/slot}）。生存：栏位自身坐标 (77,44)，位于副手
 * 上方。创造模式物品栏页：(127,20)，即副手格隔着玩家预览的镜像位置（否则原版会把它叠在快捷栏 37 号格上）。仅在栏位启用（已确认的
 * SparkWitch 服务器）时绘制。
 */
public final class HeadsetScreenFrames {
    /** Vanilla's plain slot frame sprite (18x18). / 原版普通栏位框精灵（18x18）。 */
    public static final Identifier SLOT_FRAME = Identifier.ofVanilla("container/slot");
    public static final int FRAME_SIZE = 18;
    /** Creative inventory tab position (item corner). / 创造模式物品栏页中的位置（物品左上角）。 */
    public static final int CREATIVE_X = 127;
    public static final int CREATIVE_Y = 20;

    private HeadsetScreenFrames() {
    }

    /** The handler's enabled headset slot is present. / 界面中存在已启用的耳机栏位。 */
    public static boolean shown(ScreenHandler handler) {
        return handler.slots.size() > HeadsetSlots.HANDLER_SLOT_ID
                && handler.slots.get(HeadsetSlots.HANDLER_SLOT_ID) instanceof HeadsetSlot slot
                && slot.isEnabled();
    }

    /** Frame for an item drawn at screen ({@code itemX}, {@code itemY}). / 物品绘制于屏幕坐标处时的栏位框。 */
    public static void drawFrame(DrawContext context, int itemX, int itemY) {
        context.drawGuiTexture(SLOT_FRAME, itemX - 1, itemY - 1, FRAME_SIZE, FRAME_SIZE);
    }
}
