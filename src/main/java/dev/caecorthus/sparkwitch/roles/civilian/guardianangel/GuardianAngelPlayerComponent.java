package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * Stores the Guardian Angel's cooldown and current shield target, synchronized only to its owner.
 * 保存守护天使的冷却与当前护盾目标，并且只同步给持有者本人。
 */
public final class GuardianAngelPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<GuardianAngelPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("guardian_angel"),
            GuardianAngelPlayerComponent.class
    );

    private final PlayerEntity player;
    private final GuardianAngelState state = new GuardianAngelState();

    public GuardianAngelPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return state.cooldownTicks();
    }

    public @Nullable UUID getShieldTargetUuid() {
        return state.shieldTargetUuid();
    }

    public boolean hasActiveShieldTarget() {
        return state.hasActiveShieldTarget();
    }

    public void initializeForPromotion() {
        state.initializeForPromotion();
        sync();
    }

    public boolean assignShield(UUID targetUuid) {
        if (!state.assignShield(targetUuid)) {
            return false;
        }
        sync();
        return true;
    }

    public boolean clearShieldTarget(UUID targetUuid) {
        if (!state.clearShieldTarget(targetUuid)) {
            return false;
        }
        sync();
        return true;
    }

    public void clear() {
        if (state.clear()) {
            sync();
        }
    }

    @Override
    public void serverTick() {
        boolean cooldownChanged = state.tickCooldown();
        if (cooldownChanged && (state.cooldownTicks() == 0 || state.cooldownTicks() % 20 == 0)) {
            sync();
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            GuardianAngelFeatureService.validateShieldTarget(serverPlayer, this);
        }
    }

    @Override
    public void clientTick() {
        state.tickCooldown();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // The target UUID reveals a secret protection relationship and must remain owner-private.
        // 目标 UUID 会泄露秘密保护关系，因此必须只对持有者本人同步。
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(state.cooldownTicks());
        UUID targetUuid = state.shieldTargetUuid();
        buf.writeBoolean(targetUuid != null);
        if (targetUuid != null) {
            buf.writeUuid(targetUuid);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        int cooldownTicks = buf.readVarInt();
        UUID targetUuid = buf.readBoolean() ? buf.readUuid() : null;
        state.restore(cooldownTicks, targetUuid);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (state.cooldownTicks() > 0) {
            tag.putInt("CooldownTicks", state.cooldownTicks());
        }
        if (state.shieldTargetUuid() != null) {
            tag.putUuid("ShieldTarget", state.shieldTargetUuid());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        int cooldownTicks = tag.contains("CooldownTicks", NbtElement.NUMBER_TYPE)
                ? tag.getInt("CooldownTicks")
                : 0;
        UUID targetUuid = tag.containsUuid("ShieldTarget") ? tag.getUuid("ShieldTarget") : null;
        state.restore(cooldownTicks, targetUuid);
    }

    private void sync() {
        KEY.sync(player);
    }
}
