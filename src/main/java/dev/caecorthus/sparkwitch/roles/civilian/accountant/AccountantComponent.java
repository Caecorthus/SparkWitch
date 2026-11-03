package dev.caecorthus.sparkwitch.roles.civilian.accountant;

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

/** 会计: the audit cooldown shown on her HUD. 会计：HUD 上的查账冷却。 */
public final class AccountantComponent implements AutoSyncedComponent {
    public static final ComponentKey<AccountantComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("accountant_player"),
            AccountantComponent.class
    );

    private final PlayerEntity player;
    private int cooldownSeconds;

    public AccountantComponent(PlayerEntity player) {
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

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(cooldownSeconds);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownSeconds = buf.readInt();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        cooldownSeconds = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
    }
}
