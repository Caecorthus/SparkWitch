package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecGunRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses a player whose live role is exactly USEC taking a {@code wathe:guns} item out of a foreign slot (owner O2,
 * {@link UsecGunRules}), because container clicks move stacks without {@code PlayerInventory#insertStack}.
 * Server-authoritative: a rejected click is cancelled before any slot changes and the client's predicted move is
 * overwritten by a content resync. The USEC bound items themselves have no slot-click rule any more: they move like
 * every other item under Wathe's own rules (owner, 2026-11-05).
 * 拒绝当前身份恰为 USEC 的玩家从外部栏位拿走 {@code wathe:guns} 物品（所有者 O2，{@link UsecGunRules}），因为容器点击移动
 * 物品时不经过 {@code PlayerInventory#insertStack}。由服务端裁定：被拒绝的点击在任何栏位变化之前取消，并通过内容重同步覆盖
 * 客户端的预测移动。USEC 绑定物品本身不再有栏位点击规则：它们像其他物品一样遵循 Wathe 自身规则移动（所有者 2026-11-05）。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerUsecItemMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$refuseForeignGunForUsec(
            int slotIndex,
            int button,
            SlotActionType actionType,
            PlayerEntity player,
            CallbackInfo ci
    ) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (player instanceof ServerPlayerEntity
                && UsecGunRules.blocksSlotClick(handler, player, slotIndex, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
