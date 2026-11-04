package dev.caecorthus.sparkwitch.roles.civilian.noa;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stable CCA contract {@code sparkwitch:noa_player} (NEVER_COPY): the absolute world tick at which Noa's painting is
 * ready again, synced only to its owner (whose HUD counts it down against the client world clock; the client never
 * predicts it), plus the one-off outcomes already painted this round (server only). NBT keeps both across a mid-round
 * relog; role assignment and Wathe's {@code ResetPlayer} (every round end) clear both. The forced-cooldown store
 * {@code sparkwitch:noa_paint} only ever raises the ready tick.
 * 稳定 CCA 契约 {@code sparkwitch:noa_player}（NEVER_COPY）：诺亚作画再次就绪的绝对世界刻，只同步给本人（本人 HUD 依据
 * 客户端世界时钟倒数；客户端从不预测），以及本局已画出的一次性结果（仅服务端）。NBT 使两者在局中重新登录后保留；
 * 身份分配与 Wathe 的 {@code ResetPlayer}（每局结束）会清空两者。强制冷却存储 {@code sparkwitch:noa_paint} 只会把就绪刻推后。
 */
public final class NoaComponent implements AutoSyncedComponent {
    public static final ComponentKey<NoaComponent> KEY = ComponentRegistry.getOrCreate(
            NoaRules.ROLE_ID.withSuffixedPath("_player"), NoaComponent.class);
    static final String PAINT_READY_TICK = "PaintReadyTick";
    static final String PAINTED_ONCE = "PaintedOnce";

    private final PlayerEntity player;
    private long paintReadyTick;
    private final EnumSet<NoaPaintTable.Outcome> paintedOnce = EnumSet.noneOf(NoaPaintTable.Outcome.class);

    public NoaComponent(PlayerEntity player) {
        this.player = player;
    }

    public long paintReadyTick() {
        return paintReadyTick;
    }

    /** Ticks until painting is ready at world time {@code now}; 0 when ready. / 在世界时间 now 时距作画就绪的刻数，就绪为 0。 */
    public int paintCooldownRemaining(long now) {
        return (int) Math.clamp(paintReadyTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Server only: moves the ready tick and syncs a change. / 仅服务端：设置就绪刻，有变化时同步。 */
    public void setPaintReadyTick(long tick) {
        long next = Math.max(0L, tick);
        if (next != paintReadyTick) {
            paintReadyTick = next;
            sync();
        }
    }

    /** One-off outcomes painted this round (read-only view). / 本局已画出的一次性结果（只读视图）。 */
    public Set<NoaPaintTable.Outcome> paintedOnce() {
        return java.util.Collections.unmodifiableSet(paintedOnce);
    }

    /** Server only: remembers a painted one-off outcome. / 仅服务端：记下已画出的一次性结果。 */
    public void markPainted(NoaPaintTable.Outcome outcome) {
        if (outcome.oneOff()) {
            paintedOnce.add(outcome);
        }
    }

    /** Server only: ready painting and forget this round's one-offs. / 仅服务端：作画就绪并清空本局一次性结果。 */
    public void clear() {
        paintedOnce.clear();
        setPaintReadyTick(0L);
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarLong(paintReadyTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        paintReadyTick = Math.max(0L, buf.readVarLong());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        paintReadyTick = Math.max(0L, tag.getLong(PAINT_READY_TICK));
        paintedOnce.clear();
        int bits = tag.getInt(PAINTED_ONCE);
        for (NoaPaintTable.Outcome outcome : NoaPaintTable.Outcome.values()) {
            if (outcome.oneOff() && (bits & (1 << outcome.ordinal())) != 0) {
                paintedOnce.add(outcome);
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (paintReadyTick > 0L) {
            tag.putLong(PAINT_READY_TICK, paintReadyTick);
        }
        int bits = 0;
        for (NoaPaintTable.Outcome outcome : paintedOnce) {
            bits |= 1 << outcome.ordinal();
        }
        if (bits != 0) {
            tag.putInt(PAINTED_ONCE, bits);
        }
    }
}
