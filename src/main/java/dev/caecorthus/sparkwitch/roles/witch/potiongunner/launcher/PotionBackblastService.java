package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-only launcher backblast (owner rule): every shot that actually launched a shell makes one ordinary,
 * non-forced kill attempt on the nearest player in the lane straight behind the launcher — killed, or stopped by a
 * shield. Any faction is hit, allies included; the gunner never is; SparkFactionAPI affect vetoes (Wraith/Vendetta
 * isolation) make a player transparent. No fallback effects and no +15 reward; the kill bounty follows the normal
 * faction rules. The geometry lives in {@link PotionBackblastRules}.
 * 仅服务端的炮筒尾焰（所有者规则）：每次真正射出炮弹时，对炮筒正后方通道内最近的一名玩家进行一次普通、非强制的击杀判定
 * ——直接死亡，或被护盾挡下。不分阵营，含队友；永不包括药炮手本人；被 SparkFactionAPI 影响否决（怨灵/复仇者隔离）的
 * 玩家视为透明。没有后续负面效果，也没有 +15 奖励；击杀赏金按正常阵营规则结算。几何判定位于 {@link PotionBackblastRules}。
 */
public final class PotionBackblastService {
    private static final double PARTICLE_STEP = 0.25;

    private PotionBackblastService() {
    }

    /** Call once per launched shell, after the launch succeeded. / 每颗成功射出的炮弹调用一次。 */
    public static void fire(ServerPlayerEntity gunner, float fireYaw, float firePitch) {
        ServerWorld world = gunner.getServerWorld();
        Vec3d origin = gunner.getEyePos();
        Vec3d backwards = PotionBackblastRules.backwards(fireYaw, firePitch);
        double length = PotionBackblastRules.laneLength(blockDistance(world, gunner, origin, backwards));
        // Seeker hook (coordinator, at integration): a nearer Seeker device on the lane absorbs the backblast here —
        // break it through SeekerDeviceHits and shorten `length` to it, so nobody behind it is hit.
        // 搜寻者接入点（协调者在整合时补上）：通道上更近的搜寻者设备在此吸收尾焰——经 SeekerDeviceHits 打坏它并把
        // `length` 截短到该处，使其后方的人不受影响。
        present(world, gunner, origin, backwards, length);
        Optional<ServerPlayerEntity> victim = PotionBackblastRules.nearest(lane(world, gunner, origin, backwards, length));
        victim.ifPresent(target -> JudgeKillAttribution.runWith(world, gunner.getUuid(),
                () -> GameFunctions.killPlayer(target, true, gunner, SparkWitchDeathReasons.POTION_BACKBLAST)));
    }

    /** Distance to the first COLLIDER block (closed doors included) on the full lane, or -1. / 完整通道上第一个方块的距离，否则 -1。 */
    private static double blockDistance(ServerWorld world, ServerPlayerEntity gunner, Vec3d origin, Vec3d backwards) {
        Vec3d end = origin.add(backwards.multiply(PotionGunnerRules.BACKBLAST_LENGTH));
        HitResult hit = world.raycast(new RaycastContext(origin, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, gunner));
        return hit.getType() == HitResult.Type.MISS ? -1.0 : origin.distanceTo(hit.getPos());
    }

    private static List<PotionBackblastRules.Hit<ServerPlayerEntity>> lane(ServerWorld world, ServerPlayerEntity gunner,
                                                                         Vec3d origin, Vec3d backwards,
                                                                         double length) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        List<PotionBackblastRules.Hit<ServerPlayerEntity>> hits = new ArrayList<>();
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target == gunner
                    || !GameFunctions.isPlayerPlayingAndAlive(target)
                    || !GameFunctions.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            Box box = target.getBoundingBox();
            double distance = PotionBackblastRules.hitDistance(origin, backwards, length, box);
            if (distance < 0.0
                    || !SparkFactionApi.canAffectPlayer(gunner, target, SparkWitchDeathReasons.POTION_BACKBLAST, game)) {
                continue;
            }
            Vec3d sight = PotionBackblastRules.sightPoint(origin, backwards, distance, box);
            hits.add(new PotionBackblastRules.Hit<>(target, distance,
                    SeekerDamageRules.segmentClear(world, origin, sight, gunner)));
        }
        return hits;
    }

    /** Flame and smoke along the lane plus a whoosh for everyone nearby, hit or not. / 沿通道的火焰与烟雾，以及附近所有人可闻的呼啸声，无论是否命中。 */
    private static void present(ServerWorld world, ServerPlayerEntity gunner, Vec3d origin, Vec3d backwards,
                                double length) {
        world.playSound(null, gunner.getX(), gunner.getEyeY(), gunner.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.PLAYERS, 1.5F, 0.7F);
        for (double travelled = PARTICLE_STEP; travelled <= length; travelled += PARTICLE_STEP) {
            Vec3d point = origin.add(backwards.multiply(travelled));
            world.spawnParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 2, 0.08, 0.08, 0.08, 0.02);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y, point.z, 1, 0.12, 0.12, 0.12, 0.01);
        }
    }
}
