package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative model behind {@code sparkwitch:black_raven_disguise}; the NBT key names below are a
 * stable save contract. Stashes never leave the server.
 * sparkwitch:black_raven_disguise 背后的服务端权威模型；下列 NBT 键名是稳定的存档契约。存档从不离开服务端。
 */
public final class BlackRavenDisguiseState {
    public static final String KEY_MATCH_ID = "MatchId";
    public static final String KEY_ACTING = "Acting";
    public static final String KEY_ROUND_START_AT = "RoundStartAt";
    public static final String KEY_UNLOCK_AT = "UnlockAt";
    public static final String KEY_NEXT_SWITCH_AT = "NextSwitchAt";
    public static final String KEY_LAST_CHANGE_AT = "LastChangeAt";
    public static final String KEY_POOL = "Pool";
    public static final String KEY_POOL_ID = "Id";
    public static final String KEY_POOL_FLAG = "Flag";
    public static final String KEY_VISITED = "Visited";
    public static final String KEY_IDENTITIES = "Identities";
    public static final String KEY_STASH_SLOTS = "Slots";
    public static final String KEY_STASH_SLOT = "Slot";
    public static final String KEY_STASH_ITEM = "Item";
    public static final String KEY_STASH_OVERFLOW = "Overflow";
    public static final String KEY_STASH_ABILITY_CD_UNTIL = "AbilityCdUntil";
    public static final String KEY_STASH_SKILL_ID = "SkillId";
    public static final String KEY_STASH_SKILL_CD_UNTIL = "SkillCdUntil";
    public static final String KEY_STASH_BALANCE = "Balance";

    private @Nullable UUID matchId;
    private @Nullable Identifier acting;
    private long roundStartAt;
    private long unlockAt;
    private long nextSwitchAt;
    private long lastChangeAt;
    private List<BlackRavenDisguiseRules.PoolEntry> pool = List.of();
    private final Set<Identifier> visited = new LinkedHashSet<>();
    private final Map<Identifier, BlackRavenIdentityStash> identities = new LinkedHashMap<>();

    public @Nullable UUID matchId() {
        return matchId;
    }

    public boolean isBoundTo(@Nullable UUID currentMatch) {
        return matchId != null && matchId.equals(currentMatch);
    }

    /** Clears round state, then binds the match and the round clock. / 清除本局状态后绑定对局与本局时钟。 */
    public void bind(UUID matchId, long roundStartAt) {
        clear();
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.roundStartAt = roundStartAt;
        this.unlockAt = BlackRavenDisguiseRules.unlockAt(roundStartAt);
    }

    public @Nullable Identifier acting() {
        return acting;
    }

    public void setActing(@Nullable Identifier acting) {
        this.acting = acting;
    }

    public long roundStartAt() {
        return roundStartAt;
    }

    public long unlockAt() {
        return unlockAt;
    }

    public long nextSwitchAt() {
        return nextSwitchAt;
    }

    public long lastChangeAt() {
        return lastChangeAt;
    }

    /** Sets LastChangeAt = now and NextSwitchAt = now + 400. / 设置最近变化时间与下次可切换时间。 */
    public void recordChange(long now) {
        lastChangeAt = now;
        nextSwitchAt = BlackRavenDisguiseRules.nextSwitchAt(now);
    }

    public List<BlackRavenDisguiseRules.PoolEntry> pool() {
        return pool;
    }

    public void setPool(List<BlackRavenDisguiseRules.PoolEntry> pool) {
        List<BlackRavenDisguiseRules.PoolEntry> copy = List.copyOf(pool);
        this.pool = copy.size() > BlackRavenDisguiseRules.MAX_POOL_ENTRIES
                ? List.copyOf(copy.subList(0, BlackRavenDisguiseRules.MAX_POOL_ENTRIES))
                : copy;
    }

