package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.border.WorldBorder;

import java.util.List;

/**
 * Server side of the Athlete's dash ({@code sparkwitch:use_athlete_dash}): gates the request
 * ({@link AthleteDashRules}), searches the destination ({@link AthleteDashPath}), then teleports with the client's own
 * rotation kept and no leftover momentum, plays the dash sound, records the replay and restarts the cooldown in
 * {@link AthleteComponent}. A refused or blocked dash changes nothing and costs nothing.
 * 运动员冲刺（{@code sparkwitch:use_athlete_dash}）的服务端部分：先判定请求（{@link AthleteDashRules}），再搜索落点
 * （{@link AthleteDashPath}），然后传送（保留客户端自身视角，不留余速），播放冲刺音效，记录回放，并在
 * {@link AthleteComponent} 中重新开始冷却。被拒绝或受阻的冲刺不改变任何东西，也不消耗冷却。
 */
public final class AthleteDashService {
    private AthleteDashService() {
    }

    public static void tryUse(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        AthleteComponent state = AthleteComponent.KEY.get(player);
        AthleteDashRules.DashVerdict verdict = AthleteDashRules.verdict(
                AthleteRules.isAthlete(game.getRole(player)),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator(),
                isHeld(player),
                player.hasVehicle() || player.isSleeping(),
                SparkTraitsKillerBridge.isRoleSkillBlocked(player),
                state.dashCooldownRemaining(now) <= 0,
                GrandWitchFearService.isPlayerFeared(player));
        switch (verdict) {
            case USE -> dash(player, world, state, now);
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case REFUSE -> {
            }
        }
    }

    private static void dash(ServerPlayerEntity player, ServerWorld world, AthleteComponent state, long now) {
        Vec3d look = player.getRotationVector();
        Vec3d direction = new Vec3d(look.x, 0.0D, look.z);
        if (direction.lengthSquared() < 1.0E-6D) {
            return;
        }
        Box start = player.getBoundingBox();
        Vec3d destination = AthleteDashPath.find(space(world, player, start), start, player.getPos(),
                direction.normalize(), Math.max(0.0F, player.getStepHeight()));
        if (destination == null) {
            return;
        }
        // Same-world position only. The rotation flags are relative and the absolute yaw/pitch passed equal the
        // server's own, so the client receives a zero rotation delta and its camera never snaps; the absolute position
        // makes the client zero its velocity, so nothing slides the player past a ledge or into a door afterwards
        // (owner 2026-11-03: no leftover momentum).
        // 只改同一世界内的位置。视角标志为相对，传入的绝对朝向等于服务端自身的值，客户端收到的视角增量为零，镜头不会跳动；
        // 绝对位置会让客户端把速度清零，之后不会被余速推下边缘或推进门里（所有者 2026-11-03：不保留余速）。
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0F;
        player.networkHandler.requestTeleport(destination.x, destination.y, destination.z,
                player.getYaw(), player.getPitch(), PositionFlag.ROT);
        world.playSound(null, destination.x, destination.y, destination.z,
                SoundEvents.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 0.8F, 1.15F);
        AthleteReplay.recordDash(player);
        state.setDashReadyTick(now + AthleteRules.COOLDOWN_TICKS);
    }

    /**
     * Held or locked by another mechanic (the Riftwalker session's private list, deliberately copied like the Ninja
     * hook's, plus the Hunter root): swallowed by a Taotie, Last Stand, Last Escape, Kidnapper control, Control Expert
     * stun, Seeker session, borrowed camera or rooted by a Hunter trap. A teleport would carry the body out of its hold.
     * 被其他机制持有或锁定（隙行者会话的私有列表，与忍者钩爪一样有意复制，另加猎人定身）：饕餮吞下、背水一战、脱险、
     * 绑架者控制、控场专家眩晕、搜寻者会话、镜头被占用或被猎人陷阱定身。传送会把身体带出这些控制。
     */
    static boolean isHeld(ServerPlayerEntity player) {
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(player);
        HunterPlayerComponent hunter = HunterPlayerComponent.KEY.getNullable(player);
        return NoellesTaotieSeekerBridge.isSwallowed(player)
                || SparkTraitsSeekerBridge.isLastStandPending(player)
                || SparkTraitsKillerBridge.isLastEscapeActive(player)
                || kidnap != null && kidnap.isControlled()
                || ControlExpertStun.isStunned(player)
                || SeekerRemoteSessionService.isLocked(player)
                || player.getCameraEntity() != player
                || hunter != null && hunter.isRooted();
    }

    /**
     * Vanilla collision with the player's own shape context (blocks, hard entities such as Wathe's solid players, the
     * world border), so the dash is blocked by what blocks walking. Like walking, an entity whose box already overlaps
     * the start box (two players spawned on one spot) never blocks.
     * 使用玩家自身形状上下文的原版碰撞（方块、Wathe 中互相实体化的玩家等硬碰撞实体、世界边界），因此冲刺受阻的条件与
     * 走路相同。与走路一样，碰撞箱已与起点碰撞箱重叠的实体（两名玩家生成在同一点）不会阻挡。
     */
    private static AthleteDashPath.Space space(ServerWorld world, ServerPlayerEntity player, Box start) {
        return new AthleteDashPath.Space() {
            @Override
            public boolean isFree(Box box) {
                if (world.isSpaceEmpty(player, box)) {
                    return true;
                }
                if (!world.isBlockSpaceEmpty(player, box)) {
                    return false;
                }
                WorldBorder border = world.getWorldBorder();
                if (border.canCollide(player, box) && VoxelShapes.matchesAnywhere(
                        border.asVoxelShape(), VoxelShapes.cuboid(box), BooleanBiFunction.AND)) {
                    return false;
                }
                for (VoxelShape shape : world.getEntityCollisions(player, box)) {
                    if (!shape.getBoundingBox().intersects(start)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public double fallDistance(Box box, double max) {
                return -Entity.adjustMovementForCollisions(player, new Vec3d(0.0D, -max, 0.0D), box, world, List.of()).y;
            }
        };
    }
}
