package dev.caecorthus.sparkwitch.mixin.recruit;

import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.recruit.RecruitRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.recruit.RecruitRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Recruit's one ground gun per round. Wathe's own {@code ItemEntityMixin} already lets an innocent with no gun
 * pick one up; this only adds the per-round limit. HEAD vetoes a second {@code wathe:guns} pickup (the USEC and
 * SparkTraits Impostor precedent); the {@code sendPickup} hook, which vanilla reaches only after
 * {@code insertStack} succeeded, marks the first one used. {@code item} is captured before the insert, because a
 * fully picked-up stack reads as air afterwards (Wathe's pickup record does the same).
 * 新兵每局只能从地面捡一把枪。Wathe 自身的 {@code ItemEntityMixin} 已允许没有枪的好人捡枪；这里只增加每局上限。
 * HEAD 否决第二次捡起 {@code wathe:guns}（沿用 USEC 与 SparkTraits 内鬼的先例）；原版只有在 {@code insertStack}
 * 成功后才会调用 {@code sendPickup}，在此标记第一把已用掉。{@code item} 取自插入前的局部变量，因为整组被捡走后
 * 物品堆会变成空气（Wathe 的拾取记录同样如此）。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityRecruitGunMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$blockSecondRecruitGun(PlayerEntity player, CallbackInfo ci) {
        if (RecruitRules.blocksGunPickup(player, this.getStack())) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;sendPickup(Lnet/minecraft/entity/Entity;I)V"))
    private void sparkwitch$recordRecruitGunPickup(PlayerEntity player, CallbackInfo ci, @Local Item item) {
        RecruitRuntime.onGroundPickup(player, item);
    }
}
