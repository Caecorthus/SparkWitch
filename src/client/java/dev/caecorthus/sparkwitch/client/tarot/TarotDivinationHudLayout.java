package dev.caecorthus.sparkwitch.client.tarot;

import java.util.List;

public final class TarotDivinationHudLayout {
    private static final int TOP_PADDING = 6;
    private static final int MONEY_ROW_GAP = 5;
    private static final int ROW_GAP = 2;

    private TarotDivinationHudLayout() {
    }

    public static List<Row> rows(TarotDivinationSnapshotState.Snapshot snapshot) {
        return List.of(
                new Row(FactionSlot.CIVILIAN, snapshot.civilianCount()),
                new Row(FactionSlot.KILLER, snapshot.killerCount()),
                new Row(FactionSlot.NEUTRAL, snapshot.neutralCount()),
                new Row(FactionSlot.WITCH, snapshot.witchCount())
        );
    }

    public static int titleY(int fontHeight) {
        return TOP_PADDING + fontHeight + MONEY_ROW_GAP;
    }

    public static int rowY(int rowIndex, int fontHeight) {
        return titleY(fontHeight) + (rowIndex + 1) * (fontHeight + ROW_GAP);
    }

    public enum FactionSlot {
        CIVILIAN,
        KILLER,
        NEUTRAL,
        WITCH
    }

    public record Row(FactionSlot faction, int count) {
    }
}
