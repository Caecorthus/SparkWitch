package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side dash for 运动员: five blocks forward along the current horizontal facing, stopping at the last
 * standable tile. A flat area is required: the destination must be walkable and stay within one block of the
 * starting height, so the dash never launches the player off a ledge.
 * 运动员冲刺的服务端实现：沿当前水平朝向前进 5 格，遇到不可站立的格子就停在上一格。要求平地：终点必须可站立且与
 * 起跳高度相差不超过一格，因此不会把玩家甩下高台。
 */
public final class AthleteDashService {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    private AthleteDashService() {
    }

    /**
     * Starts the cooldown the moment this role is drawn, so the dash is not usable in the opening seconds.
     * 抽到该身份的瞬间就开始冷却，避免开场就能冲刺。
     */
    public static void register() {
        RoleAssigned.EVENT.register((player, role) -> {
            if (role != null && AthleteRules.ROLE_ID.equals(role.identifier())) {
                startCooldown(player, AthleteRules.COOLDOWN_TICKS);
            }
        });
    }

    /** Puts the dash on cooldown for {@code ticks}; also used at role assignment. 让冲刺进入冷却；身份分配时也用它。 */
    public static void startCooldown(PlayerEntity player, int ticks) {
        if (player == null) {
            return;
        }
        long now = player.getWorld().getTime();
        COOLDOWN_UNTIL.put(player.getUuid(), now + ticks);
    }

    public static void handle(ServerPlayerEntity player) {
        dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.recordSkill(
                player, dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.ATHLETE_DASH, null, null);
        if (player == null || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(world).getRole(player);
        if (role == null || !AthleteRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        long now = world.getTime();
        if (now < COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L)) {
            return;
        }
        Vec3d direction = horizontalFacing(player);
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }
        Vec3d destination = findDestination(world, player, direction);
        if (destination == null) {
            return;
        }
        player.teleport(world, destination.x, destination.y, destination.z, player.getYaw(), player.getPitch());
        // Keep a little momentum so the dash reads as a burst rather than a teleport.
        // 保留一点速度，让冲刺看起来像爆发而不是瞬移。
        player.setVelocity(direction.x * 0.35D, 0.0D, direction.z * 0.35D);
        player.velocityModified = true;
        // Dash whoosh: the trident riptide burst, audible to nearby players.
        // 冲刺音效：三叉戟激流爆发声，附近玩家都能听到。
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 0.8F, 1.15F);
        COOLDOWN_UNTIL.put(player.getUuid(), now + AthleteRules.COOLDOWN_TICKS);
    }

    public static boolean isOnCooldown(ServerPlayerEntity player) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        return world.getTime() < COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
    }

    private static Vec3d horizontalFacing(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        return new Vec3d(look.x, 0.0D, look.z).normalize();
    }

    /**
     * Walks forward in small steps and returns the furthest position whose feet and head are free and whose ground is
     * at most one block above or below the start.
     * 以小块步长前进，返回脚部与头部都无遮挡、且地面高度与起点相差不超过一格的最远位置。
     */
    private static Vec3d findDestination(ServerWorld world, ServerPlayerEntity player, Vec3d direction) {
        Vec3d start = player.getPos();
        Vec3d best = null;
        for (double distance = AthleteRules.DASH_STEP; distance <= AthleteRules.DASH_BLOCKS; distance += AthleteRules.DASH_STEP) {
            Vec3d candidate = start.add(direction.multiply(distance));
            if (!canStandAt(world, player, candidate, start.y)) {
                break;
            }
            best = candidate;
        }
        return best;
    }

    private static boolean canStandAt(ServerWorld world, ServerPlayerEntity player, Vec3d position, double startY) {
        BlockPos feet = BlockPos.ofFloored(position.x, position.y + 0.1D, position.z);
        BlockPos head = feet.up();
        BlockPos ground = feet.down();
        // Needs solid ground underneath and two free blocks for the player's height.
        // 需要下方有实体地面，且身体占用的两格为空。
        if (world.getBlockState(ground).getCollisionShape(world, ground).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }
        // Flat enough: the destination floor must not drift more than one block from the start.
        // 平地判定：终点地面与起点高度差不超过一格。
        double floorY = ground.getY() + 1.0D;
        return Math.abs(floorY - startY) <= 1.0D;
    }
}
