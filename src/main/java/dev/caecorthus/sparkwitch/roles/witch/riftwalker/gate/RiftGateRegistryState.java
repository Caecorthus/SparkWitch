package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure storage behind {@link RiftGateRegistryComponent} (frozen G0 API), kept free of CCA so it is unit-testable: the
 * live gate records ordered by number, the next gate number (1-based, never reused within a match, C9), and the match
 * id the list belongs to. Every rule (spawning, tickets, sessions) lives in {@link RiftGateRegistry}.
 * {@link RiftGateRegistryComponent} 背后的纯存储（G0 冻结 API），不依赖 CCA，便于单元测试：按编号排序的存活门记录、
 * 下一个门编号（从 1 开始，同一对局内不复用，C9），以及列表所属的对局 id。所有规则（生成、区块票、会话）位于
 * {@link RiftGateRegistry}。
 */
public class RiftGateRegistryState {
    private static final int FIRST_GATE_NUMBER = 1;

    private final List<RiftGateRecord> gates = new ArrayList<>();
    private int nextNumber = FIRST_GATE_NUMBER;
    @Nullable
    private String matchId;

    /** Live records ordered by number (unmodifiable view). / 按编号排序的存活记录（只读视图）。 */
    public List<RiftGateRecord> gates() {
        return Collections.unmodifiableList(gates);
    }

    public Optional<RiftGateRecord> byNumber(int number) {
        for (RiftGateRecord gate : gates) {
            if (gate.number() == number) {
                return Optional.of(gate);
            }
        }
        return Optional.empty();
    }

    /** Hands out the next placement number (1-based, never reused this match). / 分配下一个放置编号（从 1 起，本局不复用）。 */
    public int allocateNumber() {
        return nextNumber++;
    }

    /** Adds a record, keeping number order; a duplicate number replaces the old record. / 按编号顺序加入记录；编号重复则替换。 */
    public void add(RiftGateRecord gate) {
        Objects.requireNonNull(gate, "gate");
        gates.removeIf(existing -> existing.number() == gate.number());
        gates.add(gate);
        gates.sort(Comparator.comparingInt(RiftGateRecord::number));
    }

    public Optional<RiftGateRecord> remove(int number) {
        for (int index = 0; index < gates.size(); index++) {
            if (gates.get(index).number() == number) {
                return Optional.of(gates.remove(index));
            }
        }
        return Optional.empty();
    }

    @Nullable
    public String matchId() {
        return matchId;
    }

    /** Binds the list to a match; a different match drops every record and restarts numbering. / 绑定对局；换局则清空并重新编号。 */
    public void bindMatch(@Nullable String match) {
        if (!Objects.equals(matchId, match)) {
            clear();
            matchId = match;
        }
    }

    /** Drops every record, the match binding, and restarts numbering at 1. / 清空所有记录与对局绑定，编号从 1 重新开始。 */
    public void clear() {
        gates.clear();
        nextNumber = FIRST_GATE_NUMBER;
        matchId = null;
    }
}
