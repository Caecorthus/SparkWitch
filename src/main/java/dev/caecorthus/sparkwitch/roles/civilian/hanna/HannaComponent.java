package dev.caecorthus.sparkwitch.roles.civilian.hanna;

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

/** 汉娜: float / penalty / cooldown readouts. 汉娜：浮游、惩罚与冷却的显示值。 */
public final class HannaComponent implements AutoSyncedComponent {
    public static final ComponentKey<HannaComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("hanna_player"),
            HannaComponent.class
    );

    private final PlayerEntity player;
    private int cooldownSeconds;
    private int floatSeconds;
    private int penaltySeconds;

    public HannaComponent(PlayerEntity player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(player);
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getFloatSeconds() {
        return floatSeconds;
    }

    public int getPenaltySeconds() {
        return penaltySeconds;
    }

    public void set(int cooldown, int floating, int penalty) {
        if (cooldown != cooldownSeconds || floating != floatSeconds || penalty != penaltySeconds) {
            cooldownSeconds = Math.max(0, cooldown);
            floatSeconds = Math.max(0, floating);
            penaltySeconds = Math.max(0, penalty);
            sync();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Everyone needs the float flag so the instinct filter can hide a floating player.
        // 所有人都要拿到浮游标记，本能透视过滤器才能把浮游中的玩家藏掉。
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(cooldownSeconds);
        buf.writeInt(floatSeconds);
        buf.writeInt(penaltySeconds);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownSeconds = buf.readInt();
        floatSeconds = buf.readInt();
        penaltySeconds = buf.readInt();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        cooldownSeconds = 0;
        floatSeconds = 0;
        penaltySeconds = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
    }
}
