package dev.caecorthus.sparkwitch.roles.neutral.koko;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Stable CCA contract {@code sparkwitch:koko_player} (NEVER_COPY). Server state: her traffic, three absolute world
 * ticks (when a running stream ends, when the shared cooldown is over, when a lightstick's doubling ends) and the
 * corpses she already streamed (the dead players' UUIDs, see {@code KokoStreamService.corpseKey}).
 * <p>Sync: to the owner only, as traffic plus the stream-end and ready ticks, which her HUD, her instinct outline and
 * her predicted sprint drain count down against the client world clock (the Noa precedent). Nobody else receives
 * anything: other players see her through the vanilla Glowing effect, never through this component. NBT keeps
 * everything across a mid-round relog; role assignment and Wathe's {@code ResetPlayer} clear it. The forced-cooldown
 * store {@code sparkwitch:koko_stream} only ever moves the ready tick.
 * 稳定 CCA 契约 {@code sparkwitch:koko_player}（NEVER_COPY）。服务端状态：流量、三个绝对世界刻（进行中的直播何时结束、共用冷却
 * 何时结束、应援棒翻倍何时结束），以及她已直播过的尸体（死者的 UUID，见 {@code KokoStreamService.corpseKey}）。
 * <p>同步：只发给本人，内容为流量、直播结束刻与就绪刻；她的 HUD、本能描边与体力消耗预测都依据客户端世界时钟倒数（沿用诺亚的
 * 先例）。其他人收不到任何数据：别人是通过原版发光效果看到她的，从不经过本组件。NBT 使全部状态在局中重新登录后保留；身份分配与
 * Wathe 的 {@code ResetPlayer} 会清空。强制冷却存储 {@code sparkwitch:koko_stream} 只会移动就绪刻。
 */
public final class KokoComponent implements AutoSyncedComponent {
    public static final ComponentKey<KokoComponent> KEY = ComponentRegistry.getOrCreate(
            KokoRules.ROLE_ID.withSuffixedPath("_player"), KokoComponent.class);
    static final String TRAFFIC = "Traffic";
    static final String STREAM_END_TICK = "StreamEndTick";
    static final String READY_TICK = "ReadyTick";
    static final String CHEER_END_TICK = "CheerEndTick";
    static final String STREAMED_BODIES = "StreamedBodies";

    private final PlayerEntity player;
    private int traffic;
    private long streamEndTick;
    private long readyTick;
    private long cheerEndTick;
    /** Server only. / 仅服务端。 */
    private final Set<UUID> streamedBodies = new HashSet<>();

    public KokoComponent(PlayerEntity player) {
        this.player = player;
    }

    public int traffic() {
        return traffic;
    }

    /** True while her stream runs, on the server and on her own client. / 直播是否进行中（服务端与她自己的客户端）。 */
    public boolean isLive() {
        return streamEndTick > player.getWorld().getTime();
    }

    public long streamEndTick() {
        return streamEndTick;
    }

    public long readyTick() {
        return readyTick;
    }

    /** Ticks of stream left at world time {@code now}; 0 when not live. / 直播剩余刻数，未直播为 0。 */
    public int streamRemaining(long now) {
        return (int) Math.clamp(streamEndTick - now, 0L, KokoRules.STREAM_TICKS);
    }

    /** Ticks until the shared cooldown is over at world time {@code now}; 0 when ready. / 距共用冷却结束的刻数，就绪为 0。 */
    public int cooldownRemaining(long now) {
        return (int) Math.clamp(readyTick - now, 0L, Integer.MAX_VALUE);
    }

    /** Server only: a lightstick's doubling runs at {@code now}. / 仅服务端：应援棒翻倍是否生效。 */
    public boolean isCheering(long now) {
        return cheerEndTick > now;
    }

    /** Server only. / 仅服务端。 */
    public boolean hasStreamed(UUID body) {
        return streamedBodies.contains(body);
    }

    /** Server only: adds traffic (never below 0) and tells the owner. / 仅服务端：增加流量（不低于 0）并通知本人。 */
    public void addTraffic(int amount) {
        int next = Math.max(0, traffic + amount);
        if (next != traffic) {
            traffic = next;
            sync();
        }
    }

    /**
     * Server only: takes {@code amount} traffic if she holds that much; false and unchanged otherwise.
     * 仅服务端：流量足够时扣除 {@code amount}；否则返回 false 且不改变任何东西。
     */
    public boolean spendTraffic(int amount) {
        if (amount < 0 || traffic < amount) {
            return false;
        }
        addTraffic(-amount);
        return true;
    }

    /** Server only (also the forced-cooldown store's setter): moves the ready tick. / 仅服务端（亦为强制冷却存储的写入口）。 */
    public void setReadyTick(long tick) {
        long next = Math.max(0L, tick);
        if (next != readyTick) {
            readyTick = next;
            sync();
        }
    }

    /**
     * Server only: goes live at {@code now}; the cooldown is counted from the stream's end (the Hanna float rule).
     * 仅服务端：在 {@code now} 开播；冷却从直播结束起算（与汉娜浮游相同的规则）。
     */
    public void startStream(long now) {
        streamEndTick = now + KokoRules.STREAM_TICKS;
        readyTick = Math.max(readyTick, streamEndTick + KokoRules.COOLDOWN_TICKS);
        sync();
    }

    /**
     * Server only: cuts a running stream short at {@code now} and restarts the cooldown from that tick. Returns whether
     * a stream was running.
     * 仅服务端：在 {@code now} 提前结束进行中的直播，并从该刻重新开始冷却。返回此前是否在直播。
     */
    public boolean endStream(long now) {
        if (streamEndTick <= now) {
            return false;
        }
        streamEndTick = 0L;
        readyTick = now + KokoRules.COOLDOWN_TICKS;
        sync();
        return true;
    }

    /**
     * Server only: forgets a stream that ran out, without touching the cooldown. Returns whether one just ended.
     * 仅服务端：清掉已自然结束的直播，不改动冷却。返回是否刚好有一场直播结束。
     */
    public boolean expireStream(long now) {
        if (streamEndTick > 0L && streamEndTick <= now) {
            streamEndTick = 0L;
            return true;
        }
        return false;
    }

    /** Server only: a streamed corpse starts the shared cooldown and is remembered. / 仅服务端：尸体直播开始共用冷却并被记住。 */
    public void recordCorpseStream(UUID body, long now) {
        streamedBodies.add(body);
        readyTick = now + KokoRules.COOLDOWN_TICKS;
        traffic = Math.max(0, traffic + KokoRules.CORPSE_TRAFFIC);
        sync();
    }

    /** Server only: starts a lightstick's doubling window (never synced). / 仅服务端：开始应援棒翻倍窗口（不同步）。 */
    public void startCheer(long now) {
        cheerEndTick = now + KokoRules.LIGHTSTICK_BUFF_TICKS;
    }

    /** Server only: nothing of hers is set, so the per-tick service can skip this player. / 仅服务端：没有任何状态。 */
    public boolean idle() {
        return traffic == 0 && streamEndTick <= 0L && readyTick <= 0L && cheerEndTick <= 0L;
    }

    /** Server only: clears everything (role assignment, round reset). / 仅服务端：清空全部状态（身份分配、换局）。 */
    public void clear() {
        boolean hadState = !idle();
        traffic = 0;
        streamEndTick = 0L;
        readyTick = 0L;
        cheerEndTick = 0L;
        streamedBodies.clear();
        if (hadState) {
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
        // Owner only: her traffic and timers are nobody else's to read. / 只发给本人：流量与计时不给其他人读取。
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(traffic);
        buf.writeVarLong(streamEndTick);
        buf.writeVarLong(readyTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        traffic = Math.max(0, buf.readVarInt());
        streamEndTick = Math.max(0L, buf.readVarLong());
        readyTick = Math.max(0L, buf.readVarLong());
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        traffic = Math.max(0, tag.getInt(TRAFFIC));
        streamEndTick = Math.max(0L, tag.getLong(STREAM_END_TICK));
        readyTick = Math.max(0L, tag.getLong(READY_TICK));
        cheerEndTick = Math.max(0L, tag.getLong(CHEER_END_TICK));
        streamedBodies.clear();
        for (NbtElement element : tag.getList(STREAMED_BODIES, NbtElement.INT_ARRAY_TYPE)) {
            streamedBodies.add(NbtHelper.toUuid(element));
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (traffic > 0) {
            tag.putInt(TRAFFIC, traffic);
        }
        if (streamEndTick > 0L) {
            tag.putLong(STREAM_END_TICK, streamEndTick);
        }
        if (readyTick > 0L) {
            tag.putLong(READY_TICK, readyTick);
        }
        if (cheerEndTick > 0L) {
            tag.putLong(CHEER_END_TICK, cheerEndTick);
        }
        if (!streamedBodies.isEmpty()) {
            NbtList bodies = new NbtList();
            for (UUID body : streamedBodies) {
                bodies.add(NbtHelper.fromUuid(body));
            }
            tag.put(STREAMED_BODIES, bodies);
        }
    }
}
