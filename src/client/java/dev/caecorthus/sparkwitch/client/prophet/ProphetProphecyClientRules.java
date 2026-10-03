package dev.caecorthus.sparkwitch.client.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetDeathCauseGroup;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent.ProphecyRecord;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import org.jetbrains.annotations.Nullable;

/**
 * Pure presentation and send gates for the Prophecy screen. They only mirror the server so the UI does not offer
 * a click the server would refuse; they grant no authority.
 * 预言界面的纯展示与发送门禁。它们只镜像服务端规则，避免界面提供必被拒绝的操作，本身没有任何裁决权。
 */
public final class ProphetProphecyClientRules {
    // Screen geometry shared by drawing and hit-testing. 绘制与点击判定共用的界面几何。
    public static final int PANEL_PAD = 6;
    public static final int COLUMN_GAP = 6;
    public static final int SCROLLBAR_W = 3;
    public static final int CELL_GAP = 3;
    /** Inset of the cells from the grid well's outer edge (1px border + 1px gap). / 格子距死因区外框的内缩。 */
    public static final int GRID_INSET = 2;
    /** Horizontal room a cause label leaves inside its cell. / 死因标签在格子内预留的横向空间。 */
    public static final int CELL_TEXT_MARGIN = 6;

    private ProphetProphecyClientRules() {
    }

    public static int innerWidth(int panelW) {
        return Math.max(1, panelW - PANEL_PAD * 2);
    }

    public static int listWidth(int innerW) {
        return Math.min(Math.max(innerW * 5 / 12, Math.min(84, innerW / 2)), 168);
    }

    public static int gridWidth(int innerW) {
        return Math.max(1, innerW - listWidth(innerW) - COLUMN_GAP);
    }

    /** Victim row width inside the 1px well border, minus the scrollbar and its 1px gap when the list scrolls. / 死者行宽。 */
    public static int listRowWidth(int listW, boolean scrolls) {
        return Math.max(1, listW - 2 - (scrolls ? SCROLLBAR_W + 1 : 0));
    }

    /**
     * Two-column cause cell width; a scrolling grid also leaves room for the scrollbar and its 1px gap so the right
     * column never runs under it.
     * 两列死因格宽度；死因区可滚动时额外为滚动条及其 1px 间隙留出空间，右列不会压在滚动条下面。
     */
    public static int causeCellWidth(int gridW, boolean scrolls) {
        int inner = gridW - GRID_INSET * 2 - (scrolls ? SCROLLBAR_W + 1 : 0);
        return Math.max(1, (inner - CELL_GAP) / 2);
    }

    public static int causeLabelWidth(int cellW) {
        return cellW - CELL_TEXT_MARGIN;
    }

    public enum VictimStatus {
        UNGUESSED,
        PENDING,
        SOLVED_KILLER,
        SOLVED_NO_KILLER;

        public boolean solved() {
            return this == SOLVED_KILLER || this == SOLVED_NO_KILLER;
        }
    }

    public static boolean maySend(boolean confirmedServer, boolean channelAvailable, boolean liveProphet) {
        return confirmedServer && channelAvailable && liveProphet;
    }

    public static VictimStatus status(@Nullable ProphecyRecord record) {
        if (record == null) {
            return VictimStatus.UNGUESSED;
        }
        return switch (record.outcome()) {
            case REVEALED_KILLER -> VictimStatus.SOLVED_KILLER;
            case NO_KILLER -> VictimStatus.SOLVED_NO_KILLER;
            case PENDING -> record.excluded().isEmpty() ? VictimStatus.UNGUESSED : VictimStatus.PENDING;
        };
    }

    public static boolean isExcluded(@Nullable ProphecyRecord record, ProphetDeathCauseGroup group) {
        return record != null && group != null && record.excluded().contains(group);
    }

    /** A cause cell is clickable for a selected, unsolved victim unless it was already excluded. / 选中未猜中死者时，未被排除的死因可点击。 */
    public static boolean isCauseSelectable(boolean victimSelected, @Nullable ProphecyRecord record,
                                            ProphetDeathCauseGroup group) {
        return victimSelected && !status(record).solved() && !isExcluded(record, group);
    }

    public static boolean hasEnoughMoney(int balance) {
        return balance >= ProphetRules.PROPHECY_COIN_COST;
    }

    public static boolean canConfirm(boolean submitted, boolean sessionPending, boolean victimSelected,
                                     @Nullable ProphecyRecord record, @Nullable ProphetDeathCauseGroup group,
                                     int balance) {
        return !submitted && sessionPending && group != null
                && isCauseSelectable(victimSelected, record, group) && hasEnoughMoney(balance);
    }
}
