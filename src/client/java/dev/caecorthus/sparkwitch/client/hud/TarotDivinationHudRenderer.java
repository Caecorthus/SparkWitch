package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkfactionapi.api.FactionDefinition;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationClientState;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationHudLayout;
import dev.caecorthus.sparkwitch.client.tarot.TarotDivinationSnapshotState;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Draws the Tarot Reader's divination table under Wathe's money row. The counts are the server snapshot taken when
 * the reading was bought, never live counts, and the client recalculates nothing. The change arrows compare two
 * server snapshots this player already received, so they reveal nothing new. Role-owned presentation, never part of
 * the witch skill inventory panel.
 * 在 Wathe 金币行下方绘制塔罗牌师的占卜表。人数是购买占卜时服务端生成的快照，并非实时人数，客户端不做任何重算。
 * 变化箭头只比较该玩家已收到的两次服务端快照，不会泄露新信息。属于职业自有展示，从不属于魔女技能背包面板。
 */
public final class TarotDivinationHudRenderer {
    private static final int WHITE = 0xFFFFFF;
    /** Dark indigo of the tarot_card item art, so the HUD icon matches the item. 与塔罗牌物品贴图的深靛色一致。 */
    private static final int ICON_FACE = 0xFF21164D;
    private static final int SEPARATOR = 0x99000000 | (InventoryCardPaint.POLISHED & 0xFFFFFF);
    private static final int ZERO_TEXT = 0xFF8C8C8C;
    private static final int ZERO_PIP_ALPHA = 0x80000000;
    private static final int TREND = 0xB0FFFFFF;
    private static final String ELLIPSIS = "…";
    /**
     * Vanilla scoreboard sidebar fallback (0x4C000000); honours the Text Background Opacity option.
     * 原版计分板侧栏的默认值（0x4C000000）；遵循“文字背景不透明度”设置。
     */
    private static final float BACKING_OPACITY = 0.3F;

    // 7x9 tarot card with an eye, one bitmap per colour. 7x9 带眼睛的塔罗牌图标，每种颜色一张位图。
    private static final String[] ICON_GOLD = {
            ".#####.", "#.....#", "#.....#", "#.###.#", "##...##", "#.###.#", "#.....#", "#.....#", ".#####."
    };
    private static final String[] ICON_CORNERS = {"#.....#", "", "", "", "", "", "", "", "#.....#"};
    private static final String[] ICON_FACE_ROWS = {
            "", ".#####.", ".#####.", ".#...#.", "..#.#..", ".#...#.", ".#####.", ".#####.", ""
    };
    private static final String[] ICON_IRIS = {"", "", "", "", "...#..."};
    private static final String[] TREND_UP = {"..#..", ".###.", "#####"};
    private static final String[] TREND_DOWN = {"#####", ".###.", "..#.."};

    private TarotDivinationHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;
        Table table = measure(renderer, context.getScaledWindowWidth()).orElse(null);
        if (table == null) {
            return;
        }

        String title = table.title();
        List<TarotDivinationHudLayout.Row> rows = table.rows();
        String[] names = table.names();
        Text[] counts = table.counts();
        int[] countWidths = table.countWidths();
        int[] colors = table.colors();
        int size = rows.size();
        TarotDivinationHudLayout.Geometry g = table.geometry();
        int left = g.left();
        int right = g.right();
        int backing = client.options.getTextBackgroundColor(BACKING_OPACITY);

