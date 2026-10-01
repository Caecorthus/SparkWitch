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
 * Victim-owned, server-only Time Theft curse ({@code sparkwitch:time_theft}), modelled on the Black Raven mark. The
 * stage is derived from absolute world ticks and the server counter alone decides the death; nothing is synced (the
 * victim only perceives vanilla Slowness and private chimes). Frozen NBT keys: {@code Stealer} (absent = not stolen),
 * {@code Match}, {@code StolenAt}, {@code Stage} (0-4).
 * 由受害者持有、仅服务端的窃时诅咒（{@code sparkwitch:time_theft}），参照黑羽鸦标记。阶段由绝对世界 tick 推算，
 * 死亡只由服务端计数决定；不做任何同步（受害者只感知原版缓慢与私有钟声）。冻结的 NBT 键：{@code Stealer}
 * （缺省表示未被窃）、{@code Match}、{@code StolenAt}、{@code Stage}（0-4）。
 */
public final class TimeTheftPlayerComponent implements ServerTickingComponent {
    public static final ComponentKey<TimeTheftPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("time_theft"),
            TimeTheftPlayerComponent.class
    );
    private static final int MAX_STORED_STAGE = TimeStealerRules.LETHAL_STAGE - 1;

    private final PlayerEntity player;
    private @Nullable UUID stealerUuid;
    private @Nullable UUID matchId;
    /** Server world time of the theft. / 被窃时的服务端世界时间。 */
    private long stolenAt;
    /** Last applied non-lethal stage (0-4). / 最近已施加的非致死阶段（0-4）。 */
    private int stage;
    /**
     * Whether the last applied stage established the curse's own Slowness node ({@code TimeTheftSlowness.apply}).
     * Deliberately not persisted (the NBT keys stay frozen): after a reload it is false, so the removal conservatively
     * leaves a bounded curse node to expire rather than risk a foreign one.
     * 最近施加的阶段是否确立了诅咒自己的缓慢节点（{@code TimeTheftSlowness.apply}）。刻意不持久化（NBT 键保持冻结）：
     * 重新加载后为 false，移除时保守地任有界的诅咒节点自然到期，而不冒险删除外来节点。
     */
    private boolean slownessOwned;

    public TimeTheftPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isStolen() {
        return stealerUuid != null;
    }

    public @Nullable UUID stealer() {
        return stealerUuid;
    }

    public @Nullable UUID matchId() {
        return matchId;
    }

    public long stolenAt() {
        return stolenAt;
    }

    public int stage() {
        return stage;
    }

    public boolean slownessOwned() {
        return slownessOwned;
    }

    public void setSlownessOwned(boolean owned) {
        slownessOwned = owned;
    }

    /** Server: starts a curse; refuses an already stolen victim (no re-steal). / 服务端：开始诅咒；已被窃者拒绝（不可重复窃取）。 */
    public boolean start(UUID stealer, @Nullable UUID match, long stolenAtTick) {
        if (isStolen() || stealer == null) {
            return false;
        }
        stealerUuid = stealer;
        matchId = match;
        stolenAt = Math.max(0L, stolenAtTick);
        stage = 0;
        slownessOwned = false;
        return true;
    }

    public void setStage(int appliedStage) {
        stage = Math.clamp(appliedStage, 0, MAX_STORED_STAGE);
    }

    public boolean clear() {
        boolean changed = stealerUuid != null || matchId != null || stolenAt != 0L || stage != 0;
        stealerUuid = null;
        matchId = null;
        stolenAt = 0L;
        stage = 0;
        slownessOwned = false;
        return changed;
    }

    @Override
    public void serverTick() {
        if (!isStolen()) {
            return;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            TimeTheftRuntime.tick(serverPlayer, this);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (stealerUuid != null) {
            tag.putUuid("Stealer", stealerUuid);
        }
        if (matchId != null) {
            tag.putUuid("Match", matchId);
        }
        tag.putLong("StolenAt", stolenAt);
        tag.putInt("Stage", stage);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        stealerUuid = tag.containsUuid("Stealer") ? tag.getUuid("Stealer") : null;
        matchId = tag.containsUuid("Match") ? tag.getUuid("Match") : null;
        stolenAt = Math.max(0L, tag.getLong("StolenAt"));
        stage = Math.clamp(tag.getInt("Stage"), 0, MAX_STORED_STAGE);
    }
}
