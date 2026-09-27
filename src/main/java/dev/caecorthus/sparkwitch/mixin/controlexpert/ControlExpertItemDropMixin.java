package dev.caecorthus.sparkwitch.mixin.controlexpert;

import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertInventoryRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server hotbar-drop guard for Control Expert items. Runs before the stack is removed (a later dropItem cancel would
 * delete it), then resyncs the whole handler because the client already predicted the removal.
 * 控场专家道具的服务端快捷栏丢弃防护。在物品被移除之前执行（之后再取消 dropItem 会直接删除物品），
 * 并重新同步整个界面，因为客户端已经预测了移除。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ControlExpertItemDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepControlExpertItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (ControlExpertInventoryRules.blocksDrop(player, player.getInventory().getMainHandStack())) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
