package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastGeometry;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * A Mini Wither skull's burst: no damage, no knockback and no block change (Wathe only cancels health loss, so a vanilla
 * explosion would still shove players off the train). Every live participant in the 2x2x2 cube with line of sight to the
 * centre gets Slowness II for 2 s when they are not on the killer team; a Conscience owner's burst gives everyone Speed I
 * instead (owner Q9).
 * 小凋灵头颅的爆炸：无伤害、无击退、不改变方块（Wathe 只取消扣血，原版爆炸仍会把人推下火车）。2x2x2 立方体内与爆心有视线的
 * 存活参与者，不属于杀手队伍的获得 2 秒缓慢 II；主人善良时改为所有人获得速度 I（所有者 Q9）。
 */
public final class MiniWitherBurstService {
    private static final float EXPLODE_VOLUME = 0.6F;
    private static final float EXPLODE_PITCH = 1.5F;

    private MiniWitherBurstService() {
    }

    static void burst(MiniWitherSkullEntity skull, Vec3d center) {
        if (!(skull.getWorld() instanceof ServerWorld world) || !MiniWitherSkullEntity.isRoundActive(world)) {
            return;
        }
        boolean blessed = skull.mode() == MiniWitherRules.Mode.CONSCIENCE;
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, EXPLODE_VOLUME, EXPLODE_PITCH);
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(blessed ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SOUL, center.x, center.y, center.z,
                8, 0.5, 0.5, 0.5, 0.02);

        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        UUID ownerUuid = skull.ownerUuid();
        ServerPlayerEntity owner = skull.getOwner() instanceof ServerPlayerEntity player ? player : null;
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (!MiniWitherTargeting.isLiveParticipant(target, game)
                    || !MiniWitherRules.inBurst(center, target.getBoundingBox())
                    || !hasLineOfSight(world, center, target)) {
                continue;
            }
            boolean self = target.getUuid().equals(ownerUuid);
            if (!MiniWitherRules.burstAffects(skull.mode(), self,
                    MiniWitherTargeting.isKillerTeam(target, game))) {
                continue;
            }
            // An offline owner vetoes nobody; the owner's own Speed needs no permission.
            // 离线主人不否决任何人；主人自己的速度无需许可。
            if (owner != null && !self && !MiniWitherTargeting.mayAffect(owner, target, game)) {
                continue;
            }
            target.addStatusEffect(blessed
                    ? new StatusEffectInstance(StatusEffects.SPEED, MiniWitherRules.BURST_EFFECT_TICKS,
                    MiniWitherRules.SPEED_AMPLIFIER, false, true, true)
                    : new StatusEffectInstance(StatusEffects.SLOWNESS, MiniWitherRules.BURST_EFFECT_TICKS,
                    MiniWitherRules.SLOWNESS_AMPLIFIER, false, true, true), owner);
        }
    }

    private static boolean hasLineOfSight(ServerWorld world, Vec3d center, ServerPlayerEntity target) {
        for (Vec3d point : PotionBlastGeometry.sightPoints(target.getPos(), target.getBoundingBox(),
                target.getEyeY())) {
            if (SeekerDamageRules.segmentClear(world, center, point, null)) {
                return true;
            }
        }
        return false;
    }
}
