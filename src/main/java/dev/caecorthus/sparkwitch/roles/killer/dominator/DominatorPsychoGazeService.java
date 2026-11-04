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

/**
 * While the Dominator is in psycho mode, every other player within one block is forced to stare straight up, and
 * keeps being forced every tick until they move out of range or the psycho ends or the Dominator dies. Spectators,
 * dead players and wraiths are exempt.
 * 支配者处于疯魔时，1 格内的其他玩家被强制抬头看正上方，并且每 tick 持续强制，直到离开 1 格、疯魔结束或支配者
 * 死亡。观战、死亡与冤魂玩家豁免。
 */
public final class DominatorPsychoGazeService {
    private static final double GAZE_RADIUS_SQUARED = 1.0D;
    private static final double UPWARD_LOOK_DISTANCE = 16.0D;
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
            if (other == dominator
                    || other.isSpectator()
                    || !GameFunctions.isPlayerPlayingAndAlive(other)
                    || WraithStateService.isActive(other)
                    || other.squaredDistanceTo(dominator) >= GAZE_RADIUS_SQUARED) {
                continue;
            }
            // Rotate the server-side entity as well: fake players (Carpet) have no client to receive the packet, but
            // observers still see their heads snap up through the entity tracker. Real players additionally get the
            // camera packet, which actually moves their own view.
            // 同时旋转服务端实体：假玩家（地毯）没有客户端收包，但观察者仍能通过实体追踪看到它们抬头；真实玩家
            // 还会收到相机包，真正改变自己的视角。
            forceServerLookUp(other);
            if (other.networkHandler != null) {
                // The target point sits directly above the viewer's own eyes, so the packet resolves to pitch -90.
                // 目标点就在观看者自己眼睛正上方，数据包解析后俯仰角为 -90（正上方）。
                other.networkHandler.sendPacket(new LookAtS2CPacket(
                        EntityAnchorArgumentType.EntityAnchor.EYES,
                        other.getX(),
                        other.getEyeY() + UPWARD_LOOK_DISTANCE,
                        other.getZ()
                ));
            }
        }
    }

    private static void forceServerLookUp(ServerPlayerEntity player) {
        float yaw = player.getYaw();
        player.setPitch(-90.0F);
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.prevPitch = -90.0F;
        player.prevYaw = yaw;
        player.prevHeadYaw = yaw;
    }
}
