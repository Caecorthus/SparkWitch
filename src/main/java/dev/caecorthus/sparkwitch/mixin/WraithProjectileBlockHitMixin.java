package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The impact half of {@link WraithExplosionTriggerMixin}: a projectile thrown by an active Wraith (the Wind Spirit's
 * wind charge) does not ring bells, pulse target blocks or break decorated pots, which never ask
 * {@code Explosion#canTriggerBlocks}. The projectile still hits and explodes as before.
 * {@link WraithExplosionTriggerMixin} 的命中部分：激活冤魂扔出的弹射物（风精灵的风弹）不会敲钟、触发标靶或打碎饰纹陶罐，
 * 这些反应不经过 Explosion#canTriggerBlocks。弹射物本身照常命中与爆炸。
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class WraithProjectileBlockHitMixin {
    @Inject(
            method = "onProjectileHit(Lnet/minecraft/world/World;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;Lnet/minecraft/entity/projectile/ProjectileEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$activeWraithProjectileTriggersNoBlocks(
            World world,
            BlockState state,
            BlockHitResult hit,
            ProjectileEntity projectile,
            CallbackInfo ci
    ) {
        if (!WraithParticipationRules.mayTriggerBlocks(
                projectile.getOwner() instanceof PlayerEntity player && WraithStateService.isActive(player))) {
            ci.cancel();
        }
    }
}
