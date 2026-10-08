package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecGunRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecInventoryRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the USEC bound items inside their holder's own inventory slots while every move inside them (cursor
 * included) stays allowed. Server-authoritative: a rejected click is cancelled before any slot changes (so before an
 * item's {@code onClicked}/{@code onStackClicked} hooks) and the client's predicted move is overwritten by a content
 * resync. The same HEAD also refuses a USEC taking a {@code wathe:guns} item out of a foreign slot (owner O2,
 * {@link UsecGunRules}), because container clicks move stacks without {@code PlayerInventory#insertStack}.
 * 使 USEC 绑定物品只留在持有者自身的背包栏位中，而在其中的一切移动（含光标）仍然允许。由服务端裁定：被拒绝的点击在任何栏位
 * 变化之前（因此也在物品的 {@code onClicked}/{@code onStackClicked} 钩子之前）取消，并通过内容重同步覆盖客户端的预测移动。
 * 同一 HEAD 还拒绝 USEC 从外部栏位拿走 {@code wathe:guns} 物品（所有者 O2，{@link UsecGunRules}），因为容器点击移动物品时
 * 不经过 {@code PlayerInventory#insertStack}。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerUsecItemMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepUsecItem(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (player instanceof ServerPlayerEntity
                && (UsecInventoryRules.blocksSlotClick(handler, player, slotIndex, button, actionType)
                || UsecGunRules.blocksSlotClick(handler, player, slotIndex, actionType))) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
