package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.net.SaviorGunDropS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorMuzzleS2CPayload;
import dev.caecorthus.sparkwitch.net.SaviorPistolShootC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorParticles;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorPistolItem;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.block.BedBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Client half of the Savior pistol. Target picking is Wathe's revolver geometry (30-block {@code ProjectileUtil} ray,
 * sleeping players on the aimed bed included); the recoil, local muzzle flash, the muzzle packet for observers and the
 * local gun removal on an innocent-kill punishment all mirror Wathe's revolver.
 * 救世主手枪的客户端部分。选靶沿用 Wathe 左轮的几何（30 格 {@code ProjectileUtil} 射线，包含瞄准床上的睡眠玩家）；后坐、
 * 本地枪口火焰、给观察者的枪口数据包、误杀惩罚时的本地手枪移除，全部对应 Wathe 左轮。
 */
public final class SaviorClient {
    private SaviorClient() {
    }

    public static void register() {
        ParticleFactoryRegistry.getInstance().register(SaviorParticles.GUNSHOT,
                SaviorFlashParticle.GunshotFactory::new);
        SaviorLightsaberAnimation.register();
        SaviorDomainClient.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> SaviorHandFlash.tick());
        SaviorPistolItem.installClientShot(SaviorClient::shoot);
        ClientPlayNetworking.registerGlobalReceiver(SaviorMuzzleS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> spawnTrackedMuzzle(client, payload.shooterId()));
        });
        ClientPlayNetworking.registerGlobalReceiver(SaviorGunDropS2CPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> removeLocalPistol(client.player));
        });
    }

    private static void shoot(PlayerEntity player, ItemStack stack) {
        ClientPlayNetworking.send(new SaviorPistolShootC2SPayload(pickTargetId(player)));
        player.setPitch(player.getPitch() - SaviorRules.PISTOL_RECOIL_PITCH);
        // Wathe: the first-person flash is hand-space, the world flash is only used outside first person.
        // 与 Wathe 相同：第一人称用手的空间火焰，世界粒子只在不处于第一人称时生成。
        SaviorHandFlash.spawn();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.getPerspective() != Perspective.FIRST_PERSON) {
            SaviorMuzzleTracker.spawnLocalFlash(player);
        }
    }

    /** Wathe revolver target pick: nearest alive player on the look ray, or the occupant of the aimed bed. / 左轮选靶。 */
    private static int pickTargetId(PlayerEntity player) {
        double range = SaviorRules.PISTOL_CLIENT_RANGE
                * dev.caecorthus.sparkwitch.compat.SparkTraitsUsecBridge.marksmanRangeMultiplier(player);
        HitResult hit = ProjectileUtil.getCollision(player,
                entity -> entity instanceof PlayerEntity candidate
                        && GameFunctions.isPlayerAliveAndSurvival(candidate),
                range);
        return resolveTargetFromHitResult(player.getWorld(), hit);
    }

    private static int resolveTargetFromHitResult(World world, HitResult hit) {
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity != null) {
                return entity.getId();
            }
        } else if (hit instanceof BlockHitResult blockHit) {
            Optional<PlayerEntity> sleeping = findSleepingPlayerOnBed(world, blockHit);
            if (sleeping.isPresent()) {
                return sleeping.get().getId();
            }
        }
        return -1;
    }

    private static Optional<PlayerEntity> findSleepingPlayerOnBed(World world, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (!(world.getBlockState(pos).getBlock() instanceof BedBlock bed)) {
            return Optional.empty();
        }
        var state = world.getBlockState(pos);
        BedPart part = state.get(BedBlock.PART);
        Direction facing = state.get(BedBlock.FACING);
        BlockPos headPos = part == BedPart.HEAD ? pos : pos.offset(facing);
        for (PlayerEntity candidate : world.getPlayers()) {
            if (!candidate.isSleeping()) {
                continue;
            }
            Optional<BlockPos> sleepingPos = candidate.getSleepingPosition();
            if (sleepingPos.isEmpty()) {
                continue;
            }
            if (sleepingPos.get().equals(headPos) || sleepingPos.get().equals(pos)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static void spawnTrackedMuzzle(MinecraftClient client, String shooterId) {
        if (client.world == null) {
            return;
        }
        PlayerEntity shooter;
        try {
            shooter = client.world.getPlayerByUuid(UUID.fromString(shooterId));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        // The local shooter already spawned its flash inside use(). / 本地射手已在 use() 中生成火焰。
        if (shooter == null || shooter == client.player) {
            return;
        }
        SaviorMuzzleTracker.spawnTrackedFlash(shooter);
    }

    private static void removeLocalPistol(ClientPlayerEntity player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.saviorPistol())) {
                inventory.removeStack(slot, 1);
                return;
            }
        }
    }
}
