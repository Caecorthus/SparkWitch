package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitch;
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
 * Target-owned, server-only Time Gift ({@code sparkwitch:time_gift}), the Gift Watch's counterpart of
 * {@link TimeTheftPlayerComponent}: the stage comes from absolute world ticks, nothing is synced (the target only
 * perceives vanilla Speed and private chimes), and a gift runs independently of any curse on the same player. Frozen
 * NBT keys: {@code Giver} (absent = no gift), {@code Match}, {@code GivenAt}, {@code Stage} (0-4).
 * 由目标持有、仅服务端的赠时（{@code sparkwitch:time_gift}），是赠时怀表对应 {@link TimeTheftPlayerComponent} 的组件：
 * 阶段由绝对世界 tick 推算，不做任何同步（目标只感知原版速度与私有钟声），并与同一玩家身上的诅咒相互独立。
 * 冻结的 NBT 键：{@code Giver}（缺省表示没有赠时）、{@code Match}、{@code GivenAt}、{@code Stage}（0-4）。
 */
public final class TimeGiftPlayerComponent implements ServerTickingComponent {
    public static final ComponentKey<TimeGiftPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("time_gift"),
            TimeGiftPlayerComponent.class
    );
    private static final int MAX_STORED_STAGE = TimeStealerRules.LETHAL_STAGE - 1;

    private final PlayerEntity player;
    private @Nullable UUID giverUuid;
    private @Nullable UUID matchId;
    /** Server world time of the gift. / 赠时发生时的服务端世界时间。 */
    private long givenAt;
    /** Last applied Speed stage (0-4). / 最近已施加的速度阶段（0-4）。 */
    private int stage;

    public TimeGiftPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isGifted() {
        return giverUuid != null;
    }

    public @Nullable UUID giver() {
        return giverUuid;
    }

    public @Nullable UUID matchId() {
        return matchId;
    }

    public long givenAt() {
        return givenAt;
    }

    public int stage() {
        return stage;
    }

    /** Server: starts a gift; refuses a player already being gifted. / 服务端：开始赠时；已在赠时中的玩家拒绝。 */
    public boolean start(UUID giver, @Nullable UUID match, long givenAtTick) {
        if (isGifted() || giver == null) {
            return false;
        }
        giverUuid = giver;
        matchId = match;
        givenAt = Math.max(0L, givenAtTick);
        stage = 0;
        return true;
    }

    public void setStage(int appliedStage) {
        stage = Math.clamp(appliedStage, 0, MAX_STORED_STAGE);
    }

    public boolean clear() {
        boolean changed = giverUuid != null || matchId != null || givenAt != 0L || stage != 0;
        giverUuid = null;
        matchId = null;
        givenAt = 0L;
        stage = 0;
        return changed;
    }

    @Override
    public void serverTick() {
        if (!isGifted()) {
            return;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            TimeGiftRuntime.tick(serverPlayer, this);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (giverUuid != null) {
            tag.putUuid("Giver", giverUuid);
        }
        if (matchId != null) {
            tag.putUuid("Match", matchId);
        }
        tag.putLong("GivenAt", givenAt);
        tag.putInt("Stage", stage);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        giverUuid = tag.containsUuid("Giver") ? tag.getUuid("Giver") : null;
        matchId = tag.containsUuid("Match") ? tag.getUuid("Match") : null;
        givenAt = Math.max(0L, tag.getLong("GivenAt"));
        stage = Math.clamp(tag.getInt("Stage"), 0, MAX_STORED_STAGE);
    }
}
