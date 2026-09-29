package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

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
 * Stable CCA contract {@code sparkwitch:fisher_spirit} (NEVER_COPY, never saved): the Glimmerfish window. Every client
 * must know whether a player is glimmering (two-sided collision exemption, held-item hiding, instinct skip), so the
 * active flag is synced to everyone; nothing else about the Angler is. WP4 owns the internals.
 * 稳定 CCA 契约 {@code sparkwitch:fisher_spirit}（NEVER_COPY，不存盘）：灵光鱼窗口。每个客户端都必须知道某玩家
 * 是否处于灵光中（双向无碰撞、隐藏手持物、跳过本能描边），因此只把"灵光中"标记同步给所有人，钓鱼佬的其他信息不同步。
 * 内部实现归 WP4。
 */
public final class FisherSpiritComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<FisherSpiritComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("fisher_spirit"), FisherSpiritComponent.class);

    private final PlayerEntity player;
    private int remainingTicks;

    public FisherSpiritComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isActive() {
        return remainingTicks > 0;
    }

    /** Meaningful on the server and on the owner's client (HUD). / 在服务端与拥有者客户端（HUD）上有意义。 */
    public int remainingTicks() {
        return remainingTicks;
    }

    @Override
    public void serverTick() {
    }

    @Override
    public void clientTick() {
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(recipient == player ? remainingTicks : (remainingTicks > 0 ? 1 : 0));
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        remainingTicks = buf.readVarInt();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }
}
