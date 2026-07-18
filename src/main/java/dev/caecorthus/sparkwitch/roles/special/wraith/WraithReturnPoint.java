package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A player's own round-start location, never the map-wide spawn.
 * 玩家自己的开局位置，而不是地图全局出生点。
 */
public record WraithReturnPoint(
        RegistryKey<World> worldKey,
        Vec3d position,
        float yaw,
        float pitch
) {
    public WraithReturnPoint {
        if (worldKey == null || position == null) {
            throw new IllegalArgumentException("Wraith return point requires a world and position");
        }
    }
}
