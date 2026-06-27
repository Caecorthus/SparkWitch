package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class MightyForceCombatService {
    private static boolean registered;

    private MightyForceCombatService() {
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
                || !serverAttacker.getStackInHand(hand).isEmpty()
                || !canStrike(serverAttacker, target)) {
            return ActionResult.PASS;
        }

        strike(serverAttacker, (ServerPlayerEntity) target);
        return ActionResult.SUCCESS;
    }

    private static boolean canStrike(ServerPlayerEntity attacker, Entity target) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(attacker);
        if (!component.hasActiveMightyForce()
                || !GameFunctions.isPlayerPlayingAndAlive(attacker)
                || !(target instanceof ServerPlayerEntity serverTarget)
                || attacker.getUuid().equals(serverTarget.getUuid())) {
            return false;
        }
        return GameFunctions.isPlayerPlayingAndAlive(serverTarget)
                && GameFunctions.isPlayerAliveAndSurvival(serverTarget);
    }

    private static void strike(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(attacker);
        component.clearMightyForceWindow();

        knockback(attacker, target);
        GameRecordManager.recordSkillUse(attacker, ApprenticeWitchSkillRules.MIGHTY_FORCE_ID, target, null);
        GameFunctions.killPlayer(target, true, attacker, SparkWitchDeathReasons.MIGHTY_FORCE);
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

    private static void knockback(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        Vec3d direction = target.getPos().subtract(attacker.getPos());
        Vec3d horizontal = new Vec3d(direction.x, 0.0, direction.z);
        if (horizontal.lengthSquared() < 1.0E-6) {
            horizontal = attacker.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0);
        }
        if (horizontal.lengthSquared() < 1.0E-6) {
            return;
        }
        Vec3d velocity = horizontal.normalize().multiply(ApprenticeWitchSkillRules.MIGHTY_FORCE_KNOCKBACK_STRENGTH);
        target.addVelocity(velocity.x, 0.35, velocity.z);
        target.velocityModified = true;
    }
}
