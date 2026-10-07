package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackManager;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手雷爆炸前让范围内的魔术师皮套进入“被手雷击杀/强制结束”流程。
 * Wathe 原版爆炸只遍历 ServerPlayerEntity，皮套是自定义 LivingEntity，必须单独桥接。
 */
@Mixin(GrenadeEntity.class)
public abstract class MagicianGrenadePlaybackHitMixin extends ThrownItemEntity {
    protected MagicianGrenadePlaybackHitMixin(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void sparkwitch$stopPlaybackInExplosion(HitResult hitResult, CallbackInfo ci) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        MagicianPlaybackManager.stopByExplosion(
                world,
                getBoundingBox().expand(3.0D),
                getOwner() instanceof ServerPlayerEntity player ? player : null,
                getStack().getTranslationKey()
        );
    }
}
