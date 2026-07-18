package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.index.WatheEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves the approved return-point, corpse, and death-snapshot fallback chain.
 * 解析已批准的返回点、尸体与死亡快照回退链。
 */
final class WraithPositionResolver {
    private static final List<Offset> LOCAL_OFFSETS = localOffsets();
    private static final double DOOR_BOUNDARY_SAMPLE_STEP = 0.25D;

    private WraithPositionResolver() {
    }

    static @Nullable WraithDestination resolve(
            ServerPlayerEntity player,
            WraithDeathSnapshot snapshot,
            net.minecraft.util.Identifier deathReason
    ) {
        MinecraftServer server = player.getServer();
        WraithReturnPoint returnPoint = WraithPlayerComponent.KEY.get(player).getReturnPoint();
        if (returnPoint != null) {
            ServerWorld returnWorld = server.getWorld(returnPoint.worldKey());
            if (returnWorld != null && isSafeStand(player, returnWorld, returnPoint.position())) {
                return destination(returnWorld, returnPoint, returnPoint.position());
            }
        }

        WraithReturnPoint fallback = fallback(player, snapshot, deathReason);
        if (fallback == null) {
            return null;
        }
        ServerWorld world = server.getWorld(fallback.worldKey());
        if (world == null || isBelowTrain(world, fallback.position())) {
            return null;
        }
        Vec3d safe = nearestLocalSafeStand(player, world, fallback.position());
        return safe == null ? null : destination(world, fallback, safe);
    }

    private static @Nullable WraithReturnPoint fallback(
            ServerPlayerEntity player,
            WraithDeathSnapshot snapshot,
            net.minecraft.util.Identifier deathReason
    ) {
        if (snapshot.usesSwallowedMentalBreakdownFallback(deathReason)) {
            return snapshot.taotieLocation();
        }
        WraithReturnPoint corpse = actualCorpse(player, snapshot);
        return corpse != null ? corpse : snapshot.deathLocation();
    }

    private static @Nullable WraithReturnPoint actualCorpse(
            ServerPlayerEntity player,
            WraithDeathSnapshot snapshot
    ) {
        ServerWorld world = player.getServer().getWorld(snapshot.deathLocation().worldKey());
        if (world == null) {
            return null;
        }
        PlayerBodyEntity body = world.getEntitiesByType(
                WatheEntities.PLAYER_BODY,
                candidate -> player.getUuid().equals(candidate.getPlayerUuid())
                        && Math.abs((long) candidate.getDeathGameTime() - snapshot.deathGameTime()) <= 1L
        ).stream().findFirst().orElse(null);
        return body == null ? null : new WraithReturnPoint(
                world.getRegistryKey(),
                body.getPos(),
                body.getYaw(),
                body.getPitch()
        );
    }

    private static boolean isBelowTrain(ServerWorld world, Vec3d position) {
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        return playArea != null && !WraithRules.fallbackIsAboveTrain(position.getY(), playArea.minY);
    }

    private static @Nullable Vec3d nearestLocalSafeStand(
            ServerPlayerEntity player,
            ServerWorld world,
            Vec3d anchor
    ) {
        for (Offset offset : LOCAL_OFFSETS) {
            Vec3d candidate = anchor.add(offset.x(), offset.y(), offset.z());
            if (!isBelowTrain(world, candidate)
                    && isSafeStand(player, world, candidate)
                    && staysInLocalCompartment(player, world, anchor, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isSafeStand(ServerPlayerEntity player, ServerWorld world, Vec3d position) {
        BlockPos feet = BlockPos.ofFloored(position);
        BlockPos floor = BlockPos.ofFloored(position.getX(), position.getY() - 0.01D, position.getZ());
        if (!world.getWorldBorder().contains(feet)) {
            return false;
        }
        BlockState floorState = world.getBlockState(floor);
        if (floorState.getCollisionShape(world, floor).isEmpty()) {
            return false;
        }
        Box destinationBox = player.getBoundingBox().offset(position.subtract(player.getPos()));
        return world.isSpaceEmpty(player, destinationBox);
    }

    private static boolean staysInLocalCompartment(
            ServerPlayerEntity player,
            ServerWorld world,
            Vec3d anchor,
            Vec3d candidate
    ) {
        if (anchor.equals(candidate)) {
            return true;
        }
        if (crossesDoorBoundary(world, anchor, candidate)) {
            return false;
        }
        return world.raycast(new RaycastContext(
                anchor.add(0.0D, 0.5D, 0.0D),
                candidate.add(0.0D, 0.5D, 0.0D),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        )).getType() == HitResult.Type.MISS;
    }

    /**
     * Open doors have no collision ray hit, but still separate train compartments.
     * 打开的门不会命中碰撞射线，但仍然属于列车房间边界。
     */
    private static boolean crossesDoorBoundary(ServerWorld world, Vec3d anchor, Vec3d candidate) {
        Vec3d start = anchor.add(0.0D, 0.5D, 0.0D);
        Vec3d delta = candidate.add(0.0D, 0.5D, 0.0D).subtract(start);
        int samples = Math.max(1, (int) Math.ceil(delta.length() / DOOR_BOUNDARY_SAMPLE_STEP));
        for (int sample = 0; sample <= samples; sample++) {
            Vec3d point = start.add(delta.multiply((double) sample / samples));
            Object block = world.getBlockState(BlockPos.ofFloored(point)).getBlock();
            if (block instanceof DoorBlock || block instanceof DoorPartBlock) {
                return true;
            }
        }
        return false;
    }

    private static WraithDestination destination(
            ServerWorld world,
            WraithReturnPoint orientation,
            Vec3d position
    ) {
        return new WraithDestination(world, position, orientation.yaw(), orientation.pitch());
    }

    private static List<Offset> localOffsets() {
        ArrayList<Offset> offsets = new ArrayList<>();
        for (int y = -1; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    offsets.add(new Offset(x, y, z));
                }
            }
        }
        offsets.sort(Comparator.comparingInt(Offset::distanceSquared));
        return List.copyOf(offsets);
    }

    private record Offset(int x, int y, int z) {
        int distanceSquared() {
            return x * x + y * y + z * z;
        }
    }
}
