package dev.caecorthus.sparkwitch.roles.civilian.hanna;

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

/**
 * Stable CCA contract {@code sparkwitch:hanna_player} (NEVER_COPY). Server state is four absolute world ticks: when
 * the float is ready again, when a running float ends, when the interruption penalty ends, and when a handkerchief's
 * sanity regeneration ends (any player may carry that last one: the handkerchief works for whoever holds it).
 * <p>Sync: everyone receives only the float's remaining ticks, counted down locally, because every client's instinct
 * must skip a floating player (the Ferryman veil precedent: never a server time, never another field). The owner also
 * receives the ready and penalty ticks for the role-owned HUD, which counts them down against the client world clock
 * (the Noa precedent). NBT keeps all four across a mid-round relog; role assignment and Wathe's {@code ResetPlayer}
 * clear them. The forced-cooldown store {@code sparkwitch:hanna_float} only ever raises the ready tick.
 * 稳定 CCA 契约 {@code sparkwitch:hanna_player}（NEVER_COPY）。服务端状态是四个绝对世界刻：浮游再次就绪、进行中的浮游结束、
 * 被打断惩罚结束，以及手帕回理智结束（最后一项任何玩家都可能持有：手帕谁拿到都能用）。
 * <p>同步：所有人只收到浮游的剩余刻数并在本地递减，因为每个客户端的本能都必须跳过浮游中的玩家（沿用摆渡人帷幕的先例：绝不发送
 * 服务端时间，也不发送其他字段）。本人额外收到就绪刻与惩罚刻，供职业自有 HUD 依据客户端世界时钟倒数（沿用诺亚的先例）。NBT 使
 * 四者在局中重新登录后保留；身份分配与 Wathe 的 {@code ResetPlayer} 会清空。强制冷却存储 {@code sparkwitch:hanna_float}
 * 只会把就绪刻推后。
 */
public final class HannaComponent implements AutoSyncedComponent, ClientTickingComponent {
    public static final ComponentKey<HannaComponent> KEY = ComponentRegistry.getOrCreate(
            HannaRules.ROLE_ID.withSuffixedPath("_player"), HannaComponent.class);
    static final String FLOAT_READY_TICK = "FloatReadyTick";
    static final String FLOAT_END_TICK = "FloatEndTick";
    static final String PENALTY_END_TICK = "PenaltyEndTick";
    static final String SOOTHE_END_TICK = "SootheEndTick";

    private final PlayerEntity player;
    private long floatReadyTick;
    private long floatEndTick;
    private long penaltyEndTick;
    private long sootheEndTick;
    /** Server only, never saved: whether the float's speed modifier is on this player entity. / 仅服务端、不保存。 */
    private boolean speedApplied;
    /** Client only: float ticks left, counted down locally. / 仅客户端：本地递减的浮游剩余刻数。 */
    private int clientFloatTicks;

    public HannaComponent(PlayerEntity player) {
        this.player = player;
    }

    /** True while this player floats, on either side. / 该玩家是否正在浮游（两端一致）。 */
    public boolean isFloating() {
        if (player.getWorld().isClient) {
            return clientFloatTicks > 0;
        }
        return floatEndTick > player.getWorld().getTime();
    }

    /** Client: float ticks left (0 when not floating). / 客户端：浮游剩余刻数（未浮游为 0）。 */
    public int clientFloatTicks() {
        return clientFloatTicks;
    }

    public long floatReadyTick() {
        return floatReadyTick;
    }

    public long floatEndTick() {
        return floatEndTick;
    }

    public long penaltyEndTick() {
        return penaltyEndTick;
    }

    public long sootheEndTick() {
        return sootheEndTick;
    }

