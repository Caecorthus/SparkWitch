package dev.caecorthus.sparkwitch.mixin.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerInventoryRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server hotbar-drop guard: Seeker device items never drop, and nothing drops while a remote session is open. Runs
 * before the stack is removed (a later dropItem cancel would delete it), then resyncs the whole handler because the
 * client already predicted the removal. Vanilla target, default remap.
 * 服务端快捷栏丢弃防护：搜寻者设备物品永不丢弃，遥控会话打开期间禁止一切丢弃。在物品被移除之前执行
 * （之后再取消 dropItem 会直接删除物品），并重新同步整个界面，因为客户端已经预测了移除。原版目标，默认重映射。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class SeekerItemDropMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepSeekerItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (SeekerInventoryRules.blocksDrop(player, player.getInventory().getMainHandStack())) {
            player.currentScreenHandler.syncState();
            cir.setReturnValue(false);
        }
    }
}
