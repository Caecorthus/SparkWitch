package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.impl.NoellesRoleEnhancementRules;
import dev.caecorthus.sparkwitch.impl.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * Stores per-player runtime state for SparkWitch's NoellesRoles enhancements.
 * 保存 SparkWitch 给 NoellesRoles 角色追加的玩家运行态。
 */
public final class RoleEnhancementPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<RoleEnhancementPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("role_enhancements"),
            RoleEnhancementPlayerComponent.class
    );

    private final PlayerEntity player;
    private int criminologistCooldownTicks;
    private @Nullable UUID criminologistPendingVictimUuid;
    private @Nullable UUID criminologistTrackingTargetUuid;
    private int criminologistRevealTicks;
    private int criminologistRevealIntervalTicks;
    private boolean flashlightOn;
    private int flashlightParticleTicks;

    public RoleEnhancementPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCriminologistCooldownTicks() {
        return criminologistCooldownTicks;
    }

    public @Nullable UUID getCriminologistPendingVictimUuid() {
        return criminologistPendingVictimUuid;
    }

    public @Nullable UUID getCriminologistTrackingTargetUuid() {
        return criminologistTrackingTargetUuid;
    }

    public int getCriminologistRevealTicks() {
        return criminologistRevealTicks;
    }

    public boolean isCriminologistRevealing(UUID targetUuid) {
        return criminologistRevealTicks > 0 && targetUuid.equals(criminologistTrackingTargetUuid);
    }

    public boolean hasCriminologistTarget() {
        return criminologistTrackingTargetUuid != null;
    }

    public boolean isFlashlightOn() {
        return flashlightOn;
    }

    public void initializeCriminologist() {
        criminologistCooldownTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_INITIAL_COOLDOWN_TICKS;
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        sync();
    }

    public void setCriminologistPendingVictim(@Nullable UUID victimUuid) {
        if (victimUuid == null ? criminologistPendingVictimUuid == null : victimUuid.equals(criminologistPendingVictimUuid)) {
            return;
        }
        criminologistPendingVictimUuid = victimUuid;
        sync();
    }

    public void startCriminologistTracking(UUID targetUuid) {
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = targetUuid;
        criminologistCooldownTicks = 0;
        criminologistRevealTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_TICKS;
        criminologistRevealIntervalTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_INTERVAL_TICKS;
        sync();
    }

    public void startCriminologistCooldown() {
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        criminologistCooldownTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_COOLDOWN_TICKS;
        sync();
    }

    public void clearCriminologist() {
        if (criminologistCooldownTicks == 0
                && criminologistPendingVictimUuid == null
                && criminologistTrackingTargetUuid == null
                && criminologistRevealTicks == 0
                && criminologistRevealIntervalTicks == 0) {
            return;
        }
        criminologistCooldownTicks = 0;
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        sync();
    }

    public void setFlashlightOn(boolean flashlightOn) {
        if (this.flashlightOn == flashlightOn) {
            return;
        }
        this.flashlightOn = flashlightOn;
        flashlightParticleTicks = 0;
        sync();
    }

    public void clearAll() {
        boolean changed = criminologistCooldownTicks != 0
                || criminologistPendingVictimUuid != null
                || criminologistTrackingTargetUuid != null
                || criminologistRevealTicks != 0
                || criminologistRevealIntervalTicks != 0
                || flashlightOn;
        criminologistCooldownTicks = 0;
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        flashlightOn = false;
        flashlightParticleTicks = 0;
        if (changed) {
            sync();
        }
    }

    public void sync() {
        if (player != null) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(serverPlayer.getServerWorld());
        Role role = gameComponent.getRole(serverPlayer);
        if (!NoellesRoleIds.isDetective(role) && hasCriminologistRuntime()) {
            clearCriminologist();
        }
        if (!NoellesRoleIds.isAttendant(role) && flashlightOn) {
            setFlashlightOn(false);
        }

        tickCriminologist(serverPlayer, gameComponent, role);
        tickFlashlight(serverPlayer, role);
    }

    private void tickCriminologist(ServerPlayerEntity serverPlayer, GameWorldComponent gameComponent, Role role) {
        if (!NoellesRoleIds.isDetective(role)) {
            return;
        }

        boolean shouldSync = false;
        if (criminologistCooldownTicks > 0) {
            criminologistCooldownTicks--;
            shouldSync |= criminologistCooldownTicks == 0 || criminologistCooldownTicks % 20 == 0;
        }

        if (criminologistTrackingTargetUuid != null) {
            if (gameComponent.isPlayerDead(criminologistTrackingTargetUuid)
                    || !targetIsPlayingAndAlive(serverPlayer.getServerWorld(), criminologistTrackingTargetUuid)) {
                startCriminologistCooldown();
                return;
            }
            if (criminologistRevealTicks > 0) {
                criminologistRevealTicks--;
                shouldSync |= criminologistRevealTicks == 0 || criminologistRevealTicks % 20 == 0;
            } else {
                criminologistRevealIntervalTicks--;
                if (criminologistRevealIntervalTicks <= 0) {
                    criminologistRevealTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_TICKS;
                    criminologistRevealIntervalTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_INTERVAL_TICKS;
                    shouldSync = true;
                }
            }
        }

        if (shouldSync) {
            sync();
        }
    }

    private void tickFlashlight(ServerPlayerEntity serverPlayer, Role role) {
        if (!flashlightOn || !NoellesRoleIds.isAttendant(role) || !GameFunctions.isPlayerPlayingAndAlive(serverPlayer)) {
            return;
        }

        flashlightParticleTicks++;
        if (flashlightParticleTicks < 2) {
            return;
        }
        flashlightParticleTicks = 0;
        spawnFlashlightParticles(serverPlayer);
    }

    private static boolean targetIsPlayingAndAlive(ServerWorld world, UUID targetUuid) {
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetUuid);
        return target != null && GameFunctions.isPlayerPlayingAndAlive(target);
    }

    private static void spawnFlashlightParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d start = player.getEyePos().add(player.getRotationVector().normalize().multiply(0.7));
        Vec3d direction = player.getRotationVector().normalize();
        double range = NoellesRoleEnhancementRules.FLASHLIGHT_RANGE_BLOCKS;
        for (double distance = 2.0; distance <= range; distance += 2.0) {
            Vec3d point = start.add(direction.multiply(distance));
            double spread = 0.02 + distance * 0.006;
            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    point.x,
                    point.y,
                    point.z,
                    1,
                    spread,
                    spread,
                    spread,
                    0.0
            );
        }
    }

    private boolean hasCriminologistRuntime() {
        return criminologistCooldownTicks > 0
                || criminologistPendingVictimUuid != null
                || criminologistTrackingTargetUuid != null
                || criminologistRevealTicks > 0
                || criminologistRevealIntervalTicks > 0;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean ownerVisible = recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
        buf.writeVarInt(ownerVisible ? criminologistCooldownTicks : 0);
        writeOptionalUuid(buf, ownerVisible ? criminologistPendingVictimUuid : null);
        writeOptionalUuid(buf, ownerVisible ? criminologistTrackingTargetUuid : null);
        buf.writeVarInt(ownerVisible ? criminologistRevealTicks : 0);
        buf.writeBoolean(ownerVisible && flashlightOn);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        criminologistCooldownTicks = Math.max(0, buf.readVarInt());
        criminologistPendingVictimUuid = readOptionalUuid(buf);
        criminologistTrackingTargetUuid = readOptionalUuid(buf);
        criminologistRevealTicks = Math.max(0, buf.readVarInt());
        flashlightOn = buf.readBoolean();
        criminologistRevealIntervalTicks = 0;
        flashlightParticleTicks = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (criminologistCooldownTicks > 0) {
            tag.putInt("CriminologistCooldownTicks", criminologistCooldownTicks);
        }
        if (criminologistPendingVictimUuid != null) {
            tag.putUuid("CriminologistPendingVictim", criminologistPendingVictimUuid);
        }
        if (criminologistTrackingTargetUuid != null) {
            tag.putUuid("CriminologistTrackingTarget", criminologistTrackingTargetUuid);
        }
        if (criminologistRevealTicks > 0) {
            tag.putInt("CriminologistRevealTicks", criminologistRevealTicks);
        }
        if (criminologistRevealIntervalTicks > 0) {
            tag.putInt("CriminologistRevealIntervalTicks", criminologistRevealIntervalTicks);
        }
        if (flashlightOn) {
            tag.putBoolean("FlashlightOn", true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        criminologistCooldownTicks = tag.contains("CriminologistCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CriminologistCooldownTicks"))
                : 0;
        criminologistPendingVictimUuid = tag.containsUuid("CriminologistPendingVictim")
                ? tag.getUuid("CriminologistPendingVictim")
                : null;
        criminologistTrackingTargetUuid = tag.containsUuid("CriminologistTrackingTarget")
                ? tag.getUuid("CriminologistTrackingTarget")
                : null;
        criminologistRevealTicks = tag.contains("CriminologistRevealTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CriminologistRevealTicks"))
                : 0;
        criminologistRevealIntervalTicks = tag.contains("CriminologistRevealIntervalTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CriminologistRevealIntervalTicks"))
                : 0;
        flashlightOn = tag.getBoolean("FlashlightOn");
        flashlightParticleTicks = 0;
    }

    private static void writeOptionalUuid(RegistryByteBuf buf, @Nullable UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUuid(uuid);
        }
    }

    private static @Nullable UUID readOptionalUuid(RegistryByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return buf.readUuid();
    }
}
