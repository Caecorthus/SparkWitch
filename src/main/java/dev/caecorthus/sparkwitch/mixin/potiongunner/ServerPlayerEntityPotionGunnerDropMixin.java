package dev.caecorthus.sparkwitch.mixin.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerInventoryRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server selected-slot drop (Q) guard for the launcher and shells. Runs before the stack is removed (the later
 * {@code dropItem} guard would have to re-insert it, possibly elsewhere), then resyncs the whole handler because the
 * client already predicted the removal. Control Expert pattern.
 * 炮筒与炮弹的服务端选中栏位丢弃（Q）防护。在物品被移除之前执行（之后的 {@code dropItem} 防护只能把它放回，位置可能改变），
 * 并重新同步整个界面，因为客户端已经预测了移除。沿用控场专家的做法。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityPotionGunnerDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepPotionGunnerItemSelected(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (PotionGunnerInventoryRules.blocksSelectedDrop(player)) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
