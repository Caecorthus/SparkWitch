package dev.caecorthus.sparkwitch.mixin.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.perception.BlindSoundPerception;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Read-only server seam for the Blind's perception: HEAD of the two methods every public world sound funnels through,
 * plus HEAD of the explosion method (wind charges and explosions reach clients as explosion packets). It never cancels
 * and never changes an argument, so vanilla delivery is untouched; private sounds never pass here.
 * 盲人感知的只读服务端接缝：所有公开世界声音都会经过的两个方法的 HEAD，以及爆炸方法的 HEAD（风弹与爆炸以爆炸数据包到达
 * 客户端）。从不取消、从不修改参数，原版投递不受影响；私有声音从不经过这里。
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldBlindPerceptionMixin {
    @Inject(
            method = "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;"
                    + "Lnet/minecraft/sound/SoundCategory;FFJ)V",
            at = @At("HEAD")
    )
    private void sparkwitch$perceiveWorldSound(@Nullable PlayerEntity except, double x, double y, double z,
                                               RegistryEntry<SoundEvent> sound, SoundCategory category, float volume,
                                               float pitch, long seed, CallbackInfo ci) {
        BlindSoundPerception.onWorldSound((ServerWorld) (Object) this, except, x, y, z, sound, category);
    }

    @Inject(
            method = "playSoundFromEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;"
                    + "Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V",
            at = @At("HEAD")
    )
    private void sparkwitch$perceiveEntitySound(@Nullable PlayerEntity except, Entity entity,
                                                RegistryEntry<SoundEvent> sound, SoundCategory category, float volume,
                                                float pitch, long seed, CallbackInfo ci) {
        BlindSoundPerception.onEntitySound((ServerWorld) (Object) this, except, entity, sound, category);
    }

    @Inject(
            method = "createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;"
                    + "Lnet/minecraft/world/explosion/ExplosionBehavior;DDDFZLnet/minecraft/world/World$ExplosionSourceType;"
                    + "Lnet/minecraft/particle/ParticleEffect;Lnet/minecraft/particle/ParticleEffect;"
                    + "Lnet/minecraft/registry/entry/RegistryEntry;)Lnet/minecraft/world/explosion/Explosion;",
            at = @At("HEAD")
    )
    private void sparkwitch$perceiveExplosion(@Nullable Entity entity, @Nullable DamageSource damageSource,
                                              @Nullable ExplosionBehavior behavior, double x, double y, double z,
                                              float power, boolean createFire,
                                              World.ExplosionSourceType explosionSourceType, ParticleEffect particle,
                                              ParticleEffect emitterParticle, RegistryEntry<SoundEvent> soundEvent,
                                              CallbackInfoReturnable<Explosion> cir) {
        BlindSoundPerception.onExplosion((ServerWorld) (Object) this, entity, x, y, z);
    }
}
