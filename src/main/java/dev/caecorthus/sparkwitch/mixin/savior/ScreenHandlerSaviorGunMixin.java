package dev.caecorthus.sparkwitch.mixin.savior;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorGunPickupRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server authority for container clicks, which move items without insertStack: refuses pulling a foreign gun into the
 * Savior and any click/swap/throw/quick-move on the Savior's hidden revolver in the client-only locked rows 9–26
 * ({@link SaviorGunPickupRules#blocksSlotClick}), then resyncs the screen.
 * 容器点击的服务端裁定（点击绕过 insertStack）：拒绝把外部枪械拿进救世主，也拒绝对仅客户端锁定的 9–26 行中隐藏左轮的
 * 任何点击/交换/丢出/快速移动（{@link SaviorGunPickupRules#blocksSlotClick}），随后重新同步界面。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerSaviorGunMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$blockSaviorGunSlotClick(int slotIndex, int button, SlotActionType actionType,
                                                    PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (SaviorGunPickupRules.blocksSlotClick(handler, player, slotIndex, button, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
