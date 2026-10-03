package dev.caecorthus.sparkwitch.client.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathCauseGroup;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.NecrologyEntry;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Pure row and page model for the Necrology book, built only from the owner-synced Prophet component. Text
 * measuring stays in the screen; this class decides what is listed, in which order, and how rows fill pages.
 * 亡者名录书本的纯行/页模型，只由仅同步给所有者的先知组件构建。文字测量留在界面中；本类决定列出什么、按何种顺序，
 * 以及各行如何填满书页。
 */
public final class ProphetNecrologyPages {
    /** Vanilla book text area height 128 / line height 9. / 原版书页文字区高度 128 / 行高 9。 */
    public static final int MAX_PAGE_LINES = 14;
    /** One blank line between rows that share a page. / 同一页内相邻两行之间空一行。 */
    public static final int ROW_GAP_LINES = 1;

    private ProphetNecrologyPages() {
    }

    /** One numbered name; {@code solved} marks a locked Prophecy outcome for that player. / 一行编号名字；solved 表示该玩家的预言已锁定结果。 */
    public record NameRow(int number, String name, boolean solved) {
    }

    /**
     * One Prophecy record. Exactly one of: a revealed killer, "no killer", or the excluded cause groups (enum order).
     * 一条预言记录：揭晓的凶手、「无人行凶」或已排除的死因分组（按枚举顺序）三者之一。
     */
    public record ProphecyRow(
            UUID victim,
            String victimName,
            ProphecyRecord.Outcome outcome,
            @Nullable String killerName,
            List<ProphetDeathCauseGroup> excluded
    ) {
        public ProphecyRow {
            excluded = List.copyOf(excluded);
        }
    }

    /** Names in discovery order, numbered from 1. / 按发现顺序列出名字，从 1 开始编号。 */
    public static List<NameRow> nameRows(List<NecrologyEntry> necrology, Map<UUID, ProphecyRecord> prophecies) {
        List<NameRow> rows = new ArrayList<>(necrology.size());
        for (NecrologyEntry entry : necrology) {
            ProphecyRecord record = prophecies.get(entry.player());
            boolean solved = record != null && record.outcome() != ProphecyRecord.Outcome.PENDING;
            rows.add(new NameRow(rows.size() + 1, entry.name(), solved));
        }
        return List.copyOf(rows);
    }

    /**
     * Every Prophecy record in the component's order (first guess first). It may name players missing from the
     * necrology, because a Prophecy may target any dead player (owner decision Q3).
     * 按组件顺序（最先猜测者在前）列出所有预言记录。其中可能包含名录里没有的玩家，因为预言可以猜任何死者（所有者决定 Q3）。
     */
    public static List<ProphecyRow> prophecyRows(Map<UUID, ProphecyRecord> prophecies) {
        List<ProphecyRow> rows = new ArrayList<>(prophecies.size());
        for (Map.Entry<UUID, ProphecyRecord> entry : prophecies.entrySet()) {
            ProphecyRecord record = entry.getValue();
            List<ProphetDeathCauseGroup> excluded = new ArrayList<>();
            for (ProphetDeathCauseGroup group : ProphetDeathCauseGroup.values()) {
                if (record.excluded().contains(group)) {
                    excluded.add(group);
                }
            }
            rows.add(new ProphecyRow(
                    entry.getKey(),
                    record.victimName(),
                    record.outcome(),
                    record.outcome() == ProphecyRecord.Outcome.REVEALED_KILLER ? record.killerName() : null,
                    excluded
            ));
        }
        return List.copyOf(rows);
    }

    /**
     * Greedy pagination: rows keep their order, a row never splits across pages, rows on one page are separated by
     * {@code gapLines}, and a row taller than a page gets a page of its own (the screen clips it). Always returns at
     * least one (possibly empty) page.
     * 贪心分页：保持行顺序，单行不跨页，同页相邻行之间空 {@code gapLines} 行；高于一页的行独占一页（由界面裁剪）。
     * 始终至少返回一页（可能为空）。
     */
    public static <T> List<List<T>> paginate(List<T> rows, ToIntFunction<T> heightLines, int linesPerPage, int gapLines) {
        int capacity = Math.max(1, linesPerPage);
        int gap = Math.max(0, gapLines);
        List<List<T>> pages = new ArrayList<>();
        List<T> page = new ArrayList<>();
        int used = 0;
        for (T row : rows) {
            int height = Math.clamp(heightLines.applyAsInt(row), 1, capacity);
            int needed = page.isEmpty() ? height : used + gap + height;
            if (!page.isEmpty() && needed > capacity) {
                pages.add(List.copyOf(page));
                page = new ArrayList<>();
                needed = height;
            }
            page.add(row);
            used = needed;
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(List.copyOf(page));
        }
        return List.copyOf(pages);
    }
}
