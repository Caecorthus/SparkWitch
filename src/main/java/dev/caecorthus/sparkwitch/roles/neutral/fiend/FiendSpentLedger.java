package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure server-side ledger of spent Fiends: a Fiend whose moment was ended by a Taotie swallow is out of the dormant
 * state for the rest of that Wathe match (no immunity, no hit reactions, counted normally, no second moment), even
 * after a release. Bound to one match id: a mark for another match drops the old set, and a query for any other match
 * (or between rounds) is never spent.
 * 纯服务端的「已耗尽魔人」登记表：魔人时刻因饕餮吞噬而结束的魔人，在该 Wathe 对局剩余时间内不再处于休眠状态（无免疫、
 * 无受击反应、正常计入胜负、不能再次开启时刻），即使之后被释放也是如此。绑定到单一对局 id：为其他对局登记会丢弃旧集合，
 * 查询其他对局（或回合之间）永远不算已耗尽。
 */
public final class FiendSpentLedger {
    private final Set<UUID> spent = new HashSet<>();
    private @Nullable UUID matchId;

    /** A {@code null} match binds nothing. / {@code null} 对局不登记任何内容。 */
    public void mark(UUID player, @Nullable UUID match) {
        if (player == null || match == null) {
            return;
        }
        if (!match.equals(matchId)) {
            spent.clear();
            matchId = match;
        }
        spent.add(player);
    }

    public boolean isSpent(@Nullable UUID player, @Nullable UUID currentMatch) {
        return player != null && currentMatch != null && currentMatch.equals(matchId) && spent.contains(player);
    }

    public void clear() {
        spent.clear();
        matchId = null;
    }

    public boolean isEmpty() {
        return spent.isEmpty();
    }
}
