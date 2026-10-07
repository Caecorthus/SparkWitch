package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleItem;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecAttachmentC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Client only. The attachment screen's opener, shared by the two inventory right-click seams
 * ({@code client/mixin/usec/UsecLimitedInventoryRifleClickMixin} on Wathe's in-round inventory and
 * {@code UsecInventoryScreenRifleClickMixin} on the vanilla one). Nothing needs registering: the seams call
 * {@link #tryOpen} directly, and the screen sends {@code sparkwitch:usec_attachment} itself.
 * 仅客户端。配件界面的打开入口，由两个背包右键接缝共用（Wathe 局内背包上的
 * {@code client/mixin/usec/UsecLimitedInventoryRifleClickMixin} 与原版背包上的 {@code UsecInventoryScreenRifleClickMixin}）。
 * 无需注册任何内容：接缝直接调用 {@link #tryOpen}，界面自行发送 {@code sparkwitch:usec_attachment}。
 */
public final class UsecAttachmentClient {
    private UsecAttachmentClient() {
    }

    public static void register() {
    }

    /**
     * Called instead of the inventory's slot click: opens {@link UsecAttachmentScreen} over {@code parent} and returns
     * true for an empty-cursor right press on a rifle in the player's own inventory (player screen handler only, so
     * creative, chests and other containers never open it); otherwise returns false and the caller sends the click.
     * 代替背包的栏位点击调用：空光标右键按下玩家自己背包里的步枪时（仅限玩家界面处理器，因此创造模式、箱子等容器永远不会打开），
     * 在 {@code parent} 之上打开 {@link UsecAttachmentScreen} 并返回 true；否则返回 false，由调用方照常发送点击。
     */
    public static boolean tryOpen(Screen parent, ScreenHandler handler, @Nullable Slot slot, int button,
                                  SlotActionType action) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || slot == null || !(handler instanceof PlayerScreenHandler)) {
            return false;
        }
        boolean serverReady = SparkWitchServerConnection.isConfirmedServer()
                && ClientPlayNetworking.canSend(UsecAttachmentC2SPacket.ID);
        if (!UsecAttachmentModel.opensOnClick(button, action == SlotActionType.PICKUP,
                handler.getCursorStack().isEmpty(), slot.inventory == player.getInventory(), slot.getIndex(),
                slot.getStack().getItem() instanceof UsecRifleItem, player.isSpectator(), serverReady)) {
            return false;
        }
        client.setScreen(new UsecAttachmentScreen(parent, slot.getIndex()));
        return true;
    }
}
