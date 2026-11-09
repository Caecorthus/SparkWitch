package dev.caecorthus.sparkwitch.roles.civilian.athlete;

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
 * Stable CCA contract {@code sparkwitch:athlete} (NEVER_COPY): the absolute world tick at which the Athlete's dash is
 * ready again. Server-authoritative and synced only to its owner, whose HUD counts it down against the client world
 * clock; the client never predicts it. NBT keeps it across a mid-round relog; role assignment and Wathe's
 * {@code ResetPlayer} clear it. The forced-cooldown store {@code sparkwitch:athlete_dash} only ever raises it.
 * 稳定 CCA 契约 {@code sparkwitch:athlete}（NEVER_COPY）：运动员冲刺再次就绪的绝对世界刻。服务端权威，只同步给本人，
 * 本人 HUD 依据客户端世界时钟倒数；客户端从不预测。NBT 使其在局中重新登录后保留；身份分配与 Wathe 的
 * {@code ResetPlayer} 会清空它。强制冷却存储 {@code sparkwitch:athlete_dash} 只会把它推后。
 */
public final class AthleteComponent implements AutoSyncedComponent {
    public static final ComponentKey<AthleteComponent> KEY = ComponentRegistry.getOrCreate(
            AthleteRules.ROLE_ID, AthleteComponent.class);
    static final String DASH_READY_TICK = "DashReadyTick";

    private final PlayerEntity player;
    private long dashReadyTick;

    public AthleteComponent(PlayerEntity player) {
        this.player = player;
    }

    public long dashReadyTick() {
        return dashReadyTick;
    }

    /** Ticks until the dash is ready at world time {@code now}; 0 when ready. / 在世界时间 now 时距冲刺就绪的刻数，就绪为 0。 */
    public int dashCooldownRemaining(long now) {
        return (int) Math.clamp(dashReadyTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Server only: moves the ready tick and syncs a change. / 仅服务端：设置就绪刻，有变化时同步。 */
    public void setDashReadyTick(long tick) {
        long next = Math.max(0L, tick);
        if (next != dashReadyTick) {
            dashReadyTick = next;
            sync();
        }
    }

    /** Server only: makes the dash ready. / 仅服务端：使冲刺就绪。 */
    public void clear() {
        setDashReadyTick(0L);
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
        buf.writeVarLong(dashReadyTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        dashReadyTick = Math.max(0L, buf.readVarLong());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        dashReadyTick = Math.max(0L, tag.getLong(DASH_READY_TICK));
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (dashReadyTick > 0L) {
            tag.putLong(DASH_READY_TICK, dashReadyTick);
        }
    }
}
