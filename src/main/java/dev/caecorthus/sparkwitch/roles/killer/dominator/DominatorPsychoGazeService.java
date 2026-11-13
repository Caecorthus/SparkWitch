package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.network.packet.s2c.play.LookAtS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * While the Dominator is in psycho mode, every other player within one block (teammates included, as the letter says)
 * is forced to look up, until they leave the range, the psycho ends or the Dominator dies. Spectators, dead players
 * and Wraiths are exempt. Server authority: the server reads each victim's pitch from its own movement packets and
 * re-forces only when that pitch has dropped below {@link #LOOKING_UP_PITCH}, so a victim already looking up gets no
 * packet and can still turn sideways freely. Each victim keeps its yaw: the look-at point lies far up and a little
 * ahead along the victim's current facing, because the client's vanilla {@code Entity#lookAt} recomputes yaw from the
 * horizontal offset, and a point straight above the eyes made that an atan2 of client/server position noise (the
 * view snapped sideways every tick).
 * 支配者处于疯魔时，1 格内的其他玩家（如信件所述，队友也包括在内）被强制抬头，直到离开 1 格、疯魔结束或支配者死亡。观战、
 * 死亡与冤魂玩家豁免。服务端权威：服务端从受害者自己的移动包读取其俯仰角，只有当俯仰角低于 {@link #LOOKING_UP_PITCH} 时才重新
 * 强制，因此已经抬头的受害者不会收到数据包，仍可自由左右转动。每个受害者保留自己的偏航角：注视点位于高处并沿其当前朝向略微
 * 靠前，因为客户端原版 {@code Entity#lookAt} 会用水平偏移重新计算偏航角，而正上方的点会让它变成对客户端/服务端位置误差取
 * atan2（视角每 tick 都会左右跳动）。
 */
public final class DominatorPsychoGazeService {
    private static final double GAZE_RADIUS_SQUARED = 1.0D;
    /** The forced pitch: one degree short of straight up, so the facing still decides yaw. / 强制俯仰角：差 1 度即正上方，朝向仍决定偏航。 */
    static final float FORCED_PITCH = -89.0F;
    /**
     * A victim whose server-side pitch is at or above this (more negative is higher) already looks up and is left
     * alone. / 服务端俯仰角达到或高于此值（越负越高）的受害者已在抬头，不再处理。
     */
    static final float LOOKING_UP_PITCH = -85.0F;
    /**
     * Horizontal lead of the look-at point along the facing; long enough that a few tenths of a block of client/server
     * position mismatch move the client's recomputed yaw by about a degree at most.
     * 注视点沿朝向的水平前移量；足够长，使零点几格的客户端/服务端位置差最多让客户端重算的偏航角变化约 1 度。
     */
    private static final double HORIZONTAL_LEAD = 16.0D;
    private static final double VERTICAL_RISE = HORIZONTAL_LEAD * Math.tan(Math.toRadians(-FORCED_PITCH));
    private static boolean registered;

    private DominatorPsychoGazeService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(DominatorPsychoGazeService::tick);
    }

    private static void tick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            if (!game.isRunning()) {
                continue;
            }
            for (ServerPlayerEntity dominator : world.getPlayers()) {
                if (!game.isRole(dominator, SparkWitchRoles.dominator())
                        || !GameFunctions.isPlayerPlayingAndAlive(dominator)
                        || PlayerPsychoComponent.KEY.get(dominator).getPsychoTicks() <= 0) {
                    continue;
                }
                forceNearbyPlayersUp(dominator, world);
            }
        }
    }

    private static void forceNearbyPlayersUp(ServerPlayerEntity dominator, ServerWorld world) {
        for (ServerPlayerEntity other : world.getPlayers()) {
            // The one-block distance test goes first: it rejects nearly everyone, so the liveness and Wraith component
            // reads run only for a player standing next to the Dominator. Same conditions, same result.
            // 先做一格距离判断：它会排除几乎所有人，因此只有站在支配者身边的玩家才会读取存活与冤魂组件。条件相同，结果相同。
            if (other == dominator
                    || other.squaredDistanceTo(dominator) >= GAZE_RADIUS_SQUARED
                    || other.isSpectator()
                    || !GameFunctions.isPlayerPlayingAndAlive(other)
                    || WraithStateService.isActive(other)
                    || isLookingUp(other.getPitch())) {
                continue;
            }
            // The latest yaw the victim's client reported; preserved on both the entity and the camera.
            // 受害者客户端最近上报的偏航角；在实体与相机上都保持不变。
            float yaw = other.getYaw();
            // Rotate the server-side entity as well: fake players (Carpet) have no client to receive the packet, but
            // observers still see their heads snap up through the entity tracker. It also marks the victim as looking
            // up until its client reports a lower pitch, which is what stops the resend.
            // 同时旋转服务端实体：假玩家（地毯）没有客户端收包，但观察者仍能通过实体追踪看到它们抬头。它也让服务端在
            // 客户端上报更低的俯仰角之前视其为已抬头，从而停止重复发送。
            forceServerLookUp(other, yaw);
            if (other.networkHandler != null) {
                Vec3d ahead = Vec3d.fromPolar(0.0F, yaw).multiply(HORIZONTAL_LEAD);
                other.networkHandler.sendPacket(new LookAtS2CPacket(
                        EntityAnchorArgumentType.EntityAnchor.EYES,
                        other.getX() + ahead.x,
                        other.getEyeY() + VERTICAL_RISE,
                        other.getZ() + ahead.z
                ));
            }
        }
    }

    /** Pure: whether a pitch already counts as looking up. / 纯函数：该俯仰角是否已算作抬头。 */
    static boolean isLookingUp(float pitch) {
        return pitch <= LOOKING_UP_PITCH;
    }

    private static void forceServerLookUp(ServerPlayerEntity player, float yaw) {
        player.setPitch(FORCED_PITCH);
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.prevPitch = FORCED_PITCH;
        player.prevYaw = yaw;
        player.prevHeadYaw = yaw;
    }
}
