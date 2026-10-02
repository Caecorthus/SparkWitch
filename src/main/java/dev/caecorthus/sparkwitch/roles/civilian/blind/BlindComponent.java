package dev.caecorthus.sparkwitch.roles.civilian.blind;

import java.util.Objects;
import java.util.UUID;
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

/**
 * Stable CCA contract {@code sparkwitch:blind} (NEVER_COPY): the Blind's cane and Attune windows as absolute world
 * ticks plus the bound match id and the match of the last ComTac purchase. Server-authoritative; synced only to the
 * owner, as remaining ticks (see {@link BlindTimers}), never absolute ticks; neither match id is ever synced. Nothing
 * is persisted: a restart or relog starts clean. It never enters the shared {@code sparkwitch:player} schema and holds
 * no game logic.
 * 稳定 CCA 契约 {@code sparkwitch:blind}（NEVER_COPY）：盲人盲杖与凝神窗口（绝对世界刻）、绑定的对局 id 以及最近一次购买
 * ComTac 的对局。由服务端权威决定，仅以剩余刻数同步给本人（见 {@link BlindTimers}），从不同步绝对刻；两个对局 id 都从不
 * 同步。不做任何持久化：重启或重新登录后从空状态开始。从不进入共享的 {@code sparkwitch:player} 结构，也不含游戏逻辑。
 */
public final class BlindComponent implements AutoSyncedComponent {
    public static final ComponentKey<BlindComponent> KEY = ComponentRegistry.getOrCreate(
            BlindRules.ROLE_ID, BlindComponent.class);

    private final PlayerEntity player;
    private final BlindTimers timers = new BlindTimers();
    private @Nullable UUID matchId;
    private @Nullable UUID comTacPurchaseMatch;

    public BlindComponent(PlayerEntity player) {
        this.player = player;
    }

    /** Server only (always null on a client). / 仅服务端（客户端上恒为 null）。 */
    public @Nullable UUID matchId() {
        return matchId;
    }

    public long caneReadyTick() {
        return timers.caneReadyTick();
    }

    public long caneActiveUntilTick() {
        return timers.caneActiveUntilTick();
    }

    public long attuneReadyTick() {
        return timers.attuneReadyTick();
    }

    public long attuneActiveUntilTick() {
        return timers.attuneActiveUntilTick();
    }

    /** {@code now} is this side's {@code getWorld().getTime()}. / {@code now} 为本端的世界时间。 */
    public boolean isCaneActive(long now) {
        return timers.isCaneActive(now);
    }

    public boolean isAttuneActive(long now) {
        return timers.isAttuneActive(now);
    }

    public boolean caneReady(long now) {
        return timers.caneReady(now);
    }

    public boolean attuneReady(long now) {
        return timers.attuneReady(now);
    }

    public int caneCooldownRemaining(long now) {
        return timers.caneCooldownRemaining(now);
    }

    public int caneActiveRemaining(long now) {
        return timers.caneActiveRemaining(now);
    }

    public int attuneCooldownRemaining(long now) {
        return timers.attuneCooldownRemaining(now);
    }

    public int attuneActiveRemaining(long now) {
        return timers.attuneActiveRemaining(now);
    }

    /** Server only; syncs the owner on change. / 仅服务端；有变化时同步给本人。 */
    public void setCane(long activeUntilTick, long readyTick) {
        if (timers.setCane(activeUntilTick, readyTick)) {
            syncOwner();
        }
    }

    /** Server only; syncs the owner on change. / 仅服务端；有变化时同步给本人。 */
    public void setAttune(long activeUntilTick, long readyTick) {
        if (timers.setAttune(activeUntilTick, readyTick)) {
            syncOwner();
        }
    }

    /**
     * Server only. Binds the current match; returns true when it changed (callers decide what a new match resets).
     * 仅服务端。绑定当前对局；对局变化时返回 true（由调用方决定新对局需要重置什么）。
     */
    public boolean bindMatch(@Nullable UUID match) {
        if (Objects.equals(matchId, match)) {
            return false;
        }
        matchId = match;
        return true;
    }

    /**
     * Server only, never synced or saved (C14): the match of this player's last ComTac purchase, or null; the
     * one-per-round rule lives in {@code BlindKitRules.refusesComTacPurchase}.
     * 仅服务端，从不同步或保存（C14）：该玩家最近一次购买 ComTac 的对局，或 null；每局一件的规则见
     * {@code BlindKitRules.refusesComTacPurchase}。
     */
    public @Nullable UUID comTacPurchaseMatch() {
        return comTacPurchaseMatch;
    }

    /** Server only: records a ComTac purchase in {@code match}. / 仅服务端：记录在 {@code match} 中的一次 ComTac 购买。 */
    public void markComTacBought(@Nullable UUID match) {
        comTacPurchaseMatch = match;
    }

    /**
     * Drops every window and the match binding; syncs the owner when a window changed. The ComTac purchase survives,
     * so losing and regaining the role in the same match never allows a second purchase (C14).
     * 清空所有窗口与对局绑定；有窗口变化时同步给本人。ComTac 购买记录保留，因此同一对局内失去又重获职业也不能再次购买（C14）。
     */
    public void clear() {
        matchId = null;
        if (timers.clear()) {
            syncOwner();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        timers.writeSync(buf, player.getWorld().getTime());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        timers.readSync(buf, player.getWorld().getTime());
    }

    /** Never persisted. / 从不持久化。 */
    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        timers.clear();
        matchId = null;
        comTacPurchaseMatch = null;
    }

    private void syncOwner() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }
}
