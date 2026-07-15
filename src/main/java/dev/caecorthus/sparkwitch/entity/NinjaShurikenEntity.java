/*
 * Derived from StarRailExpress ThrowingKnifeEntity at commit
 * 220d03ede335fc7971fcffbc302bc68bb91b0209 (GPL-3.0-only).
 * SparkWitch adaptations are AGPL-3.0-only; see THIRD_PARTY_NOTICES.md.
 */
package dev.caecorthus.sparkwitch.entity;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

public final class NinjaShurikenEntity extends PersistentProjectileEntity implements FlyingItemEntity {
    public static final int MAX_LIFETIME_TICKS = 8 * 20;

    public NinjaShurikenEntity(EntityType<? extends NinjaShurikenEntity> type, World world) {
        super(type, world);
        pickupType = PickupPermission.DISALLOWED;
    }

    public NinjaShurikenEntity(World world, LivingEntity owner, ItemStack stack) {
        super(
                SparkWitchEntities.ninjaShuriken(),
                owner,
                world,
                stack.copyWithCount(1),
                null
        );
        pickupType = PickupPermission.DISALLOWED;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient() && random.nextFloat() < 0.2F) {
            getWorld().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
        if (age >= MAX_LIFETIME_TICKS) {
            discard();
        }
    }

    @Override
    protected double getGravity() {
        return 0.0;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity owner = getOwner();
        Entity hitEntity = entityHitResult.getEntity();
        if (!(owner instanceof ServerPlayerEntity thrower)
                || !(hitEntity instanceof ServerPlayerEntity victim)
                || victim.getUuid().equals(thrower.getUuid())
                || !GameFunctions.isPlayerPlayingAndAlive(victim)
                || !GameFunctions.isPlayerAliveAndSurvival(victim)) {
            return;
        }

        if (getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.CRIT,
                    entityHitResult.getPos().x,
                    entityHitResult.getPos().y + 1.25,
                    entityHitResult.getPos().z,
                    10,
                    0.3,
                    0.3,
                    0.3,
                    0.15
            );
            serverWorld.playSound(
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    SoundEvents.BLOCK_CHAIN_HIT,
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.0F
            );
            GameFunctions.killPlayer(
                    victim,
                    true,
                    thrower,
                    SparkWitchDeathReasons.NINJA_SHURIKEN_KILL
            );
            discard();
        }
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return SparkWitchItems.ninjaShuriken().getDefaultStack();
    }

    @Override
    public ItemStack getStack() {
        return getItemStack();
    }
}
