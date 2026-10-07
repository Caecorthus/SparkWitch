/*
 * Derived from StarRailExpress ThrowingKnifeEntity at commit
 * 220d03ede335fc7971fcffbc302bc68bb91b0209 (GPL-3.0-only).
 * SparkWitch adaptations are AGPL-3.0-only; see THIRD_PARTY_NOTICES.md.
 */
package dev.caecorthus.sparkwitch.entity;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
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
        // Seeker seam (server, in flight): a Seeker device in this tick's path breaks and stops the shuriken, unless a
        // Magician puppet lies before it (vanilla collision then takes the puppet or a nearer player).
        // 搜寻者接缝（服务端、飞行中）：本刻路径上的搜寻者设备被打坏，手里剑随之停下；若魔术师皮套位于设备之前则跳过
        // （由原版碰撞处理该皮套或更近的玩家）。
        if (!getWorld().isClient() && !inGround
                && !MagicianPuppetHits.puppetBeforeDevice(this, getOwner(), getPos(), getPos().add(getVelocity()))
                && SeekerDeviceHits.onShurikenSweep(this, getOwner(), getPos(), getPos().add(getVelocity()))) {
            discard();
            return;
        }
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

    /**
     * Magician seam: a puppet its thrower may not end (the Magician's own) is transparent; any other entity keeps the
     * vanilla filter. / 魔术师接缝：投掷者不能结束的皮套（魔术师自己的）对手里剑透明；其他实体保持原版过滤。
     */
    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && MagicianPuppetHits.projectileMayHit(getOwner(), entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity owner = getOwner();
        // Magician seam: a puppet takes the hit as its copied player would, then the shuriken stops.
        // 魔术师接缝：皮套与其复制的玩家一样承受命中，随后手里剑停下。
        if (MagicianPuppetHits.onShurikenHit(this, owner, entityHitResult, getItemStack())) {
            discard();
            return;
        }
        Entity hitEntity = entityHitResult.getEntity();
        if (!(owner instanceof ServerPlayerEntity thrower)
                || !(hitEntity instanceof ServerPlayerEntity victim)
                || victim.getUuid().equals(thrower.getUuid())
                || !VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(thrower, victim)
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
