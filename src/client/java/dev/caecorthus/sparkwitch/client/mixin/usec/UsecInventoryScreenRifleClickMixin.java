package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * USEC D16 seam on the vanilla inventory: the same empty-cursor right press as on Wathe's inventory, on vanilla
 * {@code HandledScreen.mouseClicked}. {@link UsecAttachmentClient#tryOpen} accepts only the player screen handler, so
 * creative, chests and every other container keep their clicks; a shift press is a {@code QUICK_MOVE} there and is
 * never intercepted. Client presentation only; the server validates every attachment packet.
 * USEC D16 在原版背包上的接缝：与 Wathe 背包相同的空光标右键按下，挂在原版 {@code HandledScreen.mouseClicked} 上。
 * {@link UsecAttachmentClient#tryOpen} 只接受玩家界面处理器，因此创造模式、箱子及其他容器的点击保持不变；Shift 按下在原版中
 * 是 {@code QUICK_MOVE}，从不拦截。仅为客户端展示；服务端校验每个配件数据包。
 */
@Mixin(HandledScreen.class)
public abstract class UsecInventoryScreenRifleClickMixin {
    @WrapOperation(
            method = "mouseClicked(DDI)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V"
            )
    )
    private void sparkwitch$openUsecAttachments(HandledScreen<?> screen, Slot slot, int slotId, int button,
                                                SlotActionType action, Operation<Void> original) {
        if (UsecAttachmentClient.tryOpen(screen, screen.getScreenHandler(), slot, button, action)) {
            return;
        }
        original.call(screen, slot, slotId, button, action);
    }
}
