package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class FirePokerCombatService {
    private static boolean registered;

    private FirePokerCombatService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                tryHandleAttack(player, world, hand, entity));
    }

    public static ActionResult tryHandleAttack(Entity attacker, World world, Hand hand, Entity target) {
        if (world.isClient
                || !(attacker instanceof ServerPlayerEntity serverAttacker)
                || !serverAttacker.getStackInHand(hand).isOf(SparkWitchItems.firePoker())
                || !(target instanceof ServerPlayerEntity serverTarget)) {
            return ActionResult.PASS;
        }

        if (serverAttacker.getItemCooldownManager().isCoolingDown(SparkWitchItems.firePoker())
                || !canStrike(serverAttacker, serverTarget)) {
            return ActionResult.SUCCESS;
        }

        strike(serverAttacker, serverTarget);
        return ActionResult.SUCCESS;
    }

    private static boolean canStrike(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        if (!GameFunctions.isPlayerPlayingAndAlive(attacker)
                || attacker.getUuid().equals(target.getUuid())) {
            return false;
        }
        return GameFunctions.isPlayerPlayingAndAlive(target)
                && GameFunctions.isPlayerAliveAndSurvival(target);
    }

    private static void strike(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        knockback(attacker, target);
        applyMagicEffects(attacker, target);
        attacker.getItemCooldownManager().set(SparkWitchItems.firePoker(), FirePokerRules.COOLDOWN_TICKS);
        GameRecordManager.recordItemUse(
                attacker,
                Registries.ITEM.getId(SparkWitchItems.firePoker()),
                target,
                null
        );
        attacker.getServerWorld().playSound(
                null,
                target.getX(),
                target.getEyeY(),
                target.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                SoundCategory.PLAYERS,
                1.0f,
                0.8f
        );
    }

    private static void applyMagicEffects(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(attacker);
        if (!FirePokerRules.shouldSpendMana(component.hasManaSystem(), component.getMana())
                || !component.spendMana(FirePokerRules.MANA_COST)) {
            return;
        }

        // The debuff branch is all-or-nothing with the mana spend.
        // 魔力分支与扣魔力绑定，扣费成功后才施加所有增益/减益。
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS,
                FirePokerRules.EFFECT_DURATION_TICKS,
                0,
                false,
                false,
                true
        ));
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                FirePokerRules.EFFECT_DURATION_TICKS,
                FirePokerRules.SLOWNESS_AMPLIFIER,
                false,
                false,
                true
        ));
        attacker.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                FirePokerRules.EFFECT_DURATION_TICKS,
                FirePokerRules.SPEED_AMPLIFIER,
                false,
                false,
                true
        ));
    }

    private static void knockback(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        Vec3d direction = target.getPos().subtract(attacker.getPos());
        Vec3d horizontal = new Vec3d(direction.x, 0.0, direction.z);
        if (horizontal.lengthSquared() < 1.0E-6) {
            horizontal = attacker.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0);
        }
        if (horizontal.lengthSquared() < 1.0E-6) {
            return;
        }
        Vec3d velocity = horizontal.normalize().multiply(FirePokerRules.KNOCKBACK_STRENGTH);
        target.addVelocity(velocity.x, FirePokerRules.KNOCKBACK_UPWARD_VELOCITY, velocity.z);
        target.velocityModified = true;
    }
}
