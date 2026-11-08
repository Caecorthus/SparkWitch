package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearLoadoutService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HEAD of the player drop method, which {@code ServerPlayerEntity} reaches through {@code super}: a BEAR item dropped
 * by a dead player (vanilla's inventory drop on death, which Wathe's KEEP_INVENTORY normally skips) is cancelled, so
 * it vanishes ({@code BearLoadoutService#vanishesOnDeath}). Every drop by a living player follows Wathe's own rules
 * (owner, 2026-11-05). Server-authoritative; the client prediction is never touched.
 * 玩家丢弃方法的 HEAD（{@code ServerPlayerEntity} 经 {@code super} 调用）：已死亡玩家丢出的 BEAR 物品（原版死亡时的背包掉落，
 * Wathe 的 KEEP_INVENTORY 通常会跳过它）被取消，因此直接消失（{@code BearLoadoutService#vanishesOnDeath}）。存活玩家的一切丢弃
 * 都遵循 Wathe 自身规则（所有者 2026-11-05）。由服务端裁定；从不改动客户端预测。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityBearItemMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$vanishBearItemOnDeath(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (BearLoadoutService.vanishesOnDeath((PlayerEntity) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }
}
