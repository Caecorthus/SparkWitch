package dev.caecorthus.sparkwitch.client.medium;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of the dead's 幽灵的加护 vote screen (owner pick 2026-11-13: 2x2 cards): the panel, the four card
 * rects in blessing order (row-major: SPEED, SHIELD / CORPSE_SIGHT, SANITY), the countdown, rule and footer rows, and
 * the card hit test, so what is clicked is exactly what is painted. It holds no client types; the screen measures the
 * effect lines and passes their count in. GUI px; right and bottom edges are exclusive, like {@code DrawContext.fill}.
 * The panel keeps the 通灵 vote screen's width, title band, asks line and footer.
 * 死者幽灵的加护投票界面的纯几何（所有者 2026-11-13 选定：2x2 卡片）：面板、按加护顺序排列的四张卡片（行优先：速度、
 * 护盾 / 尸体透视、回满理智）、倒计时、刻线与页脚行以及卡片命中判定，保证点击区域与绘制位置一致。不含客户端类型，效果行数
 * 由界面测量后传入。单位为 GUI 像素，右缘与下缘为开区间（与 fill 一致）。面板沿用通灵投票界面的宽度、标题带、提问行与页脚。
 */
public final class MediumBlessingVoteLayout {
    public static final int PANEL_WIDTH = 220;
    public static final int SCREEN_MARGIN = 8;
    /** Cards keep this distance from the frame edge. / 卡片距面板边缘的距离。 */
    public static final int INSET = 9;
    public static final int ASKS_Y = 27;
    public static final int COUNTDOWN_Y = 39;
    public static final int CARDS_Y = 55;
    public static final int COLUMNS = 2;
    public static final int CARD_GAP_X = 8;
    public static final int CARD_GAP_Y = 6;
    /** A card whose effect fits one line; every further line adds {@link #LINE_HEIGHT}. / 效果只占一行时的卡片高度。 */
    public static final int CARD_HEIGHT = 34;
    public static final int LINE_HEIGHT = 10;
    public static final int MAX_EFFECT_LINES = 2;
    public static final int RULE_GAP = 7;
    public static final int FOOTER_GAP = 5;
    public static final int FOOTER_ROW_HEIGHT = 13;
    public static final int BOTTOM_PAD = 6;
    // Card content, relative to the card's top-left. / 卡片内容，相对卡片左上角。
    public static final int ICON_X = 6;
    public static final int ICON_Y = 6;
    public static final int NAME_X = 18;
    public static final int NAME_Y = 7;
    public static final int EFFECT_X = 6;
    public static final int EFFECT_Y = 20;
    public static final int EFFECT_PAD_RIGHT = 6;
    /** The check stamp / padlock sits this far left of the card's right edge. / 对勾 / 挂锁距卡片右缘的距离。 */
    public static final int MARK_RIGHT = 11;
    /** Name to the check / padlock column. / 名称与对勾 / 挂锁列之间的间隔。 */
    public static final int NAME_MARK_GAP = 4;

    private MediumBlessingVoteLayout() {
    }

    /**
     * The layout for a screen of the given size. {@code effectLines} is the most lines any card's effect text needs at
     * {@link Layout#effectWidth()} (clamped to 1..{@link #MAX_EFFECT_LINES}); every card gets that height, so the grid
     * stays even.
     * 给定屏幕尺寸下的布局。{@code effectLines} 为各卡片效果文字在 {@link Layout#effectWidth()} 下所需的最多行数（限制在
     * 1..{@link #MAX_EFFECT_LINES}）；所有卡片使用同一高度，网格保持整齐。
     */
    public static Layout layout(int screenWidth, int screenHeight, int effectLines) {
        int panelWidth = Math.max(2 * INSET + CARD_GAP_X + 2, Math.min(PANEL_WIDTH, screenWidth - 2 * SCREEN_MARGIN));
        int cardWidth = (panelWidth - 2 * INSET - CARD_GAP_X) / COLUMNS;
        int cardHeight = cardHeight(effectLines);
        int ruleY = CARDS_Y + 2 * cardHeight + CARD_GAP_Y + RULE_GAP;
        int footerY = ruleY + FOOTER_GAP;
        int panelHeight = footerY + FOOTER_ROW_HEIGHT + BOTTOM_PAD;
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = Math.max(0, (screenHeight - panelHeight) / 2);
        List<Rect> cards = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int x = i % COLUMNS == 0 ? panelX + INSET : panelX + panelWidth - INSET - cardWidth;
            int y = panelY + CARDS_Y + (i / COLUMNS) * (cardHeight + CARD_GAP_Y);
            cards.add(new Rect(x, y, cardWidth, cardHeight));
        }
        return new Layout(new Rect(panelX, panelY, panelWidth, panelHeight), cards, panelY + COUNTDOWN_Y,
                panelY + ruleY, panelY + footerY);
    }

    /** Card height for {@code effectLines} effect lines (clamped). / {@code effectLines} 行效果对应的卡片高度（有限制）。 */
    public static int cardHeight(int effectLines) {
        int lines = Math.max(1, Math.min(MAX_EFFECT_LINES, effectLines));
        return CARD_HEIGHT + (lines - 1) * LINE_HEIGHT;
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    /**
     * The panel, the four cards in blessing-index order, and the absolute y of the countdown, rule and footer rows.
     * 面板、按加护序号排列的四张卡片，以及倒计时、刻线与页脚行的绝对 y。
     */
    public record Layout(Rect panel, List<Rect> cards, int countdownY, int ruleY, int footerY) {
        public Layout {
            cards = List.copyOf(cards);
        }

        /** The card index under the pointer, or -1 (the gaps hit nothing). / 指针下的卡片序号，没有则为 -1（间隔不命中）。 */
        public int cardAt(double mouseX, double mouseY) {
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).contains(mouseX, mouseY)) {
                    return i;
                }
            }
            return -1;
        }

        /** Width an effect line may use inside a card. / 卡片内效果行可用的宽度。 */
        public int effectWidth() {
            return Math.max(0, cards.getFirst().width() - EFFECT_X - EFFECT_PAD_RIGHT);
        }

        /** Width the name may use before the check / padlock column. / 名称在对勾 / 挂锁列之前可用的宽度。 */
        public int nameWidth() {
            return Math.max(0, cards.getFirst().width() - NAME_X - MARK_RIGHT - NAME_MARK_GAP);
        }
    }
}
