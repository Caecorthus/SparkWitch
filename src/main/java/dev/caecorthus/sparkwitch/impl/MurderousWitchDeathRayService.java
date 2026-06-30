package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
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

        spawnRayParticles(world, start, direction);
        for (ServerPlayerEntity target : findTargets(caster, start, direction)) {
            if (GameFunctions.isPlayerPlayingAndAlive(target)) {
                GameFunctions.killPlayer(target, true, caster, SparkWitchDeathReasons.PIERCED_BY_RAY);
            }
        }
        component.consumeDeathRayCharge(MurderousWitchDeathRayRules.COOLDOWN_TICKS);
        return true;
    }

    static List<ServerPlayerEntity> findTargets(ServerPlayerEntity caster, Vec3d start, Vec3d direction) {
        List<ServerPlayerEntity> targets = new ArrayList<>();
        for (ServerPlayerEntity target : caster.getServerWorld().getPlayers()) {
            if (caster.getUuid().equals(target.getUuid())
                    || !GameFunctions.isPlayerPlayingAndAlive(target)
                    || GameFunctions.isPlayerSpectatingOrCreative(target)) {
                continue;
            }
            if (MurderousWitchDeathRayRules.intersectsRay(start, direction, target.getBoundingBox())) {
                targets.add(target);
            }
        }
        return targets;
    }

    private static void spawnRayParticles(ServerWorld world, Vec3d start, Vec3d direction) {
        double distance = 0.0;
        while (distance <= MurderousWitchDeathRayRules.RANGE_BLOCKS) {
            Vec3d point = start.add(direction.multiply(distance));
            world.spawnParticles(PARTICLE, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            distance += MurderousWitchDeathRayRules.PARTICLE_STEP_BLOCKS;
        }
    }
}
