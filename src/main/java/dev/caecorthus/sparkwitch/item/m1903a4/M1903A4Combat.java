package dev.caecorthus.sparkwitch.item.m1903a4;

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
 * M1903A4 hitscan: a single ray up to {@link #RANGE} blocks. It picks targets the way the knife's crosshair does (any
 * living, playing player in line of sight, invisibility ignored) and kills the nearest one with a Wathe gunshot death,
 * exactly like a revolver shot, while a trail of white smoke marks the path to the victim or to the block it hit.
 * M1903A4 命中判定：一条最远 {@link #RANGE} 格的射线。目标选取与刀的准星一致（视线内任何存活且参与对局的玩家，无视隐身），
 * 击中最近的一名，并以 Wathe 枪击死因结算，与左轮开火完全相同；白色烟雾从枪口一直画到受害者或挡住的方块。
 */
public final class M1903A4Combat {
    /** Gun range and crosshair range, both 50 blocks. / 射程与准星判定距离同为 50 格。 */
    public static final double RANGE = 50.0D;
    private static final double SMOKE_STEP = 1.0D;

    private M1903A4Combat() {
    }

    /** Nearest player on the ray, or {@code null}. Client and server share this so the crosshair matches the shot. */
    public static EntityHitResult raycast(PlayerEntity shooter) {
        Vec3d eye = shooter.getCameraPosVec(1.0F);
        Vec3d look = shooter.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(RANGE));
        Box box = shooter.getBoundingBox().stretch(look.multiply(RANGE)).expand(1.0D);
        return ProjectileUtil.raycast(
                shooter,
                eye,
                end,
                box,
                entity -> sparkwitch$isTarget(shooter, entity),
                RANGE * RANGE
        );
    }

    /** Whether the crosshair should show the aimed state: charging and a target on the ray. / 准星是否显示瞄准状态。 */
    public static boolean aimsAtTarget(PlayerEntity shooter) {
        return shooter.isUsingItem() && raycast(shooter) != null;
    }

    /** Server side of one shot: smoke trail, then the gunshot kill and its kill sound. / 一次开火的服务端结算。 */
    public static void fire(ServerPlayerEntity shooter) {
        ServerWorld world = shooter.getServerWorld();
        Vec3d eye = shooter.getCameraPosVec(1.0F);
        Vec3d look = shooter.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(RANGE));

        EntityHitResult hit = raycast(shooter);
        Vec3d stop = end;
        if (hit != null) {
            stop = hit.getPos();
        } else {
            BlockHitResult block = world.raycast(new RaycastContext(
                    eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
            if (block.getType() != HitResult.Type.MISS) {
                stop = block.getPos();
            }
        }
        spawnSmoke(world, eye, stop, look);

        if (hit != null && hit.getEntity() instanceof ServerPlayerEntity victim) {
            GameFunctions.killPlayer(victim, true, shooter, GameConstants.DeathReasons.GUN);
            world.playSound(null, shooter.getBlockPos(), dev.caecorthus.sparkwitch.SparkWitchSounds.M1903A4_KILL,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
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