    /** Ticks until the float is ready at world time {@code now}; 0 when ready. / 距浮游就绪的刻数，就绪为 0。 */
    public int floatCooldownRemaining(long now) {
        return (int) Math.clamp(floatReadyTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Ticks of penalty left at world time {@code now}. / 惩罚剩余刻数。 */
    public int penaltyRemaining(long now) {
        return penaltyEndTick <= 0L ? 0 : (int) Math.clamp(penaltyEndTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Server only (also the forced-cooldown store's setter): moves the ready tick. / 仅服务端（亦为强制冷却存储的写入口）。 */
    public void setFloatReadyTick(long tick) {
        long next = Math.max(0L, tick);
        if (next != floatReadyTick) {
            floatReadyTick = next;
            sync();
        }
    }

    /**
     * Server only: starts a float at {@code now}; the cooldown is counted from the float's end (owner 2026-11-11).
     * 仅服务端：在 {@code now} 开始浮游；冷却从浮游结束起算（所有者 2026-11-11）。
     */
    public void startFloat(long now) {
        floatEndTick = now + HannaRules.FLOAT_TICKS;
        floatReadyTick = Math.max(floatReadyTick, floatEndTick + HannaRules.COOLDOWN_TICKS);
        sync();
    }

    /**
     * Server only: cuts a running float short at {@code now} and restarts the cooldown from that tick. Returns whether
     * a float was running.
     * 仅服务端：在 {@code now} 提前结束进行中的浮游，并从该刻重新开始冷却。返回此前是否在浮游。
     */
    public boolean endFloat(long now) {
        if (floatEndTick <= now) {
            return false;
        }
        floatEndTick = 0L;
        floatReadyTick = now + HannaRules.COOLDOWN_TICKS;
        sync();
        return true;
    }

    /** Server only: starts the interruption penalty. / 仅服务端：开始被打断的惩罚。 */
    public void startPenalty(long now) {
        penaltyEndTick = now + HannaRules.PENALTY_TICKS;
        sync();
    }

    /** Server only: ends the penalty; returns whether one was running. / 仅服务端：结束惩罚；返回此前是否在惩罚中。 */
    public boolean clearPenalty() {
        if (penaltyEndTick <= 0L) {
            return false;
        }
        penaltyEndTick = 0L;
        sync();
        return true;
    }

    /** Server only: starts a handkerchief's sanity regeneration (never synced). / 仅服务端：开始手帕回理智（不同步）。 */
    public void startSoothe(long now) {
        sootheEndTick = now + HannaRules.HANDKERCHIEF_TICKS;
    }

    /** Server only. / 仅服务端。 */
    public void clearSoothe() {
        sootheEndTick = 0L;
    }

    /** Server only: nothing timed is running, so the per-tick service can skip this player. / 仅服务端：无任何计时。 */
    public boolean idle() {
        return floatEndTick <= 0L && penaltyEndTick <= 0L && sootheEndTick <= 0L && !speedApplied;
    }

    /** Server only: forgets a float that ran out, without touching the cooldown. / 仅服务端：清掉已自然结束的浮游。 */
    public void expireFloat(long now) {
        if (floatEndTick > 0L && floatEndTick <= now) {
            floatEndTick = 0L;
        }
    }

    public boolean speedApplied() {
        return speedApplied;
    }

    public void setSpeedApplied(boolean applied) {
        speedApplied = applied;
    }

    /** Server only: clears every timer (role assignment, round reset). / 仅服务端：清空全部计时（身份分配、换局）。 */
    public void clear() {
        if (floatReadyTick == 0L && floatEndTick == 0L && penaltyEndTick == 0L && sootheEndTick == 0L) {
            return;
        }
        floatReadyTick = 0L;
        floatEndTick = 0L;
        penaltyEndTick = 0L;
        sootheEndTick = 0L;
        sync();
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public void clientTick() {
        if (clientFloatTicks > 0) {
            clientFloatTicks--;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Everyone: each client's instinct must skip a floating player. / 所有人：每个客户端的本能都要跳过浮游中的玩家。
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        long now = player.getWorld().getTime();
        buf.writeVarInt((int) Math.clamp(floatEndTick - now, 0L, HannaRules.FLOAT_TICKS));
        boolean owner = recipient == player;
        buf.writeBoolean(owner);
        if (owner) {
            buf.writeVarLong(floatReadyTick);
            buf.writeVarLong(penaltyEndTick);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        clientFloatTicks = Math.clamp(buf.readVarInt(), 0, HannaRules.FLOAT_TICKS);
        if (buf.readBoolean()) {
            floatReadyTick = Math.max(0L, buf.readVarLong());
            penaltyEndTick = Math.max(0L, buf.readVarLong());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        floatReadyTick = Math.max(0L, tag.getLong(FLOAT_READY_TICK));
        floatEndTick = Math.max(0L, tag.getLong(FLOAT_END_TICK));
        penaltyEndTick = Math.max(0L, tag.getLong(PENALTY_END_TICK));
        sootheEndTick = Math.max(0L, tag.getLong(SOOTHE_END_TICK));
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (floatReadyTick > 0L) {
            tag.putLong(FLOAT_READY_TICK, floatReadyTick);
        }
        if (floatEndTick > 0L) {
            tag.putLong(FLOAT_END_TICK, floatEndTick);
        }
        if (penaltyEndTick > 0L) {
            tag.putLong(PENALTY_END_TICK, penaltyEndTick);
        }
        if (sootheEndTick > 0L) {
            tag.putLong(SOOTHE_END_TICK, sootheEndTick);
        }
    }
}
