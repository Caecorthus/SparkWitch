package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Owner-only Ceremonial Sword readiness, separate from skill cooldowns.
 * 仅拥有者可见的仪礼剑就绪状态，不占用技能或物品冷却。
 */
public final class GrandWitchRuntimeComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<GrandWitchRuntimeComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("grand_witch_runtime"), GrandWitchRuntimeComponent.class);

    private final PlayerEntity player;
    private int swordKillCooldownTicks;
    private boolean swordStrikeInProgress;

    public GrandWitchRuntimeComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getSwordKillCooldownTicks() {
        return swordKillCooldownTicks;
    }

    public void setSwordKillCooldownTicks(int ticks) {
        int normalized = Math.max(0, ticks);
        if (swordKillCooldownTicks != normalized) {
            swordKillCooldownTicks = normalized;
            sync();
        }
    }

    public boolean isSwordStrikeInProgress() {
        return swordStrikeInProgress;
    }

    public void setSwordStrikeInProgress(boolean inProgress) {
        swordStrikeInProgress = inProgress;
    }

    public void clear() {
        swordStrikeInProgress = false;
        if (swordKillCooldownTicks == 0) {
            return;
        }
        swordKillCooldownTicks = 0;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void serverTick() {
        dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordItem.updateMovementSpeed(player);
        if (swordKillCooldownTicks > 0) {
            swordKillCooldownTicks--;
            if (swordKillCooldownTicks % 20 == 0) {
                sync();
            }
        }
    }

    @Override
    public void clientTick() {
        if (swordKillCooldownTicks > 0) {
            swordKillCooldownTicks--;
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(swordKillCooldownTicks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        swordKillCooldownTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putInt("SwordKillCooldown", swordKillCooldownTicks);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        swordKillCooldownTicks = Math.max(0, tag.getInt("SwordKillCooldown"));
        swordStrikeInProgress = false;
    }
}
