package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.ForcedRecruit;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Admin-forced Grand Witch recruitments by recruitment number ({@code /sparkwitch:forceAccompliceRole}): at most one
 * entry per order and per player. Server-only, never synced. NBT: a list of {@code Order}/{@code Player}/{@code Role}
 * entries under {@code ForcedRecruits}; malformed and duplicate entries are skipped. An order is "pending" while it is
 * greater than the running round's recruited count (any order ≥ 1 when no round runs); only pending entries take part
 * in the decisions below.
 * 按招募序号保存的管理员强制大魔女招募（{@code /sparkwitch:forceAccompliceRole}）：每个序号、每名玩家至多一条。仅服务端、
 * 从不同步。NBT：{@code ForcedRecruits} 下的 {@code Order}/{@code Player}/{@code Role} 条目列表；损坏或重复的条目会被跳过。
 * 序号大于本局已招募次数时（无对局时任何 ≥1 的序号）为"待生效"；下列判定只考虑待生效条目。
 */
final class ForcedRecruitQueue {
    static final String NBT_KEY = "ForcedRecruits";

    private final TreeMap<Integer, ForcedRecruit> entries = new TreeMap<>();

    @Nullable
    ForcedRecruit get(int order) {
        return entries.get(order);
    }

    @Nullable
    ForcedRecruit remove(int order) {
        return entries.remove(order);
    }

    void clearAll() {
        entries.clear();
    }

    /** Immutable copy in ascending order. / 按序号升序的不可变副本。 */
    SortedMap<Integer, ForcedRecruit> snapshot() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(entries));
    }

    ForcedRecruitPlan plan(ForcedRecruit recruit, boolean special, @Nullable Integer requestedOrder, int recruitedCount) {
        return plan(entries, recruit, special, requestedOrder, recruitedCount);
    }

    /**
     * Stores an accepted plan: the player's other entry is dropped (moved) and any entry at the order is overwritten
     * (replaced).
     * 写入已接受的判定：删除该玩家的其他条目（移动），并覆盖该序号上的已有条目（顶替）。
     */
    void apply(ForcedRecruitPlan.Accepted plan) {
        UUID player = plan.recruit().player();
        entries.values().removeIf(entry -> entry.player().equals(player));
        entries.put(plan.order(), plan.recruit());
    }

    /**
     * The whole decision for one request. The order is the requested one, or {@link #nextFreeOrder} when omitted. A
     * passed order is refused first, then a special accomplice still held by another pending entry; otherwise the
     * result says which old order the player leaves and which other player's entry is replaced.
     * 一次请求的完整判定。序号为请求的序号，省略时取 {@link #nextFreeOrder}。先拒绝已过去的序号，再拒绝仍被其他待生效条目
     * 持有的特殊共犯；否则结果说明该玩家离开的旧序号以及被顶替的其他玩家条目。
     */
    static ForcedRecruitPlan plan(
            SortedMap<Integer, ForcedRecruit> entries,
            ForcedRecruit recruit,
            boolean special,
            @Nullable Integer requestedOrder,
            int recruitedCount
    ) {
        int done = Math.max(0, recruitedCount);
        SortedMap<Integer, ForcedRecruit> pending = pending(entries, done);
        int order = requestedOrder != null ? requestedOrder : nextFreeOrder(pending, recruit.player(), done);
        if (isPast(order, done)) {
            return new ForcedRecruitPlan.OrderPassed(order, done);
        }
        if (special) {
            Map.Entry<Integer, ForcedRecruit> holder = specialHolder(pending, recruit, order, done);
            if (holder != null) {
                return new ForcedRecruitPlan.SpecialHeld(holder.getValue(), holder.getKey());
            }
        }
        int movedFrom = 0;
        for (Map.Entry<Integer, ForcedRecruit> entry : pending.entrySet()) {
            if (entry.getKey() != order && entry.getValue().player().equals(recruit.player())) {
                movedFrom = entry.getKey();
            }
        }
        ForcedRecruit replaced = pending.get(order);
        if (replaced != null && replaced.player().equals(recruit.player())) {
            replaced = null;
        }
        return new ForcedRecruitPlan.Accepted(recruit, order, movedFrom, replaced);
    }

    /**
     * The smallest pending order (above {@code recruitedCount}) not held by another player; the player's own entry
     * counts as free, since setting them again moves them.
     * 最小的、未被其他玩家占用的待生效序号（大于 {@code recruitedCount}）；该玩家自己的条目视为空闲，因为再次设置会移动它。
     */
    static int nextFreeOrder(SortedMap<Integer, ForcedRecruit> entries, UUID player, int recruitedCount) {
        int order = Math.max(0, recruitedCount) + 1;
        while (true) {
            ForcedRecruit held = entries.get(order);
            if (held == null || held.player().equals(player)) {
                return order;
            }
            order++;
        }
    }

    /** An order at or below the round's recruited count already happened. / 不大于本局已招募次数的序号已经发生。 */
    static boolean isPast(int order, int recruitedCount) {
        return order <= Math.max(0, recruitedCount);
    }

    /**
     * Another pending entry holding the same role, ignoring the player's own entry (it moves) and the entry at
     * {@code order} (it is replaced). Only meaningful for a special accomplice; the plain Accomplice has no limit.
     * 持有相同职业的其他待生效条目，忽略该玩家自己的条目（会被移动）和 {@code order} 上的条目（会被顶替）。只对特殊共犯
     * 有意义；普通共犯不限人数。
     */
    static @Nullable Map.Entry<Integer, ForcedRecruit> specialHolder(
            SortedMap<Integer, ForcedRecruit> entries,
            ForcedRecruit recruit,
            int order,
            int recruitedCount
    ) {
        for (Map.Entry<Integer, ForcedRecruit> entry : pending(entries, recruitedCount).entrySet()) {
            ForcedRecruit held = entry.getValue();
            if (entry.getKey() != order
                    && !held.player().equals(recruit.player())
                    && held.role().equals(recruit.role())) {
                return entry;
            }
        }
        return null;
    }

    private static SortedMap<Integer, ForcedRecruit> pending(SortedMap<Integer, ForcedRecruit> entries, int recruitedCount) {
        return entries.tailMap(Math.max(0, recruitedCount) + 1);
    }

    NbtList toNbt() {
        NbtList list = new NbtList();
        for (Map.Entry<Integer, ForcedRecruit> forced : entries.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putInt("Order", forced.getKey());
            entry.putString("Player", forced.getValue().player().toString());
            entry.putString("Role", forced.getValue().role().toString());
            list.add(entry);
        }
        return list;
    }

    void readFromNbt(NbtCompound tag) {
        entries.clear();
        NbtList list = tag.getList(NBT_KEY, NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < list.size(); index++) {
            NbtCompound entry = list.getCompound(index);
            int order = entry.contains("Order", NbtElement.NUMBER_TYPE) ? entry.getInt("Order") : 0;
            Identifier roleId = Identifier.tryParse(entry.getString("Role"));
            UUID player;
            try {
                player = UUID.fromString(entry.getString("Player"));
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed entry and keep the valid ones. / 跳过损坏条目，保留其他有效条目。
                continue;
            }
            // The first entry wins a duplicated order or player. / 序号或玩家重复时保留第一条。
            if (order < 1 || roleId == null || entries.containsKey(order)
                    || entries.values().stream().anyMatch(held -> held.player().equals(player))) {
                continue;
            }
            entries.put(order, new ForcedRecruit(player, roleId));
        }
    }
}
