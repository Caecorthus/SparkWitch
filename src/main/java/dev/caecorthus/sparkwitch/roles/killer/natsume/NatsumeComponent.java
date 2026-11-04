package dev.caecorthus.sparkwitch.roles.killer.natsume;

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

/** 夏目安安: cooldown readout and whether this player is currently hypnotised. 夏目安安：冷却显示与是否处于催眠中。 */
public final class NatsumeComponent implements AutoSyncedComponent {
    public static final ComponentKey<NatsumeComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("natsume_anan_player"),
            NatsumeComponent.class
    );

    private final PlayerEntity player;
    private int cooldownSeconds;
    private int hypnosisSeconds;

    public NatsumeComponent(PlayerEntity player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(player);
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int seconds) {
        int clamped = Math.max(0, seconds);
        if (clamped != cooldownSeconds) {
            cooldownSeconds = clamped;
            sync();
        }
    }

    public int getHypnosisSeconds() {
        return hypnosisSeconds;
    }

    public void setHypnosisSeconds(int seconds) {
        int clamped = Math.max(0, seconds);
        if (clamped != hypnosisSeconds) {
            hypnosisSeconds = clamped;
            sync();
        }
    }

    public boolean isHypnotised() {
        return hypnosisSeconds > 0;
    }

    // TODO(merge): replaced by the server agent's implementation
    public boolean isLocked() { return isHypnotised(); }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(cooldownSeconds);
        buf.writeInt(hypnosisSeconds);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownSeconds = buf.readInt();
        hypnosisSeconds = buf.readInt();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        cooldownSeconds = 0;
        hypnosisSeconds = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
    }
}
