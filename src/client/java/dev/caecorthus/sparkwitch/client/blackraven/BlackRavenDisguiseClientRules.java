package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseSyncCodec;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure presentation rules for the owner's disguise view: Tab B row order, row interactivity, the shared
 * locked/cooldown/ready status, and the HUD line slot. The server stays authoritative; a clickable row is only
 * a hint and every select is re-validated there.
 * 拥有者伪装视图的纯展示规则：Tab B 行顺序、行可交互性、共用的未解锁/冷却/就绪状态与 HUD 行位置。
 * 服务端始终权威；可点击行只是提示，每次选择都由服务端重新校验。
 */
public final class BlackRavenDisguiseClientRules {
    /** Tab B rows per book page (two book lines each). / Tab B 每页行数（每行占两行书页文字）。 */
    public static final int ROWS_PER_PAGE = 5;

    private BlackRavenDisguiseClientRules() {
    }

    public enum RowKind {
        REVERT,
        ROLE
    }

    /** One Tab B row; {@code flag} is null only for the revert row. / Tab B 的一行；仅恢复行的 flag 为 null。 */
    public record Row(
            RowKind kind,
            Identifier id,
            @Nullable BlackRavenDisguiseRules.PoolFlag flag,
            boolean current,
            boolean visited
    ) {
        public Row {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }

        /** Greyed rows can never be selected, whatever the session. / 灰色行无论会话如何都不可选择。 */
        public boolean greyed() {
            return kind == RowKind.ROLE && flag != BlackRavenDisguiseRules.PoolFlag.SELECTABLE;
        }
    }

    public enum Status {
        LOCKED,
        COOLDOWN,
        READY
    }

    /**
     * Revert row first while disguised, then pool rows ordered SELECTABLE, UNSUPPORTED, POLICE, DENYLISTED,
     * UNKNOWN; inside each group, {@code roleOrder} (WatheRoles.ROLES) order, unknown ids last in sync order.
     * 伪装时恢复行在最前；其余按 可选、暂未开放、警察、禁用、未知 分组，组内按 WatheRoles.ROLES 顺序，未知 id 按同步顺序排在最后。
     */
    public static List<Row> rows(BlackRavenDisguiseSyncCodec.View view, List<Identifier> roleOrder) {
        List<Row> rows = new ArrayList<>();
        if (!view.bound()) {
            return List.of();
        }
        if (view.disguised()) {
            rows.add(new Row(RowKind.REVERT, BlackRavenDisguiseRules.BLACK_RAVEN_ID, null, false, false));
        }
        Map<Identifier, Integer> order = new HashMap<>();
        for (int index = 0; index < roleOrder.size(); index++) {
            order.putIfAbsent(roleOrder.get(index), index);
        }
        List<BlackRavenDisguiseSyncCodec.PoolRow> pool = new ArrayList<>(view.pool());
        List<BlackRavenDisguiseSyncCodec.PoolRow> synced = view.pool();
        pool.sort(Comparator
                .comparingInt((BlackRavenDisguiseSyncCodec.PoolRow row) -> row.flag().displayRank())
                .thenComparingInt(row -> order.getOrDefault(row.id(), Integer.MAX_VALUE))
                .thenComparingInt(synced::indexOf));
        for (BlackRavenDisguiseSyncCodec.PoolRow row : pool) {
            rows.add(new Row(RowKind.ROLE, row.id(), row.flag(), row.id().equals(view.acting()), row.visited()));
        }
        return List.copyOf(rows);
    }

    /**
     * The client treats a mask session as expired this many ticks before the server TTL, so a click sent just
     * before expiry still reaches the server inside the session.
     * 客户端比服务端会话时长提前这么多刻视为会话过期，使临近过期时发出的点击仍能在会话内到达服务端。
     */
    public static final int SESSION_EXPIRY_MARGIN_TICKS = 20;

    /**
     * Local estimate of the one-shot mask session: the client never learns the server open tick, so it counts
     * ticks since the open packet arrived and stops offering rows before {@code SESSION_TTL_TICKS}.
     * 一次性面具会话的本地估计：客户端不知道服务端的开启刻，因此从收到开启数据包起计数，并在
     * {@code SESSION_TTL_TICKS} 之前停止提供可点击行。
     */
    public static boolean sessionLive(int session, int ticksSinceOpen) {
        return session > 0 && ticksSinceOpen >= 0
                && ticksSinceOpen < BlackRavenDisguiseRules.SESSION_TTL_TICKS - SESSION_EXPIRY_MARGIN_TICKS;
    }

    /** True when a mask-opened book has outlived its session. / 面具打开的账本已超过其会话时长时为 true。 */
    public static boolean sessionExpired(int session, int ticksSinceOpen) {
        return session > 0 && !sessionLive(session, ticksSinceOpen);
    }

    /** A live mask session plus an unlocked, cooled-down, bound view. / 需要有效面具会话且视图已绑定、已解锁、冷却完毕。 */
    public static boolean interactive(int session, int ticksSinceOpen, BlackRavenDisguiseSyncCodec.View view) {
        return sessionLive(session, ticksSinceOpen)
                && view.bound() && view.unlockRemaining() == 0 && view.cooldownRemaining() == 0;
    }

    public static boolean clickable(Row row, int session, int ticksSinceOpen, BlackRavenDisguiseSyncCodec.View view) {
        if (!interactive(session, ticksSinceOpen, view)) {
            return false;
        }
        if (row.kind() == RowKind.REVERT) {
            return view.disguised();
        }
        return !row.greyed() && !row.current();
    }

    public static int pageCount(int rowCount) {
        return Math.max(1, (rowCount + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    public static <T> List<T> page(List<T> rows, int pageIndex) {
        int start = Math.clamp((long) pageIndex * ROWS_PER_PAGE, 0, rows.size());
        int end = Math.min(rows.size(), start + ROWS_PER_PAGE);
        return rows.subList(start, end);
    }

    public static Status status(BlackRavenDisguiseSyncCodec.View view) {
        if (view.unlockRemaining() > 0) {
            return Status.LOCKED;
        }
        return view.cooldownRemaining() > 0 ? Status.COOLDOWN : Status.READY;
    }

    /** Whole seconds for the status, same rounding as the server messages. / 状态秒数，与服务端提示同样向上取整。 */
    public static int statusSeconds(BlackRavenDisguiseSyncCodec.View view) {
        return switch (status(view)) {
            case LOCKED -> BlackRavenDisguiseRules.ceilSeconds(view.unlockRemaining());
            case COOLDOWN -> BlackRavenDisguiseRules.ceilSeconds(view.cooldownRemaining());
            case READY -> 0;
        };
    }

    /**
     * Top y of the disguise HUD line: the mode-row slot, lifted to clear the tallest bottom-right stack a batch-1
     * disguise can draw (a two-line NoellesRoles HUD at the screen edge, or the padded Orthopedist line).
     * 伪装 HUD 行的顶部 y：沿用模式行位置，并上移以避开批次一伪装可能绘制的最高右下角堆叠
     * （贴边的两行诺艾尔 HUD，或带内边距的骨科大夫行）。
     */
    public static int disguiseLineY(int scaledHeight, int fontHeight, int bottomPadding, int rowGap) {
        int modeRowY = scaledHeight - bottomPadding - fontHeight * 2 - rowGap;
        int noellesStackTop = scaledHeight - fontHeight * 2;
        int orthopedistTop = scaledHeight - bottomPadding - fontHeight;
        int clearance = Math.min(noellesStackTop, orthopedistTop) - rowGap - fontHeight;
        return Math.min(modeRowY, clearance);
    }
}
