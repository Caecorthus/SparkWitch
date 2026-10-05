package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Server-only Time Stealer state ({@code sparkwitch:time_stealer}), kept outside the shared {@code sparkwitch:player}
 * schema: the authoritative Clock and Gift Watch ready ticks, the bound match id, and the stamp retry counter. It is
 * never synced (it deliberately does not implement {@code AutoSyncedComponent}); stamps themselves live only in the
 * inventory. The frozen NBT keys are exactly {@code ClockReadyAt}, {@code GiftReadyAt}, {@code Match}, and
 * {@code UndeliveredStamps}.
 * 仅服务端的窃时者状态（{@code sparkwitch:time_stealer}），不进入共享的 {@code sparkwitch:player} 结构：
 * 权威的时钟与赠时怀表就绪 tick、绑定的对局 id 与邮票重试计数。永不同步（刻意不实现 {@code AutoSyncedComponent}）；
 * 邮票本身只存在于背包中。冻结的 NBT 键恰为 {@code ClockReadyAt}、{@code GiftReadyAt}、{@code Match} 与
 * {@code UndeliveredStamps}。
 */
public final class TimeStealerPlayerComponent implements ServerTickingComponent {
    public static final ComponentKey<TimeStealerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("time_stealer"),
            TimeStealerPlayerComponent.class
    );

    private final PlayerEntity player;
    /** Server world time from which the Clock may be used again. / 时钟可再次使用的服务端世界时间。 */
    private long clockReadyAt;
    /**
     * Server world time from which the Gift Watch may be used again; 0 until the Gift Watch is first granted this round.
     * 赠时怀表可再次使用的服务端世界时间；本局首次发放赠时怀表之前为 0。
     */
    private long giftReadyAt;
    private @Nullable UUID matchId;
    /** Stamps that found no free slot; normally 0. / 找不到空位的邮票数；正常为 0。 */
    private int undeliveredStamps;

    public TimeStealerPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public long clockReadyAt() {
        return clockReadyAt;
    }

    public long giftReadyAt() {
        return giftReadyAt;
    }

    public @Nullable UUID matchId() {
        return matchId;
    }

    public int undeliveredStamps() {
        return undeliveredStamps;
    }

    public void setClockReadyAt(long tick) {
        clockReadyAt = Math.max(0L, tick);
    }

    public void setGiftReadyAt(long tick) {
        giftReadyAt = Math.max(0L, tick);
    }

    public void setUndeliveredStamps(int count) {
        undeliveredStamps = Math.max(0, count);
    }

    /**
     * Binds the current match. A changed match drops the stale retry counter but keeps {@code ClockReadyAt}, which the
     * round-start assignment already wrote for this round.
     * 绑定当前对局。对局变化时丢弃过期的重试计数，但保留本局开局分配时已写入的 {@code ClockReadyAt}。
     */
    public boolean bindMatch(@Nullable UUID match) {
        if (Objects.equals(matchId, match)) {
            return false;
        }
        matchId = match;
        undeliveredStamps = 0;
        return true;
    }

    public boolean clear() {
        boolean changed = clockReadyAt != 0L || giftReadyAt != 0L || matchId != null || undeliveredStamps != 0;
        clockReadyAt = 0L;
        giftReadyAt = 0L;
        matchId = null;
        undeliveredStamps = 0;
        return changed;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            TimeStealerLoadoutService.tick(serverPlayer);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putLong("ClockReadyAt", clockReadyAt);
        tag.putLong("GiftReadyAt", giftReadyAt);
        if (matchId != null) {
            tag.putUuid("Match", matchId);
        }
        tag.putInt("UndeliveredStamps", undeliveredStamps);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        clockReadyAt = Math.max(0L, tag.getLong("ClockReadyAt"));
        giftReadyAt = Math.max(0L, tag.getLong("GiftReadyAt"));
        matchId = tag.containsUuid("Match") ? tag.getUuid("Match") : null;
        undeliveredStamps = Math.max(0, tag.getInt("UndeliveredStamps"));
    }
}
