package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.NecrologyEntry;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * Pure, match-bound Prophet state behind {@link ProphetPlayerComponent}: the passive Death Sense countdown, the
 * permanent highlight set, the necrology, and Prophecy records. It never touches the world.
 * {@link ProphetPlayerComponent} 背后的纯先知状态（绑定对局）：被动死亡感知倒计时、永久高亮集合、亡者名录与预言记录；
 * 本类从不访问世界。
 */
public final class ProphetPlayerState {
    /** Caps every stored and synced collection so a corrupt save or packet cannot grow without bound. / 限制所有存储与同步集合的大小，避免损坏存档或数据包无限增长。 */
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_GROUP_ID_LENGTH = 32;

    private @Nullable UUID matchId;
    private boolean senseArmed;
    private boolean senseRunning;
    private int senseRemainingTicks;
    // Only grows within one match: bodies are never removed, a despawned body simply stops rendering.
    // 同一对局内只增不减：尸体从不移除，实体消失后自然不再显示。
    private final Set<UUID> senseBodies = new LinkedHashSet<>();
    private final Map<UUID, String> necrology = new LinkedHashMap<>();
    private final Map<UUID, ProphecyRecord> prophecies = new LinkedHashMap<>();

    public enum TickOutcome {
        NONE,
        SYNC,
        PULSE
    }

    public @Nullable UUID matchId() {
        return matchId;
    }

    public boolean isSenseArmed() {
        return senseArmed;
    }

    public boolean isSenseRunning() {
        return senseRunning;
    }

    public int senseRemainingTicks() {
        return senseRemainingTicks;
    }

    public boolean isSenseBody(@Nullable UUID bodyUuid) {
        return bodyUuid != null && senseBodies.contains(bodyUuid);
    }

