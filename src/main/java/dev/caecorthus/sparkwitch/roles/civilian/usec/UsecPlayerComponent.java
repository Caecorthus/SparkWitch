package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * USEC per-player state ({@code sparkwitch:usec_player}, {@code NEVER_COPY}, appended last in the CCA list), kept out
 * of the shared {@code sparkwitch:player} packet whose field order is frozen. Its only field, {@code scoped}, is synced
 * to every client because others draw the scope glint (S1) and apply the instinct cloak (owner, 2026-10-08). The
 * server is the authority: only its scope service writes it. It is transient and never saved.
 * USEC 每玩家状态（{@code sparkwitch:usec_player}，{@code NEVER_COPY}，追加在 CCA 列表末尾），不放进字段顺序已冻结的
 * 共享 {@code sparkwitch:player} 同步包。唯一字段 {@code scoped} 同步给所有客户端，因为其他人要绘制镜头反光（S1）并
 * 执行本能隐蔽（所有者 2026-10-08）。
 * 服务端是权威：只有其开镜服务写入。该状态是瞬时的，从不保存。
 * <p>Writes are coalesced: {@link #setScoped} only stores the server's value, and {@link #flushScoped}, called once
 * per server tick by {@code UsecScopeService}, broadcasts it when it differs from the value last sent. The
 * {@code sparkwitch:usec_scope} payload is on no deny-list and has no rate gate, so syncing inside the write let one
 * client turn every toggle it sent into a packet for every tracking player.
 * <p>写入会被合并：{@link #setScoped} 只保存服务端的值，{@link #flushScoped}（由 {@code UsecScopeService} 每个服务端刻调用
 * 一次）仅在其与上次发出的值不同时广播。{@code sparkwitch:usec_scope} 数据包不在任何拒绝列表中，也没有限速，在写入时立即
 * 同步会让单个客户端把自己发出的每次切换都变成发给所有追踪玩家的数据包。
 */
public final class UsecPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<UsecPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            UsecRules.PLAYER_COMPONENT_ID, UsecPlayerComponent.class);

    private final PlayerEntity player;
    private boolean scoped;
    /** Server only: the value last broadcast by {@link #flushScoped}. / 仅服务端：{@link #flushScoped} 上次广播的值。 */
    private boolean sentScoped;

    public UsecPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isScoped() {
        return scoped;
    }

    /**
     * Server only; stores the value, which server code reads at once. Clients learn of a change from the next
     * {@link #flushScoped}, at the end of the server tick (at most one tick later).
     * 仅服务端调用；保存该值，服务端代码可立即读取。客户端在下一次 {@link #flushScoped}（服务端刻结束时，至多晚一刻）得知变化。
     */
    public void setScoped(boolean scoped) {
        this.scoped = scoped;
    }

    /**
     * Server only, once per tick: syncs only when the value differs from the one last sent, so each player causes at
     * most one broadcast per tick however many writes arrived.
     * 仅服务端调用，每刻一次：只在当前值与上次发出的值不同时同步，因此无论收到多少次写入，每名玩家每刻至多引发一次广播。
     */
    public void flushScoped() {
        if (sentScoped != scoped) {
            sentScoped = scoped;
            sync();
        }
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(scoped);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        scoped = buf.readBoolean();
    }

    /** Transient: a reload always starts unscoped. / 瞬时状态：重新加载后总是未开镜。 */
    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        scoped = false;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
    }
}
