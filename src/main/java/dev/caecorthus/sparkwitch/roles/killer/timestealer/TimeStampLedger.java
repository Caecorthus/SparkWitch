package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Time Stamp bookkeeping over a snapshot of one player's inventory; no Minecraft types, so every placement rule is
 * unit-testable. Index layout matches {@code PlayerInventory}: 0-8 hotbar, 9-35 hidden main slots, 36-39 armor,
 * 40 offhand; {@link #CURSOR} is the open handler's cursor. Plans are applied by {@link TimeStampInventory} on the
 * server thread right after the snapshot is read, so a plan never races another inventory write.
 * 基于单个玩家背包快照的纯时光邮票账本；不含 Minecraft 类型，因此每条摆放规则都可单元测试。下标布局与
 * {@code PlayerInventory} 一致：0-8 快捷栏、9-35 隐藏主背包、36-39 盔甲、40 副手；{@link #CURSOR} 表示打开界面的光标。
 * 计划由 {@link TimeStampInventory} 在服务器线程上读取快照后立即应用，因此不会与其他背包写入竞争。
 */
final class TimeStampLedger {
    static final int INVENTORY_SIZE = 41;
    static final int HOTBAR_END = 9;
    static final int MAIN_END = 36;
    static final int ARMOR_END = 40;
    static final int OFFHAND = 40;
    /** Move index of the handler cursor. / 光标对应的移动下标。 */
    static final int CURSOR = -1;

    private TimeStampLedger() {
    }

    enum Kind {
        EMPTY,
        STAMPS,
        OTHER
    }

    record SlotState(Kind kind, int count) {
        static final SlotState EMPTY = new SlotState(Kind.EMPTY, 0);
        static final SlotState OTHER = new SlotState(Kind.OTHER, 1);

        SlotState {
            Objects.requireNonNull(kind, "kind");
            count = kind == Kind.EMPTY ? 0 : Math.max(0, count);
        }

        static SlotState stamps(int count) {
            return count <= 0 ? EMPTY : new SlotState(Kind.STAMPS, count);
        }

        boolean isStamps() {
            return kind == Kind.STAMPS;
        }
    }

    /**
     * Snapshot: exactly {@link #INVENTORY_SIZE} inventory states plus the cursor. Missing trailing indices read as
     * EMPTY and extra ones are ignored, so a short list never throws.
     * 快照：恰好 {@link #INVENTORY_SIZE} 个背包格状态加光标。缺失的尾部下标视为空，多余的忽略，短列表不会抛异常。
     */
    record Holdings(List<SlotState> inventory, int cursorStamps, boolean cursorOther) {
        Holdings {
            List<SlotState> normalized = new ArrayList<>(INVENTORY_SIZE);
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                SlotState state = inventory != null && i < inventory.size() ? inventory.get(i) : null;
                normalized.add(state == null ? SlotState.EMPTY : state);
            }
            inventory = List.copyOf(normalized);
            cursorStamps = cursorOther ? 0 : Math.max(0, cursorStamps);
        }
    }

    /** Signed stamp change at one index; {@link #CURSOR} for the cursor. / 某下标的邮票增减；光标为 {@link #CURSOR}。 */
    record Move(int index, int delta) {
    }

    /** Ordered moves plus stamps that found no slot. / 有序的移动，以及找不到位置的邮票数。 */
    record Plan(List<Move> moves, int undelivered) {
        static final Plan NONE = new Plan(List.of(), 0);

        Plan {
            moves = List.copyOf(moves);
            undelivered = Math.max(0, undelivered);
        }

        boolean isEmpty() {
            return moves.isEmpty() && undelivered == 0;
        }
    }

    /** Stamps in all 41 indices plus the cursor; never counts undelivered stamps. / 41 格加光标中的邮票数；不含未送达邮票。 */
    static int balance(Holdings holdings) {
        long total = holdings.cursorStamps();
        for (SlotState state : holdings.inventory()) {
            if (state.isStamps()) {
                total += state.count();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Number of inventory indices holding stamps (the cursor is not a stack slot). / 持有邮票的背包格数量（光标不算）。 */
    static int stampStacks(Holdings holdings) {
        int stacks = 0;
        for (SlotState state : holdings.inventory()) {
            if (state.isStamps()) {
                stacks++;
            }
        }
        return stacks;
    }

    /**
     * Removal plan for a purchase: hotbar 0-8 left to right, then hidden main 9-35, then the offhand, then armor,
     * and the cursor last. Empty when {@code cost <= 0} or the balance is short. Emptying the only hotbar stack is
     * intended: it may free the hotbar slot a grenade or Psycho bat needs.
     * 购买的扣除计划：快捷栏 0-8 从左到右，然后隐藏主背包 9-35、副手、盔甲，最后是光标。{@code cost <= 0} 或余额不足时为空。
     * 扣空唯一的快捷栏邮票堆是刻意的：可能正好空出手雷或疯魔蝙蝠需要的快捷栏格。
     */
    static Optional<Plan> reserve(Holdings holdings, int cost) {
        if (cost <= 0 || balance(holdings) < cost) {
            return Optional.empty();
        }
        List<Move> moves = new ArrayList<>();
        int remaining = cost;
        for (int index : reserveOrder()) {
            if (remaining == 0) {
                break;
            }
            SlotState state = holdings.inventory().get(index);
            if (state.isStamps()) {
                int take = Math.min(remaining, state.count());
                moves.add(new Move(index, -take));
                remaining -= take;
            }
        }
        if (remaining > 0) {
            int take = Math.min(remaining, holdings.cursorStamps());
            moves.add(new Move(CURSOR, -take));
            remaining -= take;
        }
        return remaining == 0 ? Optional.of(new Plan(moves, 0)) : Optional.empty();
    }

    /**
     * Grant placement: existing hotbar stamp stacks with room, then the first empty hotbar slot, then existing hidden
     * main/offhand stacks with room, then empty main slots 9-35 in order, then an empty offhand; the rest is
     * undelivered. Stacks never exceed {@code max}; armor and the cursor never receive stamps.
     * 发放摆放：先补快捷栏已有且有空间的邮票堆，再第一个空快捷栏格，再补隐藏主背包/副手已有的邮票堆，再按顺序使用
     * 空主背包格 9-35，再空副手；其余记为未送达。堆叠从不超过 {@code max}；盔甲与光标从不接收邮票。
     */
    static Plan deliver(Holdings holdings, int amount, int max) {
        Sim sim = new Sim(holdings);
        int undelivered = sim.deliver(amount, Math.max(1, max));
        return new Plan(sim.moves, undelivered);
    }

    /**
     * Merges every other stamp stack into the canonical one: the leftmost hotbar stamp stack, else the leftmost hidden
     * stack. Overflow above {@code max} stays where it is, the cursor is untouched, and an EMPTY index is never a
     * target, so a freed hotbar slot is never taken over automatically.
     * 将其余邮票堆合并到主堆：最左的快捷栏邮票堆，否则最左的隐藏堆。超过 {@code max} 的部分留在原处，光标不动，
     * 且从不以空格为目标，因此不会自动占用刚空出的快捷栏格。
     */
    static Plan consolidate(Holdings holdings, int max) {
        int limit = Math.max(1, max);
        int target = -1;
        for (int index = 0; index < INVENTORY_SIZE; index++) {
            if (holdings.inventory().get(index).isStamps()) {
                target = index;
                break;
            }
        }
        if (target < 0) {
            return Plan.NONE;
        }
        List<Move> moves = new ArrayList<>();
        int targetCount = holdings.inventory().get(target).count();
        for (int index = target + 1; index < INVENTORY_SIZE; index++) {
            SlotState state = holdings.inventory().get(index);
            int room = limit - targetCount;
            if (room <= 0) {
                break;
            }
            if (state.isStamps()) {
                int take = Math.min(room, state.count());
                moves.add(new Move(index, -take));
                moves.add(new Move(target, take));
                targetCount += take;
            }
        }
        return new Plan(moves, 0);
    }

    /**
     * Refund after a failed purchase effect: each reserved count goes back to its exact index when that index is EMPTY
     * or a stamp stack with room (the cursor only when it is empty or holds stamps); whatever cannot go back is
     * re-delivered through {@link #deliver}.
     * 购买效果失败后的退回：每个预留数量在原下标为空或为仍有空间的邮票堆时原样放回（光标仅在为空或持有邮票时放回）；
     * 放不回的部分经 {@link #deliver} 重新发放。
     */
    static Plan undo(Holdings afterEffect, Plan reserved, int max) {
        int limit = Math.max(1, max);
        Sim sim = new Sim(afterEffect);
        int leftover = 0;
        for (Move move : reserved.moves()) {
            if (move.delta() >= 0) {
                continue;
            }
            leftover += -move.delta() - sim.putBack(move.index(), -move.delta(), limit);
        }
        int undelivered = sim.deliver(leftover + reserved.undelivered(), limit);
        return new Plan(sim.moves, undelivered);
    }

    static boolean hasEmptyHotbar(Holdings holdings) {
        for (int index = 0; index < HOTBAR_END; index++) {
            if (holdings.inventory().get(index).kind() == Kind.EMPTY) {
                return true;
            }
        }
        return false;
    }

    /** Whether any positive move lands in the hotbar. / 是否有正向移动落在快捷栏。 */
    static boolean landsInHotbar(Plan plan) {
        for (Move move : plan.moves()) {
            if (move.delta() > 0 && move.index() >= 0 && move.index() < HOTBAR_END) {
                return true;
            }
        }
        return false;
    }

    private static int[] reserveOrder() {
        int[] order = new int[INVENTORY_SIZE];
        int cursor = 0;
        for (int index = 0; index < MAIN_END; index++) {
            order[cursor++] = index;
        }
        order[cursor++] = OFFHAND;
        for (int index = MAIN_END; index < ARMOR_END; index++) {
            order[cursor++] = index;
        }
        return order;
    }

    /** Mutable working copy so chained placements see earlier ones. / 可变工作副本，使后续摆放能看到之前的摆放。 */
    private static final class Sim {
        private final Kind[] kinds = new Kind[INVENTORY_SIZE];
        private final int[] counts = new int[INVENTORY_SIZE];
        private int cursorCount;
        private final boolean cursorOther;
        private final List<Move> moves = new ArrayList<>();

        private Sim(Holdings holdings) {
            for (int index = 0; index < INVENTORY_SIZE; index++) {
                SlotState state = holdings.inventory().get(index);
                kinds[index] = state.kind();
                counts[index] = state.count();
            }
            cursorCount = holdings.cursorStamps();
            cursorOther = holdings.cursorOther();
        }

        private int putBack(int index, int amount, int max) {
            if (index == CURSOR) {
                if (cursorOther) {
                    return 0;
                }
                // The cursor had these stamps a moment ago; restoring them never exceeds what was there.
                // 光标片刻前持有这些邮票；原样放回不会超过原数量。
                cursorCount += amount;
                moves.add(new Move(CURSOR, amount));
                return amount;
            }
            if (index < 0 || index >= INVENTORY_SIZE) {
                return 0;
            }
            return fill(index, amount, max, true);
        }

        private int deliver(int amount, int max) {
            int remaining = Math.max(0, amount);
            for (int index = 0; index < HOTBAR_END && remaining > 0; index++) {
                if (kinds[index] == Kind.STAMPS) {
                    remaining -= fill(index, remaining, max, false);
                }
            }
            for (int index = 0; index < HOTBAR_END && remaining > 0; index++) {
                if (kinds[index] == Kind.EMPTY) {
                    remaining -= fill(index, remaining, max, true);
                    break;
                }
            }
            for (int index = HOTBAR_END; index < MAIN_END && remaining > 0; index++) {
                if (kinds[index] == Kind.STAMPS) {
                    remaining -= fill(index, remaining, max, false);
                }
            }
            if (remaining > 0 && kinds[OFFHAND] == Kind.STAMPS) {
                remaining -= fill(OFFHAND, remaining, max, false);
            }
            for (int index = HOTBAR_END; index < MAIN_END && remaining > 0; index++) {
                if (kinds[index] == Kind.EMPTY) {
                    remaining -= fill(index, remaining, max, true);
                }
            }
            if (remaining > 0 && kinds[OFFHAND] == Kind.EMPTY) {
                remaining -= fill(OFFHAND, remaining, max, true);
            }
            return remaining;
        }

        private int fill(int index, int amount, int max, boolean allowEmpty) {
            if (amount <= 0 || kinds[index] == Kind.OTHER || (kinds[index] == Kind.EMPTY && !allowEmpty)) {
                return 0;
            }
            int added = Math.min(amount, max - counts[index]);
            if (added <= 0) {
                return 0;
            }
            kinds[index] = Kind.STAMPS;
            counts[index] += added;
            moves.add(new Move(index, added));
            return added;
        }
    }
}
