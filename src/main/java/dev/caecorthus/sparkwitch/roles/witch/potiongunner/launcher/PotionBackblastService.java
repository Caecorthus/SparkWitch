package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
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
 * non-forced kill attempt on the nearest player in the horizontal lane straight behind the gunner (D-R2: yaw only,
 * from the eye, clipped at the first block or door, line of sight required) — killed, or stopped by a shield. Any
 * faction is hit, allies included; the gunner never is; Wathe-dead, spectator or creative players, players under
 * SparkTraits Last Escape, and players a SparkFactionAPI affect veto isolates (Wraith/Vendetta) are transparent. The
 * kill runs inside {@code JudgeKillAttribution.runWith} for the gunner, so a Judge sentence covers it. No fallback
 * effects and no +15 reward; the kill bounty follows the normal faction rules. An off-match presentation shot
 * ({@code OffMatchUse}) vents the same flame and sound but touches no player or Seeker device. The geometry lives in
 * {@link PotionBackblastRules}.
 * 仅服务端的炮筒尾焰（所有者规则）：每次真正射出炮弹时，对药炮手正后方水平通道内最近的一名玩家进行一次普通、非强制的
 * 击杀判定（D-R2：只跟随偏航角，从眼部出发，遇到第一个方块或门即截断，需要视线）——直接死亡，或被护盾挡下。不分阵营，
 * 含队友；永不包括药炮手本人；Wathe 判定死亡、旁观或创造模式的玩家、处于 SparkTraits 最后逃脱的玩家，以及被
 * SparkFactionAPI 影响否决隔离（怨灵/复仇者）的玩家视为透明。击杀在为药炮手执行的 {@code JudgeKillAttribution.runWith}
 * 内进行，因此审判官的判决同样覆盖它。没有后续负面效果，也没有 +15 奖励；击杀赏金按正常阵营规则结算。场外仅表现的射击
 * （{@code OffMatchUse}）喷出同样的火焰与声音，但不触及任何玩家或搜寻者设备。几何判定位于 {@link PotionBackblastRules}。
 */
public final class PotionBackblastService {
    private static final double PARTICLE_STEP = 0.25;

    private PotionBackblastService() {
    }

    /**
     * Call once per launched shell, after the launch succeeded, with the shot's yaw (the pitch never tilts the lane);
     * {@code presentation} marks an off-match shot, which stops after the flame and sound.
     * 每颗成功射出的炮弹调用一次，传入该次发射的偏航角（俯仰角从不使通道倾斜）；{@code presentation} 表示场外射击，
     * 只喷出火焰与声音。
     */
    public static void fire(ServerPlayerEntity gunner, float fireYaw, boolean presentation) {
        ServerWorld world = gunner.getServerWorld();
        Vec3d origin = gunner.getEyePos();
        Vec3d backwards = PotionBackblastRules.backwards(fireYaw);
        double length = PotionBackblastRules.laneLength(blockDistance(world, gunner, origin, backwards));
        present(world, gunner, origin, backwards, length);
        if (presentation) {
            return;
        }
        Optional<PotionBackblastRules.Hit<ServerPlayerEntity>> victim =
                PotionBackblastRules.nearestHit(lane(world, gunner, origin, backwards, length));
        // Seeker seam, nearest wins: the victim's distance is measured on its real box (the measure device rays use),
        // and only a breakable device strictly nearer absorbs the backblast and breaks; nobody behind it is hit; a tie
        // goes to the player. / 搜寻者接缝，最近者命中：受害者距离按其真实碰撞箱量取（与设备射线同一量法），只有严格
        // 更近的可破坏设备才会吸收尾焰并被打坏，其后方的人不受影响；距离相同时命中玩家。
        double reach = victim.map(PotionBackblastRules.Hit::distance).orElse(length);
        // Magician seam: a puppet strictly nearer than the victim and than any device takes the backblast instead.
        // 魔术师接缝：严格近于受害者且近于任何设备的皮套改为承受尾焰。
        if (MagicianPuppetHits.onPotionBackblast(gunner, origin, backwards, length, reach)) {
            return;
        }
        if (SeekerDeviceHits.onPotionBackblast(gunner, origin, backwards, reach)) {
            return;
        }
        victim.ifPresent(hit -> JudgeKillAttribution.runWith(world, gunner.getUuid(),
                () -> GameFunctions.killPlayer(hit.target(), true, gunner, SparkWitchDeathReasons.POTION_BACKBLAST)));
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
                    || !GameFunctions.isPlayerAliveAndSurvival(target)
                    || SparkTraitsKillerBridge.isLastEscapeActive(target)) {
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
