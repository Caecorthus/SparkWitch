package dev.caecorthus.sparkwitch.impl;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CeremonialSwordDashService {
    public static final double DASH_DISTANCE_BLOCKS = 6.0;
    public static final double DASH_BLOCKS_PER_TICK = 1.5;
    public static final double DASH_STEP_BLOCKS = 0.25;

    private static final Map<UUID, DashState> DASHES = new HashMap<>();
    private static boolean registered;

    private CeremonialSwordDashService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(CeremonialSwordDashService::tickServer);
    }

    public static void start(ServerPlayerEntity player) {
        Vec3d direction = horizontalDirection(player);
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }
        DASHES.put(player.getUuid(), new DashState(direction, DASH_DISTANCE_BLOCKS));
        player.getWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_TRIDENT_RIPTIDE_1,
                SoundCategory.PLAYERS,
                0.8f,
                0.9f
        );
    }

    static Vec3d horizontalDirection(Entity entity) {
        Vec3d look = entity.getRotationVec(1.0f);
        Vec3d horizontal = new Vec3d(look.x, 0.0, look.z);
        if (horizontal.lengthSquared() < 1.0E-6) {
            return Vec3d.ZERO;
        }
        return horizontal.normalize();
    }

    public static boolean shouldKeepDashActive(boolean playerPresent, boolean alive, boolean spectator) {
        return playerPresent
                && alive
                && !spectator;
    }

    private static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, DashState>> iterator = DASHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DashState> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !shouldKeepDashActive(true, player.isAlive(), player.isSpectator())) {
                iterator.remove();
                continue;
            }
            if (advance(player, entry.getValue())) {
                iterator.remove();
            }
        }
    }

    private static boolean advance(ServerPlayerEntity player, DashState state) {
        double budget = Math.min(DASH_BLOCKS_PER_TICK, state.remainingDistance());
        while (budget > 0.0) {
            double step = Math.min(Math.min(DASH_STEP_BLOCKS, budget), state.remainingDistance());
            Vec3d delta = state.direction().multiply(step);
            Box currentBox = player.getBoundingBox();
            Box nextBox = currentBox.offset(delta);

            ServerPlayerEntity target = findDashTarget(player, currentBox.stretch(delta).expand(0.15));
            if (target != null) {
                CeremonialSwordCombatService.killWithCeremonialSword(player, target);
                return true;
            }

            // Server-authoritative movement stops before solid collision.
            // 服务端权威位移在实体碰撞盒碰到实体方块前停止。
            if (!player.getServerWorld().isSpaceEmpty(player, nextBox)) {
                return true;
            }

            player.requestTeleport(player.getX() + delta.x, player.getY() + delta.y, player.getZ() + delta.z);
            state.consume(step);
            budget -= step;
            if (state.remainingDistance() <= 0.0) {
                return true;
            }
        }
        return state.remainingDistance() <= 0.0;
    }

    private static ServerPlayerEntity findDashTarget(ServerPlayerEntity player, Box sweptBox) {
        for (Entity entity : player.getServerWorld().getOtherEntities(player, sweptBox, entity -> entity instanceof ServerPlayerEntity)) {
            if (CeremonialSwordCombatService.canStrike(player, entity)) {
                return (ServerPlayerEntity) entity;
            }
        }
        return null;
    }

    private static final class DashState {
        private final Vec3d direction;
        private double remainingDistance;

        private DashState(Vec3d direction, double remainingDistance) {
            this.direction = direction.normalize();
            this.remainingDistance = remainingDistance;
        }

        private Vec3d direction() {
            return direction;
        }

        private double remainingDistance() {
            return remainingDistance;
        }

        private void consume(double distance) {
            remainingDistance = Math.max(0.0, remainingDistance - distance);
        }
    }
}
