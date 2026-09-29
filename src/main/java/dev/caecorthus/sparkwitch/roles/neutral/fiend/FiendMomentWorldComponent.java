package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;

import java.util.UUID;

/**
 * Round-scoped Fiend Moment ({@code sparkwitch:fiend_moment}), synced to every player. Server authority: only the
 * server starts or clears the moment and decides completion from its absolute deadline. The packet carries only a
 * presence flag, the Fiend UUID and the remaining ticks, never absolute server time or the match id; the server
 * resyncs on every change. Clients count the remaining ticks down locally for presentation and never decide an
 * outcome. Never persisted: a world reload drops the moment. The server also keeps the match-bound spent-Fiend
 * ledger here (Fiends taken out of their moment by a Taotie swallow); it is never synced or persisted.
 * 本局魔人时刻（{@code sparkwitch:fiend_moment}），同步给所有玩家。服务端权威：只有服务端开始或清除时刻，并依据其
 * 绝对截止 tick 判定完成。数据包只携带存在标记、魔人 UUID 与剩余 tick，绝不含服务端绝对时间或对局 id；每次变化由
 * 服务端重新同步。客户端仅为展示在本地倒数剩余 tick，从不决定胜负。从不持久化：世界重载后时刻丢失。服务端还在此
 * 保存绑定对局的「已耗尽魔人」登记表（因饕餮吞噬而退出时刻的魔人），它从不同步也从不持久化。
 */
public final class FiendMomentWorldComponent implements AutoSyncedComponent, ClientTickingComponent {
    public static final ComponentKey<FiendMomentWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("fiend_moment"), FiendMomentWorldComponent.class);

    private final World world;
    private final FiendMomentState state = new FiendMomentState();
    /** Server only, never synced or persisted. / 仅服务端，从不同步或持久化。 */
    private final FiendSpentLedger spent = new FiendSpentLedger();
    private @Nullable UUID clientFiend;
    private int clientRemainingTicks;

    public FiendMomentWorldComponent(World world) {
        this.world = world;
    }

    public static FiendMomentWorldComponent get(World world) {
        return KEY.get(world);
    }

    /**
     * Raw moment check by UUID only (no role check); see {@link FiendParticipation#isMomentFiend} for the role-gated one.
     * 仅按 UUID 判断时刻归属（不检查职业）；需要职业门槛时使用 {@link FiendParticipation#isMomentFiend}。
     */
    public static boolean isMomentFiend(@Nullable PlayerEntity player) {
        return player != null && KEY.get(player.getWorld()).isMomentFiend(player.getUuid());
    }

    public static @Nullable UUID activeFiend(@Nullable World world) {
        return world == null ? null : KEY.get(world).fiend();
    }

    /**
     * Server only: whether this player is a spent Fiend in the current Wathe match (its moment was ended by a Taotie
     * swallow). Always false on the client, where the ledger is never synced.
     * 仅服务端：该玩家在当前 Wathe 对局中是否为已耗尽的魔人（其时刻因饕餮吞噬而结束）。客户端恒为 false，登记表从不同步。
     */
    public static boolean isSpentFiend(@Nullable PlayerEntity player) {
        return player != null && KEY.get(player.getWorld()).isSpent(player.getUuid());
    }

    /** Server only; a no-op on the client. / 仅服务端；客户端调用无效果。 */
    public void start(UUID fiend, int durationTicks, @Nullable UUID matchId) {
        if (world.isClient) {
            return;
        }
        state.start(fiend, world.getTime(), durationTicks, matchId);
        KEY.sync(world);
    }

    /** Server only; a no-op on the client or when no moment is active. / 仅服务端；客户端或无时刻时无效果。 */
    public void clear() {
        if (world.isClient || !state.isActive()) {
            return;
        }
        state.clear();
        KEY.sync(world);
    }

    public boolean isActive() {
        return world.isClient ? clientFiend != null : state.isActive();
    }

    public @Nullable UUID fiend() {
        return world.isClient ? clientFiend : state.fiend();
    }

    public boolean isMomentFiend(@Nullable UUID player) {
        return player != null && player.equals(fiend());
    }

    public int remainingTicks() {
        return world.isClient ? clientRemainingTicks : state.remainingTicks(world.getTime());
    }

    /** Server-authoritative; always false on the client. / 服务端权威；客户端恒为 false。 */
    public boolean isComplete() {
        return !world.isClient && state.isComplete(world.getTime());
    }

    /** Server-only match binding; never synced, so always null on the client. / 仅服务端的对局绑定；从不同步，客户端恒为 null。 */
    public @Nullable UUID matchId() {
        return world.isClient ? null : state.matchId();
    }

    /**
     * Server only: marks a Fiend spent for that match; nothing is synced. A no-op on the client.
     * 仅服务端：将魔人登记为该对局中已耗尽；不进行任何同步。客户端调用无效果。
     */
    public void markSpent(UUID player, @Nullable UUID matchId) {
        if (!world.isClient) {
            spent.mark(player, matchId);
        }
    }

    /** Server only, bound to the current Wathe match; always false on the client. / 仅服务端，绑定当前 Wathe 对局；客户端恒为 false。 */
    public boolean isSpent(@Nullable UUID player) {
        return !world.isClient && !spent.isEmpty() && spent.isSpent(player, FiendMatch.currentId());
    }

    /** Server only: forgets every spent Fiend (round boundary). / 仅服务端：遗忘所有已耗尽魔人（回合边界）。 */
    public void clearSpent() {
        if (!world.isClient) {
            spent.clear();
        }
    }

    @Override
    public void clientTick() {
        if (clientFiend != null && clientRemainingTicks > 0) {
            clientRemainingTicks--;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        UUID fiend = state.fiend();
        buf.writeBoolean(fiend != null);
        if (fiend != null) {
            buf.writeUuid(fiend);
            buf.writeVarInt(state.remainingTicks(world.getTime()));
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        if (buf.readBoolean()) {
            clientFiend = buf.readUuid();
            clientRemainingTicks = Math.max(0, buf.readVarInt());
        } else {
            clientFiend = null;
            clientRemainingTicks = 0;
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.clear();
        spent.clear();
        clientFiend = null;
        clientRemainingTicks = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }
}
