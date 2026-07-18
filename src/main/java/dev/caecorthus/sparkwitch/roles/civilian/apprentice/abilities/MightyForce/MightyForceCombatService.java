package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(attacker.getWorld());
        boolean ownsMightyForce = gameComponent.getRole(attacker) == SparkWitchRoles.apprenticeWitch()
                && MightyForceAbility.ID.equals(component.getActiveSkillId());
        if (!ownsMightyForce
                || !component.hasActiveMightyForce()
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

        ServerWorld world = attacker.getServerWorld();
        Vec3d impulse = knockbackImpulse(attacker, target);
        applyKnockback(target, impulse);

        // Wathe spawns the corpse synchronously; remember old bodies so a cancelled death cannot launch one.
        // Wathe 会同步生成尸体；先记录旧尸体，避免死亡被拦截时误把旧尸体击飞。
        Set<UUID> existingBodyUuids = new HashSet<>();
        for (PlayerBodyEntity body : world.getEntitiesByType(
                WatheEntities.PLAYER_BODY,
                body -> body.getPlayerUuid().equals(target.getUuid())
        )) {
            existingBodyUuids.add(body.getUuid());
        }

        GameRecordManager.recordSkillUse(attacker, MightyForceAbility.ID, target, null);
        GameFunctions.killPlayer(target, true, attacker, SparkWitchDeathReasons.MIGHTY_FORCE);

        Entity impactRecipient = world.getEntitiesByType(
                WatheEntities.PLAYER_BODY,
                body -> body.getPlayerUuid().equals(target.getUuid())
                        && body.getDeathGameTime() == (int) world.getTime()
                        && SparkWitchDeathReasons.MIGHTY_FORCE.equals(body.getDeathReason())
                        && !existingBodyUuids.contains(body.getUuid())
        ).stream().findFirst().orElse(null);
        if (!GameFunctions.isPlayerPlayingAndAlive(target) && impactRecipient != null) {
            applyKnockback(impactRecipient, impulse);
        }

        world.playSound(
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

    private static Vec3d knockbackImpulse(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        Vec3d direction = target.getPos().subtract(attacker.getPos());
        Vec3d horizontal = new Vec3d(direction.x, 0.0, direction.z);
        if (horizontal.lengthSquared() < 1.0E-6) {
            horizontal = attacker.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0);
        }
        if (horizontal.lengthSquared() < 1.0E-6) {
            return Vec3d.ZERO;
        }
        return horizontal.normalize()
                .multiply(MightyForceAbility.KNOCKBACK_STRENGTH)
                .add(0.0, 0.35, 0.0);
    }

    private static void applyKnockback(Entity target, Vec3d impulse) {
        if (impulse.lengthSquared() < 1.0E-6) {
            return;
        }
        target.addVelocity(impulse.x, impulse.y, impulse.z);
        target.velocityModified = true;
    }
}
