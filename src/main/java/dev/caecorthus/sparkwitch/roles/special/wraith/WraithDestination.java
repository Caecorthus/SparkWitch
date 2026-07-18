package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** A verified server-side teleport destination. */
record WraithDestination(ServerWorld world, Vec3d position, float yaw, float pitch) {
}
