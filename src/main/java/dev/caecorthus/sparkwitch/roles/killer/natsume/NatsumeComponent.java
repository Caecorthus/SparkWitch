package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.UUID;

/**
 * Stable CCA contract {@code sparkwitch:natsume_anan_player} (NEVER_COPY, appended last). Server-authoritative state,
 * all in absolute world ticks: the hypnosis end tick, its caster and world (on every hypnotised player, any role), and
 * 夏目安安's own cooldown ready tick. Only the shown seconds of both timers are synced, and only to the owner, whenever
 * a shown second changes; the client never predicts them. {@link #isHypnotised()} and {@link #isLocked()} read the
 * shown hypnosis second on both sides, so the item and skill locks and the HUD agree with the server. NBT keeps only
 * the cooldown across a relog; a relog, role assignment, Wathe's {@code ResetPlayer} and round end all clear the
 * hypnosis. The forced-cooldown store {@code sparkwitch:natsume_hypnosis} only ever raises the ready tick.
 * 稳定 CCA 契约 {@code sparkwitch:natsume_anan_player}（NEVER_COPY，追加在最后）。服务端权威状态均为绝对世界刻：催眠结束刻、
 * 施放者与所在世界（任何职业的被催眠者身上都有），以及夏目安安自己的冷却就绪刻。两个计时只同步“显示秒数”，且只同步给本人，
 * 在显示秒数变化时发送；客户端从不预测。{@link #isHypnotised()} 与 {@link #isLocked()} 在两端都读取显示的催眠秒数，因此道具与
 * 技能锁、HUD 与服务端一致。NBT 只在重新登录时保留冷却；重新登录、身份分配、Wathe 的 {@code ResetPlayer} 与回合结束都会清除
 * 催眠。强制冷却存储 {@code sparkwitch:natsume_hypnosis} 只会把就绪刻推后。
 */
