package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

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
 * Stable CCA contract {@code sparkwitch:control_expert_status} (NEVER_COPY): each player's own disrupt and stun
 * counters. Server-authoritative; synced only to the affected player as remaining ticks, so no other client
 * learns who was disrupted or stunned. It never enters the shared {@code sparkwitch:player} schema.
 * 稳定 CCA 契约 {@code sparkwitch:control_expert_status}（NEVER_COPY）：每位玩家自己的干扰与眩晕计时。
 * 由服务端权威决定，仅以剩余刻数同步给受影响的玩家本人，其他客户端无从得知谁被干扰或眩晕；
 * 它从不进入共享的 {@code sparkwitch:player} 结构。
 */
public final class ControlExpertStatusComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ControlExpertStatusComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("control_expert_status"), ControlExpertStatusComponent.class);

    private final PlayerEntity player;
    private final ControlExpertStatusState state = new ControlExpertStatusState();

    public ControlExpertStatusComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getDisruptTicks() {
        return state.disruptTicks();
    }

    public int getStunTicks() {
        return state.stunTicks();
    }

    public boolean isDisrupted() {
        return state.isDisrupted();
    }

    public boolean isStunned() {
        return state.isStunned();
    }

    public boolean blocksInstinct() {
        return state.blocksInstinct();
    }

    /** Server only; max semantics. / 仅服务端调用；取最大值。 */
    public void disrupt(int ticks) {
        if (state.disrupt(ticks)) {
            syncOwner();
        }
    }

    /** Server only; max semantics, no immunity. / 仅服务端调用；取最大值，无免疫。 */
    public void stun(int ticks) {
        if (state.stun(ticks)) {
            syncOwner();
        }
    }

    public void clear() {
        if (state.clear()) {
            syncOwner();
        }
    }

    @Override
    public void serverTick() {
        if (!state.isActive()) {
            return;
        }
        // Death, spectating, creative, active Wraith or a stopped game drops both effects at once.
        // 死亡、旁观、创造、激活冤魂或对局结束时立即清除两种效果。
        if (!ControlExpertTargeting.isParticipant(player)) {
            clear();
            return;
        }
        if (state.serverTick()) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        state.clientTick();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        state.writeSync(buf);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        state.readSync(buf);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.writeNbt(tag);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.readNbt(tag);
    }

    private void syncOwner() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }
}
