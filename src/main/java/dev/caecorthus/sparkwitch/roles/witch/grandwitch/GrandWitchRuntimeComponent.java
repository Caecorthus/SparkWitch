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
 * Owner-only sword readiness and cumulative recruitment state, separate from skill cooldowns.
 * 仅拥有者可见的仪礼剑就绪状态与累计招募记录，不占用技能或物品冷却。
 */
public final class GrandWitchRuntimeComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<GrandWitchRuntimeComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("grand_witch_runtime"), GrandWitchRuntimeComponent.class);

    private final PlayerEntity player;
    private int swordKillCooldownTicks;
    private int recruitmentCount;
    private int roundParticipants;
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

    public int getRecruitmentCount() {
        return recruitmentCount;
    }

    public void setRecruitmentCount(int count) {
        int normalized = Math.max(0, count);
        if (recruitmentCount != normalized) {
            recruitmentCount = normalized;
            sync();
        }
    }

    public void incrementRecruitmentCount() {
        if (recruitmentCount < Integer.MAX_VALUE) {
            recruitmentCount++;
            sync();
        }
    }

    public int getRoundParticipants() {
        return roundParticipants;
    }

    public void setRoundParticipants(int participants) {
        int normalized = Math.max(0, participants);
        if (roundParticipants != normalized) {
            roundParticipants = normalized;
            sync();
        }
    }

    public void clear() {
        swordStrikeInProgress = false;
        if (swordKillCooldownTicks == 0 && recruitmentCount == 0 && roundParticipants == 0) {
            return;
        }
        swordKillCooldownTicks = 0;
        recruitmentCount = 0;
        roundParticipants = 0;
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
        buf.writeVarInt(recruitmentCount);
        buf.writeVarInt(roundParticipants);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        swordKillCooldownTicks = Math.max(0, buf.readVarInt());
        recruitmentCount = Math.max(0, buf.readVarInt());
        roundParticipants = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putInt("SwordKillCooldown", swordKillCooldownTicks);
        tag.putInt("RecruitmentCount", recruitmentCount);
        tag.putInt("RoundParticipants", roundParticipants);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        swordKillCooldownTicks = Math.max(0, tag.getInt("SwordKillCooldown"));
        recruitmentCount = Math.max(0, tag.getInt("RecruitmentCount"));
        roundParticipants = Math.max(0, tag.getInt("RoundParticipants"));
        swordStrikeInProgress = false;
    }
}