    public @Nullable BlackRavenDisguiseRules.PoolFlag poolFlag(Identifier roleId) {
        for (BlackRavenDisguiseRules.PoolEntry entry : pool) {
            if (entry.id().equals(roleId)) {
                return entry.flag();
            }
        }
        return null;
    }

    public boolean isVisited(Identifier roleId) {
        return visited.contains(roleId);
    }

    public void markVisited(Identifier roleId) {
        visited.add(Objects.requireNonNull(roleId, "roleId"));
    }

    public Set<Identifier> visited() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(visited));
    }

    public void clearVisited() {
        visited.clear();
    }

    public @Nullable BlackRavenIdentityStash stash(Identifier roleId) {
        return identities.get(roleId);
    }

    public @Nullable BlackRavenIdentityStash removeStash(Identifier roleId) {
        return identities.remove(roleId);
    }

    /** Existing stash of {@code roleId}, or a new empty one stored under it. / 取得或新建该身份的存档。 */
    public BlackRavenIdentityStash stashOrCreate(Identifier roleId) {
        return identities.computeIfAbsent(Objects.requireNonNull(roleId, "roleId"),
                ignored -> new BlackRavenIdentityStash());
    }

    public Map<Identifier, BlackRavenIdentityStash> stashes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(identities));
    }

    public void clearStashes() {
        identities.clear();
    }

    public boolean hasRoundState() {
        return matchId != null || acting != null || roundStartAt != 0L || unlockAt != 0L || nextSwitchAt != 0L
                || lastChangeAt != 0L || !pool.isEmpty() || !visited.isEmpty() || !identities.isEmpty();
    }

    public void clear() {
        matchId = null;
        acting = null;
        roundStartAt = 0L;
        unlockAt = 0L;
        nextSwitchAt = 0L;
        lastChangeAt = 0L;
        pool = List.of();
        visited.clear();
        identities.clear();
    }

    /**
     * Owner view at {@code now}: remaining ticks only, never stashes or absolute ticks. ravenBalance reads the
     * stashed Black Raven wallet while disguised and is 0 otherwise (decision D9). Unbound state is EMPTY.
     * 拥有者视图：只含剩余 tick，从不包含存档或绝对 tick。伪装时 ravenBalance 读取黑羽鸦存档钱包，否则为 0；
     * 未绑定时为 EMPTY。
     */
    public BlackRavenDisguiseSyncCodec.View toView(@Nullable UUID currentMatch, long now) {
        if (!isBoundTo(currentMatch)) {
            return BlackRavenDisguiseSyncCodec.View.EMPTY;
        }
        List<BlackRavenDisguiseSyncCodec.PoolRow> rows = new ArrayList<>(pool.size());
        for (BlackRavenDisguiseRules.PoolEntry entry : pool) {
            rows.add(new BlackRavenDisguiseSyncCodec.PoolRow(entry.id(), entry.flag(), visited.contains(entry.id())));
        }
        BlackRavenIdentityStash raven = acting == null ? null : identities.get(BlackRavenDisguiseRules.BLACK_RAVEN_ID);
        return new BlackRavenDisguiseSyncCodec.View(
                true,
                acting,
                BlackRavenDisguiseRules.remainingTicks(unlockAt, now),
                BlackRavenDisguiseRules.remainingTicks(nextSwitchAt, now),
                rows,
                raven == null ? 0 : Math.max(0, raven.balance())
        );
    }

    public void writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        if (matchId != null) {
            tag.putUuid(KEY_MATCH_ID, matchId);
        }
        if (acting != null) {
            tag.putString(KEY_ACTING, acting.toString());
        }
        tag.putLong(KEY_ROUND_START_AT, roundStartAt);
        tag.putLong(KEY_UNLOCK_AT, unlockAt);
        tag.putLong(KEY_NEXT_SWITCH_AT, nextSwitchAt);
        tag.putLong(KEY_LAST_CHANGE_AT, lastChangeAt);
        NbtList poolList = new NbtList();
        for (int index = 0; index < pool.size() && index < BlackRavenDisguiseRules.MAX_POOL_ENTRIES; index++) {
            BlackRavenDisguiseRules.PoolEntry entry = pool.get(index);
            NbtCompound entryNbt = new NbtCompound();
            entryNbt.putString(KEY_POOL_ID, entry.id().toString());
            entryNbt.putByte(KEY_POOL_FLAG, entry.flag().code());
            poolList.add(entryNbt);
        }
        tag.put(KEY_POOL, poolList);
        NbtList visitedList = new NbtList();
        for (Identifier roleId : visited) {
            visitedList.add(NbtString.of(roleId.toString()));
        }
        tag.put(KEY_VISITED, visitedList);
        NbtCompound identitiesNbt = new NbtCompound();
        identities.forEach((roleId, stash) -> identitiesNbt.put(roleId.toString(), stash.toNbt(lookup)));
        tag.put(KEY_IDENTITIES, identitiesNbt);
    }

    /**
     * Replaces this state from NBT with the caps applied; unparsable ids are skipped. The MatchId purge against
     * the live match is the service's job (join and tick fallback).
     * 从 NBT 替换状态并应用上限；无法解析的 id 被跳过。与当前对局 MatchId 不符时的清除由服务负责（加入与刻回退）。
     */
    public void readNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        clear();
        matchId = tag.containsUuid(KEY_MATCH_ID) ? tag.getUuid(KEY_MATCH_ID) : null;
        acting = tag.contains(KEY_ACTING, NbtElement.STRING_TYPE) ? parseRoleId(tag.getString(KEY_ACTING)) : null;
        roundStartAt = tag.getLong(KEY_ROUND_START_AT);
        unlockAt = tag.getLong(KEY_UNLOCK_AT);
        nextSwitchAt = tag.getLong(KEY_NEXT_SWITCH_AT);
        lastChangeAt = tag.getLong(KEY_LAST_CHANGE_AT);
        List<BlackRavenDisguiseRules.PoolEntry> poolEntries = new ArrayList<>();
        NbtList poolList = tag.getList(KEY_POOL, NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < poolList.size() && poolEntries.size() < BlackRavenDisguiseRules.MAX_POOL_ENTRIES;
             index++) {
            NbtCompound entryNbt = poolList.getCompound(index);
            Identifier roleId = parseRoleId(entryNbt.getString(KEY_POOL_ID));
            if (roleId != null) {
                poolEntries.add(new BlackRavenDisguiseRules.PoolEntry(roleId,
                        BlackRavenDisguiseRules.PoolFlag.fromCode(entryNbt.getByte(KEY_POOL_FLAG))));
            }
        }
        pool = List.copyOf(poolEntries);
        NbtList visitedList = tag.getList(KEY_VISITED, NbtElement.STRING_TYPE);
        for (int index = 0; index < visitedList.size() && visited.size() < BlackRavenDisguiseRules.MAX_IDENTITIES;
             index++) {
            Identifier roleId = parseRoleId(visitedList.getString(index));
            if (roleId != null) {
                visited.add(roleId);
            }
        }
        NbtCompound identitiesNbt = tag.getCompound(KEY_IDENTITIES);
        for (String key : identitiesNbt.getKeys()) {
            if (identities.size() >= BlackRavenDisguiseRules.MAX_IDENTITIES) {
                break;
            }
            Identifier roleId = parseRoleId(key);
            if (roleId != null && identitiesNbt.contains(key, NbtElement.COMPOUND_TYPE)) {
                identities.put(roleId, BlackRavenIdentityStash.fromNbt(identitiesNbt.getCompound(key), lookup));
            }
        }
    }

    static @Nullable Identifier parseRoleId(@Nullable String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > BlackRavenDisguiseRules.MAX_ROLE_ID_LENGTH) {
            return null;
        }
        return Identifier.tryParse(raw);
    }
}
