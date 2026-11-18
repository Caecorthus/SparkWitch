package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * M1903A4 hitscan: one ray up to {@link #RANGE} blocks, stopped by the first solid block. Targets are picked the way the
 * knife's crosshair does (any living, playing player in line of sight, invisibility ignored) and the nearest one dies to
 * a Wathe gunshot death, exactly like a revolver shot, while a trail of white smoke marks the path to the victim or to
 * the block that stopped the round.
 * M1903A4 命中判定：一条最远 {@link #RANGE} 格的射线，遇到第一个实体方块即停止。目标选取与刀的准星一致（视线内任何存活且
 * 参与对局的玩家，无视隐身），击中最近的一名，并以 Wathe 枪击死因结算，与左轮开火相同；白色烟雾从枪口一直画到受害者或
 * 挡住子弹的方块。
 */
public final class M1903A4Combat {
    /** Gun range and crosshair range, both 50 blocks. / 射程与准星判定距离同为 50 格。 */
    public static final double RANGE = 50.0D;
    private static final double SMOKE_STEP = 1.0D;

    private M1903A4Combat() {
    }

    /**
     * One resolved shot: where the round stops and, if a player was in front of that point, whom it hit. Client and
     * server share this so the crosshair can never promise a hit through a wall.
     * 一次结算好的射击：子弹停在哪里，以及在此之前是否命中玩家。客户端与服务端共用，准星因此不会在墙后亮起。
     */
    public record Shot(Vec3d stop, EntityHitResult hit) {
    }

    /** Resolves the round: block ray first, the entity ray then only searches up to that block. / 先算方块，再只算到方块为止的实体。 */
    public static Shot resolveShot(PlayerEntity shooter) {
        Vec3d eye = shooter.getCameraPosVec(1.0F);
        Vec3d look = shooter.getRotationVec(1.0F);
        Vec3d maxEnd = eye.add(look.multiply(RANGE));
        BlockHitResult block = shooter.getWorld().raycast(new RaycastContext(
                eye, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
        Vec3d stop = block.getType() == HitResult.Type.MISS ? maxEnd : block.getPos();

        Vec3d searched = stop.subtract(eye);
        Box box = shooter.getBoundingBox().stretch(searched).expand(1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(
                shooter,
                eye,
                stop,
                box,
                entity -> sparkwitch$isTarget(shooter, entity),
                RANGE * RANGE
        );
        if (hit != null) {
            stop = hit.getPos();
        }
        return new Shot(stop, hit);
    }

    /** Whether the crosshair should show the aimed state: charging and a target before any wall. / 蓄力且墙前有目标时显示瞄准准星。 */
    public static boolean aimsAtTarget(PlayerEntity shooter) {
        return shooter.isUsingItem() && resolveShot(shooter).hit() != null;
    }

    /** Server side of one shot: smoke trail, then the gunshot kill and its kill sound. / 一次开火的服务端结算。 */
    public static void fire(ServerPlayerEntity shooter) {
        ServerWorld world = shooter.getServerWorld();
        Vec3d eye = shooter.getCameraPosVec(1.0F);
        Vec3d look = shooter.getRotationVec(1.0F);
        Shot shot = resolveShot(shooter);
        spawnSmoke(world, eye, shot.stop(), look);

        if (shot.hit() != null && shot.hit().getEntity() instanceof ServerPlayerEntity victim) {
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            boolean wasDead = game.isPlayerDead(victim.getUuid());
            GameFunctions.killPlayer(victim, true, shooter, GameConstants.DeathReasons.GUN);
            // A shield, the Fiend's feign death or any other protection cancels the kill inside killPlayer; the kill
            // sound belongs to an actual death only.
            // 护盾、魔人假死或其他保护会在 killPlayer 内部取消击杀；击杀音效只属于真正的死亡。
            boolean killed = !wasDead && game.isPlayerDead(victim.getUuid());
            dev.doctor4t.wathe.record.GameRecordManager.recordItemUse(
                    shooter, dev.caecorthus.sparkwitch.SparkWitchItems.M1903A4_ID, victim, new net.minecraft.nbt.NbtCompound());
            if (killed) {
                world.playSound(null, shooter.getBlockPos(), dev.caecorthus.sparkwitch.SparkWitchSounds.M1903A4_KILL,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    private static boolean sparkwitch$isTarget(PlayerEntity shooter, Entity entity) {
        return entity instanceof PlayerEntity target
                && target != shooter
                && GameFunctions.isPlayerAliveAndSurvival(target);
    }

    private static void spawnSmoke(ServerWorld world, Vec3d from, Vec3d to, Vec3d look) {
        double length = from.distanceTo(to);
        if (length < 0.1D) {
            return;
        }
        for (double travelled = SMOKE_STEP; travelled <= length; travelled += SMOKE_STEP) {
            Vec3d point = from.add(look.normalize().multiply(travelled));
            if (point.squaredDistanceTo(to) > length * length) {
                break;
            }
            world.spawnParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        world.spawnParticles(ParticleTypes.CLOUD, to.x, to.y, to.z, 2, 0.02D, 0.02D, 0.02D, 0.0D);
    }
}
