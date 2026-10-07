package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecInventoryRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server selected-slot drop (Q) guard for the USEC bound items. Runs before the stack is removed (the later
 * {@code dropItem} guard would have to re-insert it, possibly elsewhere, and a loaded rifle keeps its state in place),
 * then resyncs the whole handler because the client already predicted the removal. Potion Gunner pattern.
 * USEC 绑定物品的服务端选中栏位丢弃（Q）防护。在物品被移除之前执行（之后的 {@code dropItem} 防护只能把它放回，位置可能改变；
 * 已装填的步枪由此原地保持状态），并重新同步整个界面，因为客户端已经预测了移除。沿用药炮手的做法。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityUsecDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepUsecItemSelected(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (UsecInventoryRules.blocksSelectedDrop(player)) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
