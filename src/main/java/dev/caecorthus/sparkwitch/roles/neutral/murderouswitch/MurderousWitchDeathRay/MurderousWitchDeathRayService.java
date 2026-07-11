package dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side activation and firing path for Murderous Witch's Death Ray.
 * 杀意魔女“死亡射线”的服务端开启与发射流程，所有命中都以服务端朝向为准。
 */
public final class MurderousWitchDeathRayService {
    private static final DustParticleEffect PARTICLE = new DustParticleEffect(
            new Vector3f(1.0f, 0.02f, 0.02f),
            MurderousWitchDeathRayRules.PARTICLE_SCALE
    );

    private MurderousWitchDeathRayService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        if (!MurderousWitchDeathRayRules.canSelect(context.role())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(context.player());
        if (component.hasActiveDeathRay()) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.death_ray.active");
        }
        if (!component.spendMana(MurderousWitchDeathRayRules.MANA_COST)) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.not_enough_mana");
        }

        component.beginDeathRayWindow(
                MurderousWitchDeathRayRules.WINDOW_TICKS,
                MurderousWitchDeathRayRules.MAX_CHARGES
        );
        return WitchSkillUseResult.successAfterActiveWindow(
                MurderousWitchDeathRayRules.COOLDOWN_TICKS,
                "message.sparkwitch.skill.death_ray.activated"
        );
    }

    public static boolean fire(ServerPlayerEntity caster) {
        ServerWorld world = caster.getServerWorld();
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        if (!MurderousWitchDeathRayRules.canSelect(gameComponent.getRole(caster))
                || !GameFunctions.isPlayerPlayingAndAlive(caster)) {
            return false;
        }

        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(caster);
        if (!component.hasActiveDeathRay()) {
            return false;
        }

        Vec3d start = caster.getEyePos();
        Vec3d direction = MurderousWitchDeathRayRules.normalize(caster.getRotationVec(1.0f));
        if (direction == Vec3d.ZERO) {
            return false;
        }

        world.playSound(
                null,
                caster.getX(),
                caster.getEyeY(),
                caster.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.PLAYERS,
                1.0f,
                1.35f
        );
        double visibleDistance = visibleRayDistance(world, caster, start, direction);
        spawnRayParticles(world, start, direction, visibleDistance);
        for (ServerPlayerEntity target : findTargets(caster, start, direction, visibleDistance)) {
            if (GameFunctions.isPlayerPlayingAndAlive(target)) {
                GameFunctions.killPlayer(target, true, caster, SparkWitchDeathReasons.PIERCED_BY_RAY);
            }
        }
        consumeCharge(component);
        return true;
    }

    public static void tickWindow(ServerPlayerEntity player, WitchPlayerComponent component) {
        if (component.getDeathRayTicks() <= 0 && component.getDeathRayCharges() <= 0) {
            return;
        }
        if (!MurderousWitchDeathRayRules.canSelect(
                GameWorldComponent.KEY.get(player.getWorld()).getRole(player)
        ) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            component.resetDeathRayWindowState();
            component.clearDeferredCooldownState();
            component.sync();
            return;
        }

        if (component.getDeathRayTicks() > 0) {
            component.decrementDeathRayTicks();
        }
        if (component.getDeathRayTicks() <= 0 || component.getDeathRayCharges() <= 0) {
            finishWindow(component);
            return;
        }
        if (component.getDeathRayTicks() % 20 == 0) {
            component.sync();
        }
    }

    private static void consumeCharge(WitchPlayerComponent component) {
        if (!component.hasActiveDeathRay()) {
            return;
        }
        if (component.decrementDeathRayCharges() <= 0) {
            finishWindow(component);
            return;
        }
        component.sync();
    }

    private static void finishWindow(WitchPlayerComponent component) {
        component.finishDeathRayWindowState(MurderousWitchDeathRayRules.COOLDOWN_TICKS);
        component.sync();
    }

    static List<ServerPlayerEntity> findTargets(
            ServerPlayerEntity caster,
            Vec3d start,
            Vec3d direction,
            double visibleDistance
    ) {
        List<ServerPlayerEntity> targets = new ArrayList<>();
        for (ServerPlayerEntity target : caster.getServerWorld().getPlayers()) {
            if (caster.getUuid().equals(target.getUuid())
                    || !GameFunctions.isPlayerPlayingAndAlive(target)
                    || GameFunctions.isPlayerSpectatingOrCreative(target)) {
                continue;
            }
            if (MurderousWitchDeathRayRules.intersectsRay(start, direction, target.getBoundingBox(), visibleDistance)) {
                targets.add(target);
            }
        }
        return targets;
    }

    static double visibleRayDistance(ServerWorld world, ServerPlayerEntity caster, Vec3d start, Vec3d direction) {
        Vec3d end = start.add(direction.multiply(MurderousWitchDeathRayRules.RANGE_BLOCKS));
        BlockHitResult hitResult = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                caster
        ));
        if (hitResult.getType() == HitResult.Type.MISS) {
            return MurderousWitchDeathRayRules.RANGE_BLOCKS;
        }
        return Math.max(0.0, Math.min(MurderousWitchDeathRayRules.RANGE_BLOCKS, start.distanceTo(hitResult.getPos())));
    }

    private static void spawnRayParticles(ServerWorld world, Vec3d start, Vec3d direction, double visibleDistance) {
        double maxDistance = Math.max(0.0, Math.min(MurderousWitchDeathRayRules.RANGE_BLOCKS, visibleDistance));
        double distance = 0.0;
        while (distance <= maxDistance) {
            Vec3d point = start.add(direction.multiply(distance));
            world.spawnParticles(PARTICLE, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            distance += MurderousWitchDeathRayRules.PARTICLE_STEP_BLOCKS;
        }
        if (maxDistance > 0.0) {
            Vec3d point = start.add(direction.multiply(maxDistance));
            world.spawnParticles(PARTICLE, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
