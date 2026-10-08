package dev.caecorthus.sparkwitch.roles.special.wraith.conversion;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapEnhancementsWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.config.datapack.RoomConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where a credited train-fall victim wakes as a Wraith: its own Wathe room spawn, read from the round's room roster
 * and the map's room config with the same index Wathe used at round start. No room, no configured spawn points
 * (legacy maps), or a spawn below the fall line means no destination, and the fall does not convert.
 * 被记功推下车的玩家作为冤魂苏醒的位置：自己的 Wathe 房间出生点，按本局房间名单与地图房间配置读取，下标与 Wathe
 * 开局传送一致。没有房间、没有配置出生点（旧地图）或出生点低于坠落线时没有落点，此次坠落不转化。
 */
final class WraithRoomDestination {
    private WraithRoomDestination() {
    }

    static @Nullable RoomConfig.SpawnPoint resolve(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        GameWorldComponent.RoomData room = GameWorldComponent.KEY.get(world).getPlayerRoom(player.getUuid());
        if (room == null) {
            return null;
        }
        return select(
                player.getUuid(),
                room.getPlayers(),
                MapEnhancementsWorldComponent.KEY.get(world).getRoomConfig(room.getIndex()).orElse(null),
                MapVariablesWorldComponent.KEY.get(world).getPlayArea()
        );
    }

    /**
     * Pure rule. Only the fall line of the play area is checked, because the rest of the server box is unreliable.
     * 纯规则。只检查游玩区域的坠落线，因为服务端区域盒的其余边界不可靠。
     */
    static @Nullable RoomConfig.SpawnPoint select(
            UUID playerUuid,
            @Nullable List<UUID> roomPlayers,
            @Nullable RoomConfig roomConfig,
            @Nullable Box playArea
    ) {
        if (roomPlayers == null || roomConfig == null || roomConfig.spawnPoints().isEmpty()) {
            return null;
        }
        // Wathe's getSpawnPoint wraps the index and throws only on an empty list, which is excluded above.
        // Wathe 的 getSpawnPoint 会对下标取模，仅在列表为空时抛出，上面已排除。
        RoomConfig.SpawnPoint spawn = roomConfig.getSpawnPoint(Math.max(0, roomPlayers.indexOf(playerUuid)));
        return playArea != null && spawn.y() < playArea.minY ? null : spawn;
    }

    /**
     * Moves the already-activated Wraith with the 7-arg teleport. Fall distance is cleared because vanilla plays the
     * landing sound before the Wraith damage cancel, and living players would hear it.
     * 以 7 参数传送移动已激活的冤魂。须清除摔落距离：原版会在冤魂伤害取消之前播放落地声，生者会听到。
     */
    static void placeInRoom(ServerPlayerEntity player, RoomConfig.SpawnPoint spawn) {
        player.teleport(player.getServerWorld(), spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
    }
}