        // Every fill in one batch, then text: batched vertices flush per layer, not in call order.
        // 所有填充放入一个批次，然后再绘制文字：批次按渲染层而非调用顺序提交。
        InventoryCardPaint.batch(context, () -> {
            if ((backing >>> 24) != 0) {
                InventoryCardPaint.roundedFill(context, g.backingLeft(), g.backingTop(),
                        g.backingRight() - g.backingLeft(), g.backingBottom() - g.backingTop(), backing);
            }
            drawIcon(context, left, TarotDivinationHudLayout.ICON_Y);
            int separatorY = g.separatorY();
            context.fill(left + 1, separatorY + 1, right, separatorY + 2, shadow(SEPARATOR));
            context.fill(left, separatorY, right - 1, separatorY + 1, SEPARATOR);
            for (int i = 0; i < size; i++) {
                TarotDivinationHudLayout.Row row = rows.get(i);
                int y = g.rowY(i);
                int pipX = left + TarotDivinationHudLayout.PIP_INSET;
                int pipY = y + TarotDivinationHudLayout.PIP_Y_OFFSET;
                if (row.count() <= 0) {
                    // Outline, not only a dimmer hue, marks an empty faction; its shadow keeps it on light scenes.
                    // 空阵营用描边而非仅靠变暗来区分；描边阴影保证其在亮色场景中可见。
                    int outline = ZERO_PIP_ALPHA | (colors[i] & 0xFFFFFF);
                    InventoryCardPaint.diamondOutline(context, pipX + 1, pipY + 1, shadow(outline));
                    InventoryCardPaint.diamondOutline(context, pipX, pipY, outline);
                } else {
                    InventoryCardPaint.diamond(context, pipX + 1, pipY + 1, shadow(colors[i]));
                    InventoryCardPaint.diamond(context, pipX, pipY, colors[i]);
                }
                if (row.trend() != TarotDivinationHudLayout.Trend.NONE) {
                    String[] arrow = row.trend() == TarotDivinationHudLayout.Trend.UP ? TREND_UP : TREND_DOWN;
                    int arrowX = right - countWidths[i] - TarotDivinationHudLayout.TREND_GAP
                            - TarotDivinationHudLayout.TREND_WIDTH;
                    int arrowY = y + TarotDivinationHudLayout.TREND_Y_OFFSET;
                    InventoryCardPaint.bitmap(context, arrowX + 1, arrowY + 1, shadow(TREND), arrow);
                    InventoryCardPaint.bitmap(context, arrowX, arrowY, TREND, arrow);
                }
            }
        });

