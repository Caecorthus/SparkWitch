package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.WitherGoheiInventoryRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server selected-slot drop (Q) guard for the Wither Gohei. Runs before the stack is removed, then resyncs the
 * whole handler because the client already predicted the removal.
 * 凋灵御币的服务端选中栏位丢弃（Q）防护。在物品被移除之前执行，并重新同步整个界面，因为客户端已经预测了移除。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityWitherGoheiDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepWitherGoheiItemSelected(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (WitherGoheiInventoryRules.blocksSelectedDrop(player)) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
