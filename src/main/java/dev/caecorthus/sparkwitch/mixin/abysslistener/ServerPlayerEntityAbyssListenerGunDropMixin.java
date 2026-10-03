package dev.caecorthus.sparkwitch.mixin.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout.AbyssListenerInventoryRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server Q-drop guard for the Shriek Gun. Runs before the selected stack is removed (a later dropItem cancel would
 * delete it), then resyncs the whole handler because the client already predicted the removal.
 * 啸音铳的服务端 Q 键丢弃防护。在选中物品被移除之前执行（之后再取消 dropItem 会直接删除物品），
 * 并重新同步整个界面，因为客户端已经预测了移除。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityAbyssListenerGunDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepSelectedShriekGun(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (AbyssListenerInventoryRules.blocksDrop(player.getInventory().getMainHandStack())) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
