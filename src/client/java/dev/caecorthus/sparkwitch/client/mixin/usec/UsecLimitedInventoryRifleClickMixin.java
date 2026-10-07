package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentClient;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * USEC D16 seam on Wathe's in-round inventory: an empty-cursor right press on the sniper rifle opens the attachment
 * screen instead of picking the rifle up. Wathe's {@code mouseClicked} sends a slot click only with an empty cursor (a
 * press with rounds on the cursor starts a drag and clicks on release), so cursor loading is never intercepted. Every
 * other press is passed through unchanged, and this wrapper chains with the armor panel's wrapper on the same call.
 * Client presentation only: the screen sends {@code sparkwitch:usec_attachment}, which the server validates. The
 * selector is pinned in {@code watheClientMixinContracts}.
 * USEC D16 在 Wathe 局内背包上的接缝：空光标右键按下狙击步枪时打开配件界面，而不是拿起步枪。Wathe 的 {@code mouseClicked}
 * 只在空光标时发送栏位点击（光标拿着子弹时按下会开始拖动，在松开时才点击），因此光标装填永远不会被拦截。其他按下原样放行，
 * 本包装与护甲面板对同一调用的包装可以串联。仅为客户端展示：界面发送由服务端校验的 {@code sparkwitch:usec_attachment}。
 * 选择器固定在 {@code watheClientMixinContracts} 中。
 */
@Mixin(LimitedHandledScreen.class)
public abstract class UsecLimitedInventoryRifleClickMixin {
    // Keep Yarn: intermediary selectors crash runClient; remapJar emits the intermediary form verifyClientMixinSelectors checks.
    // 保持 Yarn：intermediary 选择器会使 runClient 崩溃；remapJar 生成的 intermediary 形式由 verifyClientMixinSelectors 校验。
    @WrapOperation(
            method = "mouseClicked(DDI)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/gui/screen/ingame/LimitedHandledScreen;onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V"
            )
    )
    private void sparkwitch$openUsecAttachments(LimitedHandledScreen<?> screen, Slot slot, int slotId, int button,
                                                SlotActionType action, Operation<Void> original) {
        if (UsecAttachmentClient.tryOpen(screen, screen.getScreenHandler(), slot, button, action)) {
            return;
        }
        original.call(screen, slot, slotId, button, action);
    }
}
