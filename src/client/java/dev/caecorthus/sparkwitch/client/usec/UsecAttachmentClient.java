package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.mixin.usec.UsecCreativeSlotAccessor;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.SlotRef;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAttachmentCursorLoading;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecAttachmentC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Client only. The attachment screen's opener, shared by the two inventory right-click seams
 * ({@code client/mixin/usec/UsecLimitedInventoryRifleClickMixin} on Wathe's in-round inventory and
 * {@code UsecInventoryScreenRifleClickMixin} on vanilla {@code HandledScreen}, which also carries the creative
 * inventory's clicks). The seams call {@link #tryOpen} directly, and the screen sends {@code sparkwitch:usec_attachment}
 * itself. {@link #register} only installs the creative-screen probe of cursor loading.
 * 仅客户端。配件界面的打开入口，由两个背包右键接缝共用（Wathe 局内背包上的
 * {@code client/mixin/usec/UsecLimitedInventoryRifleClickMixin} 与原版 {@code HandledScreen} 上的
 * {@code UsecInventoryScreenRifleClickMixin}，后者也承载创造模式物品栏的点击）。接缝直接调用 {@link #tryOpen}，界面自行发送
 * {@code sparkwitch:usec_attachment}。{@link #register} 只安装光标装填所用的创造界面探针。
 */
public final class UsecAttachmentClient {
    private UsecAttachmentClient() {
    }

    /**
     * Client authority seam (owner, 2026-10-07: creative players may modify the rifle): the creative inventory screen
     * resolves its slot clicks on the client and syncs whole stacks, so the server never sees a cursor load made there;
     * cursor loading asks this probe to let the client write exactly those clicks. Survival stays server-only.
     * 客户端权威接缝（所有者 2026-10-07：创造模式玩家也可以改枪）：创造模式物品栏在客户端结算栏位点击并同步整个物品堆，
     * 服务端看不到在那里进行的光标装填；光标装填询问此探针，只让客户端写入这类点击。生存模式仍只由服务端写入。
     */
    public static void register() {
        UsecAttachmentCursorLoading.installCreativeScreenProbe(UsecAttachmentClient::creativeScreenOpen);
    }

    private static boolean creativeScreenOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen instanceof CreativeInventoryScreen
                && client.interactionManager != null && client.interactionManager.hasCreativeInventory();
    }

    /**
     * Called instead of the inventory's slot click: opens {@link UsecAttachmentScreen} over {@code parent} and returns
     * true for an empty-cursor right press on a rifle in the player's own inventory: a slot of the player screen
     * handler, or a creative inventory-tab slot read through the slot it wraps ({@link UsecAttachmentModel#ownInventoryIndex}).
     * Chests and other containers, the creative item list and the other creative tabs' hotbar row never open it;
     * otherwise returns false and the caller sends the click.
     * 代替背包的栏位点击调用：空光标右键按下玩家自己背包里的步枪时（玩家界面处理器的栏位，或经其包装栏位读取的创造模式背包
     * 标签页栏位，见 {@link UsecAttachmentModel#ownInventoryIndex}），在 {@code parent} 之上打开 {@link UsecAttachmentScreen}
     * 并返回 true。箱子等容器、创造模式物品列表与其他创造标签页的快捷栏行永远不会打开；否则返回 false，由调用方照常发送点击。
     */
    public static boolean tryOpen(Screen parent, ScreenHandler handler, @Nullable Slot slot, int button,
                                  SlotActionType action) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || slot == null) {
            return false;
        }
        Slot wrapped = slot instanceof UsecCreativeSlotAccessor creative ? creative.sparkwitch$getWrappedSlot() : null;
        Slot own = wrapped != null ? wrapped : slot;
        int index = UsecAttachmentModel.ownInventoryIndex(handler instanceof PlayerScreenHandler,
                new SlotRef(slot.inventory == player.getInventory(), slot.getIndex()),
                wrapped == null ? null : new SlotRef(wrapped.inventory == player.getInventory(), wrapped.getIndex()));
        boolean serverReady = SparkWitchServerConnection.isConfirmedServer()
                && ClientPlayNetworking.canSend(UsecAttachmentC2SPacket.ID);
        if (!UsecAttachmentModel.opensOnClick(button, action == SlotActionType.PICKUP,
                handler.getCursorStack().isEmpty(), index != UsecAttachmentModel.NO_SLOT, index,
                own.getStack().getItem() instanceof UsecRifleItem, player.isSpectator(), serverReady)) {
            return false;
        }
        client.setScreen(new UsecAttachmentScreen(parent, index));
        return true;
    }
}