public final class NatsumeComponent implements AutoSyncedComponent {
    public static final ComponentKey<NatsumeComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("natsume_anan_player"),
            NatsumeComponent.class
    );
    static final String COOLDOWN_READY_TICK = "CooldownReadyTick";

    private final PlayerEntity player;
    /** Server only. / 仅服务端。 */
    private long hypnosisEndTick;
    private @Nullable UUID hypnosisCaster;
    private @Nullable RegistryKey<World> hypnosisWorld;
    private long cooldownReadyTick;
    /** Shown seconds, synced to the owner. / 显示秒数，同步给本人。 */
    private int hypnosisSeconds;
    private int cooldownSeconds;

    public NatsumeComponent(PlayerEntity player) {
        this.player = player;
    }

    /**
     * Stable: whether this player is hypnotised right now (both sides; the client sees only its own).
     * 稳定接口：该玩家此刻是否被催眠（两端可用；客户端只能看到自己的）。
     */
    public boolean isHypnotised() {
        return hypnosisSeconds > 0;
    }

    /**
     * Stable: whether items and role skills are locked out. Owner decision: every hypnotised player is locked,
     * whatever their faction, so this equals {@link #isHypnotised()}.
     * 稳定接口：道具与职业技能是否被封锁。所有者决定：无论阵营，被催眠者一律被封锁，因此与 {@link #isHypnotised()} 相同。
     */
    public boolean isLocked() {
        return isHypnotised();
    }

    /** Stable: whole hypnosis seconds left, 0 when not hypnotised. / 稳定接口：剩余催眠整秒，未被催眠为 0。 */
    public int getHypnosisSeconds() {
        return hypnosisSeconds;
    }

    /** Stable: whole cooldown seconds left for 夏目安安's HUD, 0 when ready. / 稳定接口：夏目安安 HUD 的剩余冷却整秒，就绪为 0。 */
    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    /** Server only: whether a hypnosis window is stored. / 仅服务端：是否存有催眠窗口。 */
    boolean hasHypnosis() {
        return hypnosisEndTick > 0L;
    }

    long hypnosisEndTick() {
        return hypnosisEndTick;
    }

    @Nullable UUID hypnosisCaster() {
        return hypnosisCaster;
    }

    @Nullable RegistryKey<World> hypnosisWorld() {
        return hypnosisWorld;
    }

    /**
     * Server only: starts (or extends, never shortens) the hypnosis window ending at {@code endTick} and syncs the
     * shown second at once, so the locks hold from the cast onwards.
     * 仅服务端：开始（或延长，绝不缩短）在 {@code endTick} 结束的催眠窗口，并立即同步显示秒数，使封锁从施放起即生效。
     */
    void startHypnosis(UUID caster, RegistryKey<World> world, long endTick, long now) {
        hypnosisEndTick = Math.max(hypnosisEndTick, endTick);
        hypnosisCaster = caster;
        hypnosisWorld = world;
        refreshHypnosisSeconds(now);
    }

    /**
     * Server only: recomputes the shown hypnosis second at world time {@code now}; true (and synced) when it changed.
     * 仅服务端：按世界时间 {@code now} 重新计算显示的催眠秒数；有变化时返回 true 并同步。
     */
    boolean refreshHypnosisSeconds(long now) {
        int next = hasHypnosis() ? NatsumeRules.secondsLeft(hypnosisEndTick, now) : 0;
        if (next == hypnosisSeconds) {
            return false;
        }
        hypnosisSeconds = next;
        sync();
        return true;
    }

    /** Server only: drops the hypnosis window and syncs. / 仅服务端：清除催眠窗口并同步。 */
    void endHypnosis() {
        hypnosisEndTick = 0L;
        hypnosisCaster = null;
        hypnosisWorld = null;
        if (hypnosisSeconds != 0) {
            hypnosisSeconds = 0;
            sync();
        }
    }

    /** The absolute world tick at which hypnosis is ready again. / 催眠再次就绪的绝对世界刻。 */
    public long cooldownReadyTick() {
        return cooldownReadyTick;
    }

    /** Ticks until hypnosis is ready at world time {@code now}; 0 when ready. / 在世界时间 now 时距就绪的刻数，就绪为 0。 */
    public int cooldownRemainingTicks(long now) {
        return (int) Math.clamp(cooldownReadyTick - now, 0L, Integer.MAX_VALUE);
    }

    /**
     * Server only: moves the ready tick (also the forced-cooldown store's writer) and syncs the shown second when it
     * changes. / 仅服务端：设置就绪刻（也是强制冷却存储的写入口），显示秒数变化时同步。
     */
    public void setCooldownReadyTick(long tick) {
        cooldownReadyTick = Math.max(0L, tick);
        refreshCooldownSeconds(player.getWorld().getTime());
    }

    /** Server only: recomputes the shown cooldown second, syncing a change. / 仅服务端：重新计算显示的冷却秒数，有变化时同步。 */
    void refreshCooldownSeconds(long now) {
        int next = NatsumeRules.secondsLeft(cooldownReadyTick, now);
        if (next != cooldownSeconds) {
            cooldownSeconds = next;
            sync();
        }
    }

    /** Server only: clears the hypnosis and the cooldown. / 仅服务端：清除催眠与冷却。 */
    void clear() {
        endHypnosis();
        cooldownReadyTick = 0L;
        if (cooldownSeconds != 0) {
            cooldownSeconds = 0;
            sync();
        }
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
        buf.writeVarInt(cooldownSeconds);
        buf.writeVarInt(hypnosisSeconds);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownSeconds = Math.max(0, buf.readVarInt());
        hypnosisSeconds = Math.max(0, buf.readVarInt());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        // A relog never resumes a hypnosis; the cooldown survives it, like Noa's painting.
        // 重新登录绝不恢复催眠；冷却会保留，与诺亚作画相同。
        hypnosisEndTick = 0L;
        hypnosisCaster = null;
        hypnosisWorld = null;
        hypnosisSeconds = 0;
        cooldownReadyTick = Math.max(0L, tag.getLong(COOLDOWN_READY_TICK));
        cooldownSeconds = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        if (cooldownReadyTick > 0L) {
            tag.putLong(COOLDOWN_READY_TICK, cooldownReadyTick);
        }
    }
}
