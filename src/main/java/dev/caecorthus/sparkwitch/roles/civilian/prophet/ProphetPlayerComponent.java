package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Owner-private Prophet state ({@code sparkwitch:prophet_player}, {@code NEVER_COPY}): the passive Death Sense
 * countdown, the permanent corpse highlight set, the necrology, and Prophecy records. It syncs only to its owner,
 * is bound to one Wathe match id, and ticks itself on the server outside {@code WitchPlayerComponent}'s tick order.
 * 仅所有者可见的先知状态（{@code sparkwitch:prophet_player}，{@code NEVER_COPY}）：被动死亡感知倒计时、永久尸体高亮集合、
 * 亡者名录与预言记录。只同步给拥有者，绑定单个 Wathe 对局 id，并在服务端自行 tick，不进入 {@code WitchPlayerComponent}
 * 的 tick 顺序。
 */
public final class ProphetPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ProphetPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("prophet_player"),
            ProphetPlayerComponent.class
    );

    /** One necrology line, in first-seen order. / 名录中的一行，按首次发现顺序排列。 */
    public record NecrologyEntry(UUID player, String name) {
        public NecrologyEntry {
            Objects.requireNonNull(player, "player");
            name = ProphetPlayerState.truncate(name);
        }
    }

    /**
     * One victim's Prophecy history: excluded cause groups and, once solved, the locked outcome. {@code deathSerial}
     * is the ledger serial of the death these guesses were made against ({@link ProphetDeathRecord#serial()}); when the
     * victim's current death has a different serial, the record is stale and the server treats it as fresh.
     * 某名死者的预言记录：已排除的死因分组，以及猜中后锁定的结果。{@code deathSerial} 是这些猜测所针对那次死亡的账本序号
     * （{@link ProphetDeathRecord#serial()}）；死者当前死亡的序号不同时，该记录已过期，服务端将其视为全新记录。
     */
    public record ProphecyRecord(
            String victimName,
            Set<ProphetDeathCauseGroup> excluded,
            Outcome outcome,
            @Nullable String killerName,
            long deathSerial
    ) {
        public ProphecyRecord {
            victimName = ProphetPlayerState.truncate(victimName);
            EnumSet<ProphetDeathCauseGroup> copy = EnumSet.noneOf(ProphetDeathCauseGroup.class);
            if (excluded != null) {
                excluded.stream().filter(Objects::nonNull).forEach(copy::add);
            }
            excluded = Collections.unmodifiableSet(copy);
            outcome = outcome == null ? Outcome.PENDING : outcome;
        }

        /** A record not yet tied to a ledger death (serial 0). / 尚未关联账本死亡的记录（序号 0）。 */
        public ProphecyRecord(String victimName, Set<ProphetDeathCauseGroup> excluded, Outcome outcome,
                              @Nullable String killerName) {
            this(victimName, excluded, outcome, killerName, 0L);
        }

        public enum Outcome {
            PENDING,
            REVEALED_KILLER,
            NO_KILLER
        }
    }

    private final PlayerEntity player;
    private final ProphetPlayerState state = new ProphetPlayerState();

    public ProphetPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    /** Remaining ticks until the next Death Sense pulse (client-predicted between syncs). / 距下次死亡感知的剩余 tick（同步间隔内由客户端预测）。 */
    public int senseRemainingTicks() {
        return state.senseRemainingTicks();
    }

    /** Whether the passive countdown is armed and currently advancing. / 被动倒计时是否已启用且正在推进。 */
    public boolean isSenseRunning() {
        return state.isSenseArmed() && state.isSenseRunning();
    }

    public boolean isSenseBody(@Nullable UUID bodyEntityUuid) {
        return state.isSenseBody(bodyEntityUuid);
    }

    public Set<UUID> senseBodyUuids() {
        return state.senseBodyUuids();
    }

    public List<NecrologyEntry> necrology() {
        return state.necrology();
    }

    public Optional<ProphecyRecord> prophecy(@Nullable UUID victim) {
        return state.prophecy(victim);
    }

    public Map<UUID, ProphecyRecord> prophecies() {
        return state.prophecies();
    }

    /** Server only. / 仅服务端。 */
    public @Nullable UUID matchId() {
        return state.matchId();
    }

    public boolean hasState() {
        return !state.isEmpty();
    }

    /**
     * Server write API for the Prophecy flow; syncs the owner when changed. {@code deathSerial} is the victim's current
     * ledger serial: a record made against another death is replaced, not extended.
     * 预言流程的服务端写入接口；变化时同步给拥有者。{@code deathSerial} 为死者当前的账本序号：针对其他死亡的记录会被替换而非累加。
     */
    public boolean recordWrongGuess(UUID victim, String victimName, ProphetDeathCauseGroup group, long deathSerial) {
        boolean changed = state.recordWrongGuess(victim, victimName, group, deathSerial);
        if (changed) {
            syncOwner();
        }
        return changed;
    }

    /** Server write API; {@code killerName == null} records "no killer". / 服务端写入接口；{@code killerName == null} 表示无人行凶。 */
    public boolean recordCorrectGuess(UUID victim, String victimName, @Nullable String killerName, long deathSerial) {
        boolean changed = state.recordCorrectGuess(victim, victimName, killerName, deathSerial);
        if (changed) {
            syncOwner();
        }
        return changed;
    }

    /**
     * Server only: drops records whose victim has died again since (serial changed), then syncs once, so the client
     * never shows exclusions or a solved outcome for a fresh death.
     * 仅服务端：删除死者此后再次死亡（序号变化）的记录，并只同步一次，确保客户端不会为新的死亡显示旧的排除项或已猜中结果。
     */
    public boolean forgetStaleProphecies(Map<UUID, Long> currentDeathSerials) {
        boolean changed = state.forgetStaleProphecies(currentDeathSerials);
        if (changed) {
            syncOwner();
        }
        return changed;
    }

    boolean isSenseArmed() {
        return state.isSenseArmed();
    }

    void arm() {
        state.arm(ProphetRules.SENSE_INTERVAL_TICKS);
        syncOwner();
    }

    boolean bindMatch(@Nullable UUID currentMatch) {
        UUID previous = state.matchId();
        boolean changed = state.bindMatch(currentMatch);
        if (changed && previous != null) {
            syncOwner();
        }
        return changed;
    }

    ProphetPlayerState.TickOutcome tickSense(boolean eligible) {
        ProphetPlayerState.TickOutcome outcome = state.tickSense(eligible, ProphetRules.SENSE_INTERVAL_TICKS);
        if (outcome == ProphetPlayerState.TickOutcome.SYNC) {
            syncOwner();
        }
        return outcome;
    }

    /** Applies one pulse snapshot and always syncs the owner so the countdown restarts visibly. / 写入一次感知快照，并总是同步以显示倒计时重新开始。 */
    void recordPulse(List<UUID> bodyUuids, List<NecrologyEntry> owners) {
        state.recordPulse(bodyUuids, owners);
        syncOwner();
    }

    public void clear() {
        if (state.isEmpty()) {
            return;
        }
        state.clear();
        syncOwner();
    }

    public void syncOwner() {
        if (player != null) {
            KEY.sync(player);
        }
    }

    // Owner-only: the necrology and Prophecy results would leak deaths and killers to anyone else.
    // 仅同步给拥有者：名录与预言结果会向他人泄露死亡与凶手信息。
    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ProphetRuntime.tick(serverPlayer, this);
        }
    }

    @Override
    public void clientTick() {
        state.tickClientCountdown();
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
