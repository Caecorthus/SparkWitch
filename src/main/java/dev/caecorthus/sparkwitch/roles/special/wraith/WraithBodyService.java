package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.mixin.PlayerBodyEntityWraithAccessor;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.index.WatheEntities;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Ensures a qualifying Wraith death leaves exactly one ordinary corpse. */
final class WraithBodyService {
    private WraithBodyService() {
    }

    static void ensureDeathBody(
            ServerPlayerEntity player,
            Identifier deathReason,
            int deathGameTime,
            Identifier originalRoleId
    ) {
        ServerWorld world = player.getServerWorld();
        boolean bodyAlreadySpawned = world.getEntitiesByType(
                WatheEntities.PLAYER_BODY,
                body -> isDeathBody(body, player.getUuid(), deathGameTime)
        ).stream().findAny().isPresent();
        if (bodyAlreadySpawned) {
            return;
        }

        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null) {
            return;
        }
        body.setPlayerUuid(player.getUuid());
        // setPlayerUuid snapshots the live role, which may have changed during deferred listeners.
        // setPlayerUuid 会快照当前身份，但延迟监听期间该身份可能已经改变。
        body.getDataTracker().set(
                PlayerBodyEntityWraithAccessor.sparkwitch$getDeathRole(),
                originalRoleId.toString()
        );
        body.setDeathReason(deathReason);
        body.setDeathGameTime(deathGameTime);
        Vec3d spawnPos = player.getPos().add(player.getRotationVector().normalize());
        body.refreshPositionAndAngles(spawnPos.getX(), player.getY(), spawnPos.getZ(), player.getHeadYaw(), 0.0F);
        body.setYaw(player.getHeadYaw());
        body.setHeadYaw(player.getHeadYaw());
        world.spawnEntity(body);
    }

    private static boolean isDeathBody(PlayerBodyEntity body, UUID playerUuid, int deathGameTime) {
        return playerUuid.equals(body.getPlayerUuid()) && body.getDeathGameTime() == deathGameTime;
    }
}
