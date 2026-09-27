package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerSessionState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
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
 * Stable CCA contract {@code sparkwitch:seeker_status} (NEVER_COPY): the Seeker's own car, camera, session, battery
 * and mark state. Server-authoritative; synced only to the owner as remaining ticks, never absolute server ticks. It
 * never enters the shared {@code sparkwitch:player} schema.
 * TODO(WP-02): implement the server tick order, battery drain, owner sync and NBT. / 待 WP-02 实现服务端刻顺序、电量消耗、拥有者同步与 NBT。
 * 稳定 CCA 契约 {@code sparkwitch:seeker_status}（NEVER_COPY）：搜寻者自己的小车、摄像头、会话、电量与标记状态。
 * 由服务端权威决定，仅以剩余刻数同步给拥有者，从不发送绝对服务端刻；从不进入共享的 {@code sparkwitch:player} 结构。
 */
public final class SeekerStatusComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SeekerStatusComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("seeker_status"), SeekerStatusComponent.class);

    private final PlayerEntity player;
    private final SeekerState state = new SeekerState();
    @Nullable
    private SeekerSessionState sessionState;

    public SeekerStatusComponent(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity player() {
        return player;
    }

    /** Mutable pure state; mutate only through transitions, then {@link #apply}. / 仅通过状态转移修改，然后调用 apply。 */
    public SeekerState state() {
        return state;
    }

    public SeekerCarState carState() {
        return state.carState();
    }

    public int carEntityId() {
        return state.carEntityId();
    }

    public int cameraEntityId() {
        return state.cameraEntityId();
    }

    public SeekerSessionMode sessionMode() {
        return state.sessionMode();
    }

    public int sessionId() {
        return state.sessionId();
    }

    public int effectiveRadius() {
        return state.effectiveRadius();
    }

    public SeekerCooldownReason cooldownReason() {
        return state.cooldownReason();
    }

    @Nullable
    public UUID markTarget() {
        return state.markTarget();
    }

    public int markRemainingTicks() {
        return state.markRemainingTicks();
    }

    public int carBattery() {
        return state.carBattery();
    }

    @Nullable
    public UUID carLostTo() {
        return state.carLostTo();
    }

    public boolean pendingReturn() {
        return state.pendingReturn();
    }

    /** WP-09's opaque server-only session data; never synced or saved. / WP-09 独有的仅服务端会话数据，不同步也不存盘。 */
    @Nullable
    public SeekerSessionState sessionState() {
        return sessionState;
    }

    public void setSessionState(@Nullable SeekerSessionState sessionState) {
        this.sessionState = sessionState;
    }

    /** Server only: syncs the owner when the delta is dirty. / 仅服务端：delta 有变化时同步给拥有者。 */
    public void apply(SeekerState.Delta delta) {
        // TODO(WP-02) / 待 WP-02 实现
    }

    public void bindMatch(String matchId) {
        // TODO(WP-02) / 待 WP-02 实现
    }

    public void clearAll() {
        // TODO(WP-02) / 待 WP-02 实现
    }

    public void syncOwner() {
        // TODO(WP-02) / 待 WP-02 实现
    }

    @Override
    public void serverTick() {
        // TODO(WP-02) / 待 WP-02 实现
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
}
