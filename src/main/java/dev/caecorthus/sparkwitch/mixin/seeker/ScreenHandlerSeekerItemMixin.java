package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerInventoryRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server slot-click guard: Seeker device items stay inside their holder's own inventory slots, and every click is
 * denied while a remote session is open. Server-authoritative: a rejected click is cancelled before any slot changes
 * and the client's predicted move is overwritten by a content resync. Vanilla target, default remap.
 * 服务端槽位点击守卫：搜寻者设备物品只留在持有者自身的背包栏位中，遥控会话打开期间禁止一切点击。由服务端裁定：
 * 被拒绝的点击在任何栏位变化之前取消，并通过内容重同步覆盖客户端的预测移动。原版目标，默认重映射。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerSeekerItemMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepSeekerItem(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        if (player instanceof ServerPlayerEntity
                && SeekerInventoryRules.blocksSlotClick(player, slotIndex, button, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