        int textX = left + TarotDivinationHudLayout.NAME_X;
        context.drawTextWithShadow(renderer, title, textX, TarotDivinationHudLayout.HEADER_Y,
                InventoryCardPaint.POLISHED);
        for (int i = 0; i < size; i++) {
            int y = g.rowY(i);
            boolean zero = rows.get(i).count() <= 0;
            context.drawTextWithShadow(renderer, names[i], textX, y, zero ? ZERO_TEXT : colors[i]);
            context.drawTextWithShadow(renderer, counts[i], right - countWidths[i], y,
                    zero ? ZERO_TEXT : InventoryCardPaint.TEXT_HI);
        }
    }

    /**
     * Where {@link #render} draws the table's backing on a screen this wide, or empty when there is no snapshot.
     * It shares {@link #render}'s measuring path, so the two cannot drift.
     * 在此屏幕宽度下 {@link #render} 绘制表格底板的位置；没有快照时为空。与 {@link #render} 共用同一测量流程，二者不会偏离。
     */
    public static Optional<TarotDivinationHudLayout.Geometry> backingBounds(int screenWidth) {
        return measure(MinecraftClient.getInstance().textRenderer, screenWidth).map(Table::geometry);
    }

    /**
     * The HUD's 7x9 eye-card mark, fills only (callers batch it). Shared so the HUD, the selector and the result slip
     * carry one mark.
     * HUD 的 7x9 眼睛卡牌图标，仅含填充（由调用方批量提交）。HUD、选择界面与结果条共用同一图标。
     */
    public static void drawIcon(DrawContext context, int x, int y) {
        drawIcon(context, x, y, 0xFF);
    }

    /** The icon at {@code alpha}; every icon colour is an opaque token. 以 {@code alpha} 绘制图标；图标颜色均为不透明令牌。 */
    static void drawIcon(DrawContext context, int x, int y, int alpha) {
        int a = alpha << 24;
        InventoryCardPaint.bitmap(context, x, y, a | (InventoryCardPaint.POLISHED & 0xFFFFFF), ICON_GOLD);
        InventoryCardPaint.bitmap(context, x, y, a | (InventoryCardPaint.BRASS_LO & 0xFFFFFF), ICON_CORNERS);
        InventoryCardPaint.bitmap(context, x, y, a | (ICON_FACE & 0xFFFFFF), ICON_FACE_ROWS);
        InventoryCardPaint.bitmap(context, x, y, a | (InventoryCardPaint.TEXT_HI & 0xFFFFFF), ICON_IRIS);
    }

    private static Optional<Table> measure(TextRenderer renderer, int screenWidth) {
        TarotDivinationSnapshotState state = TarotDivinationClientState.snapshotState();
        TarotDivinationSnapshotState.Snapshot snapshot = state.snapshot().orElse(null);
        if (snapshot == null) {
            return Optional.empty();
        }

        List<TarotDivinationHudLayout.Row> rows = TarotDivinationHudLayout.rows(snapshot, state.previous().orElse(null));
        String title = fit(renderer, Text.translatable("hud.sparkwitch.tarot.title").getString(),
                TarotDivinationHudLayout.TITLE_MAX_WIDTH);
        int size = rows.size();
        String[] names = new String[size];
        Text[] counts = new Text[size];
        int[] countWidths = new int[size];
        int[] colors = new int[size];
        int nameWidth = 0;
        int countWidth = 0;
        for (int i = 0; i < size; i++) {
            TarotDivinationHudLayout.Row row = rows.get(i);
            names[i] = fit(renderer, Text.translatable(nameKey(row.faction())).getString(),
                    TarotDivinationHudLayout.NAME_MAX_WIDTH);
            nameWidth = Math.max(nameWidth, renderer.getWidth(names[i]));
            MutableText count = Text.literal(TarotDivinationHudLayout.displayCount(row.count()));
            counts[i] = TarotDivinationHudLayout.boldCount(row.count()) ? count.formatted(Formatting.BOLD) : count;
            countWidths[i] = renderer.getWidth(counts[i]);
            countWidth = Math.max(countWidth, countWidths[i]);
            colors[i] = 0xFF000000 | factionColor(factionId(row.faction()));
        }
        int countReserveWidth = renderer.getWidth(
                Text.literal(TarotDivinationHudLayout.COUNT_RESERVE_TEXT).formatted(Formatting.BOLD));
        TarotDivinationHudLayout.Geometry geometry = TarotDivinationHudLayout.geometry(
                screenWidth, renderer.getWidth(title), nameWidth, countWidth, countReserveWidth);
        return Optional.of(new Table(title, rows, names, counts, countWidths, colors, geometry));
    }

    /**
     * House ellipsis, minus the space a word-boundary cut leaves before "…" (e.g. "Divination Result…"). Shared by
     * the HUD, the result slip and the selector so all three cut text alike.
     * 使用共享省略号截断，并去掉在词边界截断时留在 "…" 前的空格。HUD、结果条与选择界面共用，截断方式一致。
     */
    public static String fit(TextRenderer renderer, String text, int maxWidth) {
        String fitted = InventoryCardPaint.ellipsize(renderer, text, maxWidth);
        if (fitted.equals(text) || !fitted.endsWith(ELLIPSIS)) {
            return fitted;
        }
        return fitted.substring(0, fitted.length() - ELLIPSIS.length()).stripTrailing() + ELLIPSIS;
    }

    /** Vanilla text-shadow colour, alpha kept. 原版文字阴影颜色，保留透明度。 */
    private static int shadow(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
    }

    private static String nameKey(TarotDivinationHudLayout.FactionSlot faction) {
        return switch (faction) {
            case CIVILIAN -> "hud.sparkwitch.tarot.faction.civilian";
            case KILLER -> "hud.sparkwitch.tarot.faction.killer";
            case NEUTRAL -> "hud.sparkwitch.tarot.faction.neutral";
            case WITCH -> "hud.sparkwitch.tarot.faction.witch";
        };
    }

    private static Identifier factionId(TarotDivinationHudLayout.FactionSlot faction) {
        return switch (faction) {
            case CIVILIAN -> FactionIds.CIVILIAN;
            case KILLER -> FactionIds.KILLER;
            case NEUTRAL -> FactionIds.NEUTRAL;
            case WITCH -> SparkWitchFactions.WITCH;
        };
    }

    /**
     * A faction's RGB from SparkFactionAPI (white when unregistered), no alpha. The HUD pips and the selector's
     * section marks share it so a faction reads the same everywhere.
     * 来自 SparkFactionAPI 的阵营 RGB（未注册时为白色），不含透明度。HUD 圆点与选择界面分区标记共用，同一阵营处处同色。
     */
    public static int factionColor(Identifier factionId) {
        return SparkFactionApi.getFaction(factionId)
                .map(FactionDefinition::color)
                .orElse(WHITE) & 0xFFFFFF;
    }

    /** One measured table: what {@link #render} draws and where. 一次测量得到的表格：绘制内容与位置。 */
    private record Table(
            String title,
            List<TarotDivinationHudLayout.Row> rows,
            String[] names,
            Text[] counts,
            int[] countWidths,
            int[] colors,
            TarotDivinationHudLayout.Geometry geometry
    ) {
    }
}