    public Set<UUID> senseBodyUuids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(senseBodies));
    }

    public List<NecrologyEntry> necrology() {
        List<NecrologyEntry> entries = new ArrayList<>(necrology.size());
        necrology.forEach((player, name) -> entries.add(new NecrologyEntry(player, name)));
        return List.copyOf(entries);
    }

    public Optional<ProphecyRecord> prophecy(@Nullable UUID victim) {
        return victim == null ? Optional.empty() : Optional.ofNullable(prophecies.get(victim));
    }

    public Map<UUID, ProphecyRecord> prophecies() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(prophecies));
    }

    public boolean isEmpty() {
        return matchId == null && !senseArmed && !senseRunning && senseRemainingTicks == 0
                && senseBodies.isEmpty() && necrology.isEmpty() && prophecies.isEmpty();
    }

    /**
     * Starts a fresh Prophet assignment: every earlier record is dropped and the first pulse is one interval away.
     * 开始一次新的先知分配：丢弃此前所有记录，首次感知在一个周期之后。
     */
    public void arm(int intervalTicks) {
        clear();
        senseArmed = true;
        senseRemainingTicks = Math.max(1, intervalTicks);
    }

    /**
     * True when the state is bound to a match that is no longer current (a new match, or no match at all).
     * 当状态绑定的对局已不是当前对局（新对局或已无对局）时为真。
     */
    public static boolean isStale(@Nullable UUID storedMatch, @Nullable UUID currentMatch) {
        return storedMatch != null && !storedMatch.equals(currentMatch);
    }

    /**
     * Binds the state to one match; a different non-null id from an already bound match drops everything.
     * 将状态绑定到某个对局；已绑定时遇到不同的非空对局 id 会清空全部状态。
     */
    public boolean bindMatch(@Nullable UUID currentMatch) {
        if (currentMatch == null || currentMatch.equals(matchId)) {
            return false;
        }
        if (matchId != null) {
            clear();
        }
        matchId = currentMatch;
        return true;
    }

    /**
     * Advances the passive countdown only while {@code eligible}; a running-flag change asks for an owner sync, and
     * reaching zero restarts the interval and reports a pulse.
     * 仅在 {@code eligible} 时推进被动倒计时；运行标记变化时请求同步，归零时重新开始周期并报告一次感知。
     */
    public TickOutcome tickSense(boolean eligible, int intervalTicks) {
        if (!senseArmed) {
            if (senseRunning) {
                senseRunning = false;
                return TickOutcome.SYNC;
            }
            return TickOutcome.NONE;
        }
        boolean runningChanged = senseRunning != eligible;
        senseRunning = eligible;
        if (!eligible) {
            return runningChanged ? TickOutcome.SYNC : TickOutcome.NONE;
        }
        senseRemainingTicks = Math.max(0, senseRemainingTicks - 1);
        if (senseRemainingTicks == 0) {
            senseRemainingTicks = Math.max(1, intervalTicks);
            return TickOutcome.PULSE;
        }
        return runningChanged ? TickOutcome.SYNC : TickOutcome.NONE;
    }

    /** Client-side prediction between owner syncs. / 两次所有者同步之间的客户端预测。 */
    public void tickClientCountdown() {
        if (senseArmed && senseRunning && senseRemainingTicks > 0) {
            senseRemainingTicks--;
        }
    }

    /**
     * Records one pulse snapshot: body entity ids join the permanent highlight set and owners join the necrology in
     * first-seen order, never duplicated. Returns whether anything new was added.
     * 记录一次感知快照：尸体实体 id 加入永久高亮集合，尸体主人按首次发现顺序加入名录且不重复；返回是否有新增。
     */
    public boolean recordPulse(List<UUID> bodyUuids, List<NecrologyEntry> owners) {
        boolean changed = false;
        for (UUID bodyUuid : bodyUuids) {
            if (bodyUuid != null && senseBodies.size() < MAX_ENTRIES && senseBodies.add(bodyUuid)) {
                changed = true;
            }
        }
        for (NecrologyEntry owner : owners) {
            if (owner != null && necrology.size() < MAX_ENTRIES && !necrology.containsKey(owner.player())) {
                necrology.put(owner.player(), owner.name());
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Adds one excluded cause group to a pending Prophecy record; a resolved record is locked.
     * 为未猜中的预言记录添加一个已排除的死因分组；已猜中的记录被锁定。
     */
    public boolean recordWrongGuess(UUID victim, String victimName, ProphetDeathCauseGroup group, long deathSerial) {
        if (victim == null || group == null) {
            return false;
        }
        ProphecyRecord stored = prophecies.get(victim);
        if (stored == null && prophecies.size() >= MAX_ENTRIES) {
            return false;
        }
        ProphecyRecord existing = sameDeath(stored, deathSerial);
        if (existing != null && existing.outcome() != ProphecyRecord.Outcome.PENDING) {
            return false;
        }
        EnumSet<ProphetDeathCauseGroup> excluded = EnumSet.noneOf(ProphetDeathCauseGroup.class);
        if (existing != null) {
            excluded.addAll(existing.excluded());
        }
        excluded.add(group);
        prophecies.put(victim, new ProphecyRecord(
                safeName(victimName, stored), excluded, ProphecyRecord.Outcome.PENDING, null, deathSerial));
        return true;
    }

    /**
     * Locks a Prophecy record as solved; a {@code null} killer means nobody was responsible.
     * 将预言记录锁定为已猜中；{@code null} 凶手表示无人行凶。
     */
    public boolean recordCorrectGuess(UUID victim, String victimName, @Nullable String killerName, long deathSerial) {
        if (victim == null) {
            return false;
        }
        ProphecyRecord stored = prophecies.get(victim);
        if (stored == null && prophecies.size() >= MAX_ENTRIES) {
            return false;
        }
        ProphecyRecord existing = sameDeath(stored, deathSerial);
        if (existing != null && existing.outcome() != ProphecyRecord.Outcome.PENDING) {
            return false;
        }
        Set<ProphetDeathCauseGroup> excluded = existing == null ? Set.of() : existing.excluded();
        String killer = killerName == null ? null : truncate(killerName);
        prophecies.put(victim, new ProphecyRecord(
                safeName(victimName, stored),
                excluded,
                killer == null ? ProphecyRecord.Outcome.NO_KILLER : ProphecyRecord.Outcome.REVEALED_KILLER,
                killer,
                deathSerial
        ));
        return true;
    }

    /**
     * Removes every record whose victim appears in {@code currentDeathSerials} with a different serial (the victim was
     * revived and died again). Victims absent from the map are left untouched. Returns whether anything was removed.
     * 删除所有在 {@code currentDeathSerials} 中序号不同的记录（死者被复活后再次死亡）；不在映射中的死者保持不变。返回是否有删除。
     */
    public boolean forgetStaleProphecies(Map<UUID, Long> currentDeathSerials) {
        if (currentDeathSerials == null || currentDeathSerials.isEmpty()) {
            return false;
        }
        return prophecies.entrySet().removeIf(entry -> {
            Long current = currentDeathSerials.get(entry.getKey());
            return current != null && current != entry.getValue().deathSerial();
        });
    }

    /** The stored record only when it was made against this same death. / 仅当已存记录针对同一次死亡时才返回它。 */
    private static @Nullable ProphecyRecord sameDeath(@Nullable ProphecyRecord stored, long deathSerial) {
        return stored != null && stored.deathSerial() == deathSerial ? stored : null;
    }

    public void clear() {
        matchId = null;
        senseArmed = false;
        senseRunning = false;
        senseRemainingTicks = 0;
        senseBodies.clear();
        necrology.clear();
        prophecies.clear();
    }

    public void writeNbt(NbtCompound tag) {
        if (matchId != null) {
            tag.putUuid("Match", matchId);
        }
        tag.putBoolean("SenseArmed", senseArmed);
        tag.putInt("SenseTicks", senseRemainingTicks);
        NbtList bodies = new NbtList();
        senseBodies.forEach(body -> bodies.add(NbtHelper.fromUuid(body)));
        tag.put("SenseBodies", bodies);
        NbtList names = new NbtList();
        necrology.forEach((player, name) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Player", player);
            entry.putString("Name", name);
            names.add(entry);
        });
        tag.put("Necrology", names);
        NbtList records = new NbtList();
        prophecies.forEach((victim, record) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Victim", victim);
            entry.putString("VictimName", record.victimName());
            entry.putString("Outcome", record.outcome().name());
            NbtList excluded = new NbtList();
            record.excluded().forEach(group -> excluded.add(NbtString.of(group.id())));
            entry.put("Excluded", excluded);
            if (record.killerName() != null) {
                entry.putString("KillerName", record.killerName());
            }
            entry.putLong("DeathSerial", record.deathSerial());
            records.add(entry);
        });
        tag.put("Prophecies", records);
    }

    public void readNbt(NbtCompound tag) {
        clear();
        matchId = tag.containsUuid("Match") ? tag.getUuid("Match") : null;
        senseArmed = tag.getBoolean("SenseArmed");
        senseRemainingTicks = senseArmed && tag.contains("SenseTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(1, tag.getInt("SenseTicks"))
                : 0;
        NbtList bodies = tag.getList("SenseBodies", NbtElement.INT_ARRAY_TYPE);
        for (int index = 0; index < bodies.size() && senseBodies.size() < MAX_ENTRIES; index++) {
            try {
                senseBodies.add(NbtHelper.toUuid(bodies.get(index)));
            } catch (IllegalArgumentException ignored) {
                // Malformed saved UUIDs are skipped so stale data cannot outline an arbitrary entity.
                // 跳过损坏的存档 UUID，避免旧数据描边任意实体。
            }
        }
        NbtList names = tag.getList("Necrology", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < names.size() && necrology.size() < MAX_ENTRIES; index++) {
            NbtCompound entry = names.getCompound(index);
            if (entry.containsUuid("Player")) {
                necrology.putIfAbsent(entry.getUuid("Player"), truncate(entry.getString("Name")));
            }
        }
        NbtList records = tag.getList("Prophecies", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < records.size() && prophecies.size() < MAX_ENTRIES; index++) {
            NbtCompound entry = records.getCompound(index);
            if (!entry.containsUuid("Victim")) {
                continue;
            }
            EnumSet<ProphetDeathCauseGroup> excluded = EnumSet.noneOf(ProphetDeathCauseGroup.class);
            NbtList groups = entry.getList("Excluded", NbtElement.STRING_TYPE);
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                ProphetDeathCauseGroup.byId(groups.getString(groupIndex)).ifPresent(excluded::add);
            }
            String killer = entry.contains("KillerName", NbtElement.STRING_TYPE)
                    ? truncate(entry.getString("KillerName"))
                    : null;
            prophecies.put(entry.getUuid("Victim"), new ProphecyRecord(
                    truncate(entry.getString("VictimName")),
                    excluded,
                    readOutcome(entry.getString("Outcome"), killer),
                    killer,
                    // Missing in older saves → 0, which never matches a ledger serial, so the record reads as stale.
                    // 旧存档缺失时为 0，永不匹配账本序号，因此该记录视为过期。
                    entry.getLong("DeathSerial")
            ));
        }
    }

    /**
     * Owner-only sync layout: armed, running, remaining ticks, body UUIDs, necrology (UUID, name), then Prophecy
     * records (victim, name, outcome ordinal, excluded group ids, optional killer name, death serial). The match id
     * stays server-side; the serial carries no cause or killer.
     * 仅所有者同步布局：armed、running、剩余 tick、尸体 UUID、名录（UUID、名字），再到预言记录（受害者、名字、结果序号、
     * 已排除分组 id、可选凶手名、死亡序号）。对局 id 只保留在服务端；序号不含死因或凶手。
     */
    public void writeSync(RegistryByteBuf buf) {
        buf.writeBoolean(senseArmed);
        buf.writeBoolean(senseRunning);
        buf.writeVarInt(senseRemainingTicks);
        List<UUID> bodies = List.copyOf(senseBodies);
        int bodyCount = Math.min(MAX_ENTRIES, bodies.size());
        buf.writeVarInt(bodyCount);
        for (int index = 0; index < bodyCount; index++) {
            buf.writeUuid(bodies.get(index));
        }
        List<NecrologyEntry> entries = necrology();
        int entryCount = Math.min(MAX_ENTRIES, entries.size());
        buf.writeVarInt(entryCount);
        for (int index = 0; index < entryCount; index++) {
            buf.writeUuid(entries.get(index).player());
            buf.writeString(entries.get(index).name(), MAX_NAME_LENGTH);
        }
        List<Map.Entry<UUID, ProphecyRecord>> records = List.copyOf(prophecies.entrySet());
        int recordCount = Math.min(MAX_ENTRIES, records.size());
        buf.writeVarInt(recordCount);
        for (int index = 0; index < recordCount; index++) {
            UUID victim = records.get(index).getKey();
            ProphecyRecord record = records.get(index).getValue();
            buf.writeUuid(victim);
            buf.writeString(record.victimName(), MAX_NAME_LENGTH);
            buf.writeVarInt(record.outcome().ordinal());
            buf.writeVarInt(record.excluded().size());
            record.excluded().forEach(group -> buf.writeString(group.id(), MAX_GROUP_ID_LENGTH));
            buf.writeBoolean(record.killerName() != null);
            if (record.killerName() != null) {
                buf.writeString(record.killerName(), MAX_NAME_LENGTH);
            }
            buf.writeVarLong(record.deathSerial());
        }
    }

    public void readSync(RegistryByteBuf buf) {
        UUID keptMatch = matchId;
        clear();
        matchId = keptMatch;
        senseArmed = buf.readBoolean();
        senseRunning = buf.readBoolean();
        senseRemainingTicks = Math.max(0, buf.readVarInt());
        int bodyCount = Math.clamp(buf.readVarInt(), 0, MAX_ENTRIES);
        for (int index = 0; index < bodyCount; index++) {
            senseBodies.add(buf.readUuid());
        }
        int entryCount = Math.clamp(buf.readVarInt(), 0, MAX_ENTRIES);
        for (int index = 0; index < entryCount; index++) {
            UUID player = buf.readUuid();
            necrology.putIfAbsent(player, buf.readString(MAX_NAME_LENGTH));
        }
        int recordCount = Math.clamp(buf.readVarInt(), 0, MAX_ENTRIES);
        ProphecyRecord.Outcome[] outcomes = ProphecyRecord.Outcome.values();
        for (int index = 0; index < recordCount; index++) {
            UUID victim = buf.readUuid();
            String victimName = buf.readString(MAX_NAME_LENGTH);
            int outcomeIndex = buf.readVarInt();
            int excludedCount = Math.clamp(buf.readVarInt(), 0, ProphetDeathCauseGroup.values().length);
            EnumSet<ProphetDeathCauseGroup> excluded = EnumSet.noneOf(ProphetDeathCauseGroup.class);
            for (int groupIndex = 0; groupIndex < excludedCount; groupIndex++) {
                ProphetDeathCauseGroup.byId(buf.readString(MAX_GROUP_ID_LENGTH)).ifPresent(excluded::add);
            }
            String killer = buf.readBoolean() ? buf.readString(MAX_NAME_LENGTH) : null;
            long deathSerial = buf.readVarLong();
            ProphecyRecord.Outcome outcome = outcomeIndex >= 0 && outcomeIndex < outcomes.length
                    ? outcomes[outcomeIndex]
                    : ProphecyRecord.Outcome.PENDING;
            prophecies.put(victim, new ProphecyRecord(victimName, excluded, outcome, killer, deathSerial));
        }
    }

    private static ProphecyRecord.Outcome readOutcome(String name, @Nullable String killer) {
        for (ProphecyRecord.Outcome outcome : ProphecyRecord.Outcome.values()) {
            if (outcome.name().equals(name)) {
                return outcome == ProphecyRecord.Outcome.REVEALED_KILLER && killer == null
                        ? ProphecyRecord.Outcome.NO_KILLER
                        : outcome;
            }
        }
        return ProphecyRecord.Outcome.PENDING;
    }

    private static String safeName(@Nullable String name, @Nullable ProphecyRecord existing) {
        if (name != null && !name.isBlank()) {
            return truncate(name);
        }
        return existing == null ? "" : existing.victimName();
    }

    static String truncate(@Nullable String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
