package dev.caecorthus.sparkwitch.client.tarot;

/**
 * Pure layout of the Tarot Reader's result slip: a brass tooltip centred above the actionbar holding the eye-card
 * mark, the verdict stamp and the verdict sentence, plus its fade curve. The icon width is the HUD's and the stamp
 * width and frame reach are the selector's, so all three draw the same pieces. It holds no client types; the renderer
 * measures the sentence and passes the width in.
 * 塔罗牌师结果条的纯布局：居中于动作栏上方的黄铜提示框，内含眼睛卡牌图标、结论印记与结论句，以及渐隐曲线。图标宽度
 * 取自 HUD，印记宽度与外框外扩取自选择界面，三者绘制相同的部件。不含客户端类型，句子宽度由渲染器测量后传入。
 */
public final class TarotReadingSlipLayout {
    /**
     * Distance from the screen bottom to the slip's bottom edge; mirrors the private
     * {@code ControlExpertStatusHudRules.ACTION_BAR_CLEARANCE}, so SparkWitch keeps one "above the actionbar" band.
     * 屏幕底部到结果条底边的距离；与私有的 {@code ControlExpertStatusHudRules.ACTION_BAR_CLEARANCE} 保持一致，
     * 使 SparkWitch 只有一条"动作栏上方"的显示带。
     */
    public static final int ACTION_BAR_CLEARANCE = 76;
    /** Vanilla TextRenderer.fontHeight; the content box is one line tall. 原版字体行高；内容框高一行。 */
    public static final int CONTENT_HEIGHT = 9;
    /** Minimum air between the slip's frame and the screen edge. 结果条外框与屏幕边缘的最小间距。 */
    public static final int SCREEN_MARGIN = 6;
    public static final int ICON_GAP = 4;
    public static final int STAMP_GAP = 3;
    /** The 5x5 stamp's centre row matches the 7x9 icon's centre row. 5x5 印记的中心行与 7x9 图标的中心行对齐。 */
    public static final int STAMP_Y = 2;
    /** Text sits one row below the icon top, as in the HUD title. 文字比图标顶部低一行，与 HUD 标题一致。 */
    public static final int TEXT_Y = 1;
    public static final int TEXT_X =
            TarotDivinationHudLayout.ICON_WIDTH + ICON_GAP + TarotLedgerLayout.STAMP_W + STAMP_GAP;
    public static final long SOLID_MS = 6000L;
    public static final long FADE_MS = 1000L;
    /**
     * Vanilla draws text with alpha below 4 fully opaque (TextRenderer.tweakTransparency), so the slip stops at 4.
     * 原版会把透明度低于 4 的文字按不透明绘制（TextRenderer.tweakTransparency），因此结果条在 4 以下停止绘制。
     */
    public static final int MIN_ALPHA = 4;
    /** The frame reaches as far around the content box as brassTooltip. 外框超出内容区的宽度与 brassTooltip 相同。 */
    private static final int FRAME = TarotLedgerLayout.TIP_PAD;

    private TarotReadingSlipLayout() {
    }

    /** The widest sentence the slip shows on this screen. 此屏幕上结果条可显示的最宽句子。 */
    public static int textBudget(int screenWidth) {
        return screenWidth - 2 * (SCREEN_MARGIN + FRAME) - TEXT_X;
    }

    /**
     * Width left for the argument after the whole verdict; the argument is ellipsized to it, the verdict never is.
     * 完整结论文字之外留给参数的宽度；只按此宽度截断参数，从不截断结论。
     */
    public static int argumentBudget(int screenWidth, int verdictWidth) {
        return Math.max(0, textBudget(screenWidth) - verdictWidth);
    }

    public static Geometry geometry(int screenWidth, int screenHeight, int textWidth) {
        int width = TEXT_X + Math.max(0, textWidth);
        int x = Math.floorDiv(screenWidth - width, 2);
        int y = screenHeight - ACTION_BAR_CLEARANCE - FRAME - CONTENT_HEIGHT;
        return new Geometry(x, y, width, CONTENT_HEIGHT);
    }

    /**
     * Slip alpha {@code elapsedMs} after the reading arrived: solid for {@link #SOLID_MS}, then a linear fade over
     * {@link #FADE_MS}. Zero means "do not draw", including every value under {@link #MIN_ALPHA}.
     * 收到结果 {@code elapsedMs} 毫秒后的透明度：先完全显示 {@link #SOLID_MS}，再在 {@link #FADE_MS} 内线性渐隐。
     * 返回 0 表示不绘制，低于 {@link #MIN_ALPHA} 的值也返回 0。
     */
    public static int alpha(long elapsedMs) {
        long remaining = SOLID_MS + FADE_MS - Math.max(0L, elapsedMs);
        if (remaining >= FADE_MS) {
            return 0xFF;
        }
        int alpha = remaining <= 0L ? 0 : (int) (0xFF * remaining / FADE_MS);
        return alpha < MIN_ALPHA ? 0 : alpha;
    }

    /** Scales a colour's own alpha by {@code alpha} / 255. 按 {@code alpha} / 255 缩放颜色自身的透明度。 */
    public static int withAlpha(int argb, int alpha) {
        return (((argb >>> 24) * alpha / 0xFF) << 24) | (argb & 0xFFFFFF);
    }

    /**
     * The content box handed to brassTooltip; {@code frame*} are its drawn bounds, exclusive right/bottom like
     * {@code DrawContext.fill}.
     * 传给 brassTooltip 的内容框；{@code frame*} 为实际绘制范围，右缘与下缘为开区间，与 fill 一致。
     */
    public record Geometry(int x, int y, int width, int height) {
        public int iconX() {
            return x;
        }

        public int iconY() {
            return y;
        }

        public int stampX() {
            return x + TarotDivinationHudLayout.ICON_WIDTH + ICON_GAP;
        }

        public int stampY() {
            return y + STAMP_Y;
        }

        public int textX() {
            return x + TEXT_X;
        }

        public int textY() {
            return y + TEXT_Y;
        }

        public int frameLeft() {
            return x - FRAME;
        }

        public int frameTop() {
            return y - FRAME;
        }

        public int frameRight() {
            return x + width + FRAME;
        }

        public int frameBottom() {
            return y + height + FRAME;
        }
    }
}
