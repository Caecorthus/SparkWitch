package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Stable CCA contract {@code sparkwitch:sphinx_hao_world}: the round's 嘉豪病毒 infections and the running 豪气冲天
 * window.
 * <ul>
 *   <li>Infections (server truth): victim → who infected it, the world tick of its private notice, and whether the
 *   notice was shown. An infection lasts until a 豪气冲天 window ends, the victim dies, or the round resets.</li>
 *   <li>Window: the caster, the crawler set (caster included) and the absolute end tick. Every client needs it: the
 *   forced crawl pose and the instinct veto run on every side, and 《Fade》 plays for everyone (owner Q2).</li>
 * </ul>
 * Sync is per recipient: the window goes to everyone; the infected set only to a Sphinx Hao, a spectator or a creative
 * player (an infection is a secret until its notice). The services drive every change; this class only stores.
 * 稳定 CCA 契约 {@code sparkwitch:sphinx_hao_world}：本局的嘉豪病毒感染与进行中的豪气冲天窗口。
 * <ul>
 *   <li>感染（服务端为准）：被感染者 → 感染者、私人提示的世界刻、提示是否已显示。感染持续到豪气冲天窗口结束、被感染者死亡
 *   或换局。</li>
 *   <li>窗口：施法者、陆泳者集合（含施法者）与绝对结束刻。每个客户端都需要：强制陆泳姿势与本能屏蔽在各端运行，《Fade》对
 *   所有人播放（所有者 Q2）。</li>
 * </ul>
 * 同步按接收者区分：窗口发给所有人；感染集合只发给狮身豪、旁观者与创造模式玩家（提示出现前感染是秘密）。所有变更由服务驱动，
 * 本类只负责存储。
 */
