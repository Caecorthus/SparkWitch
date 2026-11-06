package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BEAR S3 catch-all: every vanilla explosion on the server (the method every {@code createExplosion} overload funnels
 * into, the same descriptor the Blind's perception seam reads) carves a hole of {@code power} blocks into smoke.
 * Nothing in Wathe, NoellesRoles, SparkStrength, SparkTraits or SparkWitch calls it today; this covers commands, TNT and
 * other mods. Read-only: never cancels and never changes an argument.
 * BEAR S3 兜底：服务端每次原版爆炸（所有 {@code createExplosion} 重载最终汇入的方法，与盲人感知接缝读取的描述符相同）都在烟雾中
 * 炸开半径为 {@code power} 格的洞。目前 Wathe、NoellesRoles、SparkStrength、SparkTraits 与 SparkWitch 都不调用它；此处覆盖指令、
 * TNT 与其他模组。只读：从不取消、从不修改参数。
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldExplosionSmokeCarveMixin {
    @Inject(
            method = "createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;"
                    + "Lnet/minecraft/world/explosion/ExplosionBehavior;DDDFZLnet/minecraft/world/World$ExplosionSourceType;"
                    + "Lnet/minecraft/particle/ParticleEffect;Lnet/minecraft/particle/ParticleEffect;"
                    + "Lnet/minecraft/registry/entry/RegistryEntry;)Lnet/minecraft/world/explosion/Explosion;",
            at = @At("HEAD")
    )
    private void sparkwitch$carveExplosion(@Nullable Entity entity, @Nullable DamageSource damageSource,
                                          @Nullable ExplosionBehavior behavior, double x, double y, double z,
                                          float power, boolean createFire,
                                          World.ExplosionSourceType explosionSourceType, ParticleEffect particle,
                                          ParticleEffect emitterParticle, RegistryEntry<SoundEvent> soundEvent,
                                          CallbackInfoReturnable<Explosion> cir) {
        RangedSmokeCarve.blast((ServerWorld) (Object) this, new Vec3d(x, y, z), power);
    }
}
