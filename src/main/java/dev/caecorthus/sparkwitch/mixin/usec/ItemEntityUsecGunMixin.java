package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecGunRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owner O2: a USEC never picks a {@code wathe:guns} item up from the ground (the Wathe {@code ItemEntityMixin} and
 * SparkTraits Impostor precedent, HEAD of {@code onPlayerCollision}). Server only; a creative player and a former USEC
 * are free (see {@link UsecGunRules}).
 * 所有者 O2：USEC 永远不能从地面拾取 {@code wathe:guns} 物品（沿用 Wathe {@code ItemEntityMixin} 与 SparkTraits 内鬼的先例，
 * 挂在 {@code onPlayerCollision} 的 HEAD）。仅服务端；创造模式玩家与前 USEC 不受限制（见 {@link UsecGunRules}）。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityUsecGunMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$blockUsecGunPickup(PlayerEntity player, CallbackInfo ci) {
        if (UsecGunRules.blocksGun(player, this.getStack())) {
            ci.cancel();
        }
    }
}