public final class SphinxHaoWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<SphinxHaoWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SphinxHaoRules.ROLE_ID.withSuffixedPath("_world"), SphinxHaoWorldComponent.class);
    static final String INFECTIONS = "Infections";
    static final String VICTIM = "Victim";
    static final String INFECTOR = "Infector";
    static final String NOTICE_TICK = "NoticeTick";
    static final String NOTICED = "Noticed";
    static final String ROAR_CASTER = "RoarCaster";
    static final String CRAWLERS = "Crawlers";
    static final String ROAR_END_TICK = "RoarEndTick";

    /** One infection on the server. / 服务端的一条感染。 */
    public record Infection(UUID infector, long noticeTick, boolean noticed) {
    }

    private final World world;
    /** Server truth, in infection order. / 服务端真值，按感染顺序。 */
    private final Map<UUID, Infection> infections = new LinkedHashMap<>();
    /** Client copy of the infected set (empty for recipients that may not know it). / 客户端的感染集合副本。 */
    private final Set<UUID> visibleInfected = new LinkedHashSet<>();
    private @Nullable UUID roarCaster;
    private final Set<UUID> crawlers = new LinkedHashSet<>();
    private long roarEndTick;

    public SphinxHaoWorldComponent(World world) {
        this.world = world;
    }

    // ---- infections / 感染 ---------------------------------------------------------------------------------------

    /** Server: the truth; client: what this client was told. / 服务端：真值；客户端：本端收到的副本。 */
    public boolean isInfected(UUID player) {
        return world.isClient ? visibleInfected.contains(player) : infections.containsKey(player);
    }

    /** Server: the truth; client: what this client was told. Read-only. / 服务端：真值；客户端：收到的副本。只读。 */
    public Set<UUID> infectedIds() {
        return Collections.unmodifiableSet(world.isClient ? visibleInfected : infections.keySet());
    }

    /** Server only, read-only. / 仅服务端，只读。 */
    public Map<UUID, Infection> infections() {
        return Collections.unmodifiableMap(infections);
    }

    /**
     * Server only: infects {@code victim}; false (unchanged) when it already is.
     * 仅服务端：感染 {@code victim}；已被感染时返回 false 且不改变。
     */
    public boolean infect(UUID victim, UUID infector, long noticeTick) {
        if (infections.containsKey(victim)) {
            return false;
        }
        infections.put(victim, new Infection(infector, noticeTick, false));
        sync();
        return true;
    }

    /** Server only: the private notice was shown (never synced). / 仅服务端：私人提示已显示（不同步）。 */
    public void markNoticed(UUID victim) {
        infections.computeIfPresent(victim, (id, infection) -> new Infection(infection.infector(),
                infection.noticeTick(), true));
    }

    /** Server only: drops one infection (death). / 仅服务端：移除一条感染（死亡）。 */
    public boolean removeInfection(UUID victim) {
        if (infections.remove(victim) == null) {
            return false;
        }
        sync();
        return true;
    }

    /** Server only: clears every infection (a window ends, the round resets). / 仅服务端：清除全部感染。 */
    public void clearInfections() {
        if (infections.isEmpty()) {
            return;
        }
        infections.clear();
        sync();
    }

    // ---- 豪气冲天 window / 豪气冲天窗口 ----------------------------------------------------------------------------

    /** Whether a window runs at world time {@code now} (both sides). / 世界刻 {@code now} 时窗口是否进行中（两端）。 */
    public boolean isRoarActive(long now) {
        return roarCaster != null && roarEndTick > now;
    }

    public long roarEndTick() {
        return roarEndTick;
    }

    public @Nullable UUID roarCaster() {
        return roarCaster;
    }

    /** Read-only crawler set, caster included. / 只读的陆泳者集合，含施法者。 */
    public Set<UUID> crawlers() {
        return Collections.unmodifiableSet(crawlers);
    }

    /** Whether {@code player} is forced to crawl at {@code now} (both sides). / 该玩家此刻是否被强制陆泳（两端）。 */
    public boolean isCrawling(UUID player, long now) {
        return isRoarActive(now) && crawlers.contains(player);
    }

    /** Ticks of window left at {@code now}; 0 when none runs. / 窗口剩余刻数，未进行为 0。 */
    public int roarRemaining(long now) {
        return isRoarActive(now) ? (int) Math.min(roarEndTick - now, Integer.MAX_VALUE) : 0;
    }

    /** Server only: opens a window. / 仅服务端：开启窗口。 */
    public void startRoar(UUID caster, Collection<UUID> crawlerIds, long endTick) {
        roarCaster = caster;
        crawlers.clear();
        crawlers.add(caster);
        crawlers.addAll(crawlerIds);
        roarEndTick = endTick;
        sync();
    }

    /** Server only: one crawler leaves the window early (death, swallow, Rift). / 仅服务端：一名陆泳者提前退出。 */
    public boolean releaseCrawler(UUID player) {
        if (!crawlers.remove(player)) {
            return false;
        }
        sync();
        return true;
    }

    /** Server only: closes the window (infections are the caller's business). / 仅服务端：关闭窗口（感染由调用方处理）。 */
    public void endRoar() {
        if (roarCaster == null && crawlers.isEmpty() && roarEndTick == 0L) {
            return;
        }
        roarCaster = null;
        crawlers.clear();
        roarEndTick = 0L;
        sync();
    }

    /** Server only: clears everything (round start/stop). / 仅服务端：清空全部（回合开始/结束）。 */
    public void clear() {
        boolean hadState = !infections.isEmpty() || roarCaster != null || !crawlers.isEmpty() || roarEndTick != 0L;
        infections.clear();
        roarCaster = null;
        crawlers.clear();
        roarEndTick = 0L;
        if (hadState) {
            sync();
        }
    }

    /** Server only: re-sends the per-recipient view (e.g. after a role change). / 仅服务端：重发按接收者的视图。 */
    public void sync() {
        if (world instanceof ServerWorld) {
            KEY.sync(world);
        }
    }

    /**
     * Whether {@code recipient} may know who is infected: a Sphinx Hao, a spectator or a creative player.
     * 接收者是否可以知道谁被感染：狮身豪、旁观者或创造模式玩家。
     */
    static boolean mayKnowInfections(ServerPlayerEntity recipient) {
        return recipient.isSpectator() || recipient.isCreative() || SphinxHaoParticipation.isSphinxHao(recipient);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarLong(roarEndTick);
        buf.writeBoolean(roarCaster != null);
        if (roarCaster != null) {
            buf.writeUuid(roarCaster);
        }
        buf.writeVarInt(crawlers.size());
        for (UUID crawler : crawlers) {
            buf.writeUuid(crawler);
        }
        if (mayKnowInfections(recipient)) {
            buf.writeVarInt(infections.size());
            for (UUID victim : infections.keySet()) {
                buf.writeUuid(victim);
            }
        } else {
            buf.writeVarInt(0);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        roarEndTick = Math.max(0L, buf.readVarLong());
        roarCaster = buf.readBoolean() ? buf.readUuid() : null;
        crawlers.clear();
        int crawlerCount = buf.readVarInt();
        for (int i = 0; i < crawlerCount; i++) {
            crawlers.add(buf.readUuid());
        }
        visibleInfected.clear();
        int infectedCount = buf.readVarInt();
        for (int i = 0; i < infectedCount; i++) {
            visibleInfected.add(buf.readUuid());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        infections.clear();
        for (NbtElement element : tag.getList(INFECTIONS, NbtElement.COMPOUND_TYPE)) {
            if (element instanceof NbtCompound entry && entry.containsUuid(VICTIM) && entry.containsUuid(INFECTOR)) {
                infections.put(entry.getUuid(VICTIM), new Infection(entry.getUuid(INFECTOR),
                        Math.max(0L, entry.getLong(NOTICE_TICK)), entry.getBoolean(NOTICED)));
            }
        }
        roarCaster = tag.containsUuid(ROAR_CASTER) ? tag.getUuid(ROAR_CASTER) : null;
        crawlers.clear();
        for (NbtElement element : tag.getList(CRAWLERS, NbtElement.INT_ARRAY_TYPE)) {
            crawlers.add(NbtHelper.toUuid(element));
        }
        roarEndTick = Math.max(0L, tag.getLong(ROAR_END_TICK));
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (!infections.isEmpty()) {
            NbtList list = new NbtList();
            for (Map.Entry<UUID, Infection> entry : infections.entrySet()) {
                NbtCompound compound = new NbtCompound();
                compound.putUuid(VICTIM, entry.getKey());
                compound.putUuid(INFECTOR, entry.getValue().infector());
                compound.putLong(NOTICE_TICK, entry.getValue().noticeTick());
                compound.putBoolean(NOTICED, entry.getValue().noticed());
                list.add(compound);
            }
            tag.put(INFECTIONS, list);
        }
        if (roarCaster != null) {
            tag.putUuid(ROAR_CASTER, roarCaster);
        }
        if (!crawlers.isEmpty()) {
            NbtList list = new NbtList();
            for (UUID crawler : crawlers) {
                list.add(NbtHelper.fromUuid(crawler));
            }
            tag.put(CRAWLERS, list);
        }
        if (roarEndTick > 0L) {
            tag.putLong(ROAR_END_TICK, roarEndTick);
        }
    }
}
