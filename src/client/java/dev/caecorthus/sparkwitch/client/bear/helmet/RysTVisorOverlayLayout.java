package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisorGeometry;

/**
 * Pure layout of the visor-down overlay (art contract, {@code bear-full/art/README.md} section 5-6): the overlay texture
 * is stretched over the whole scaled window like vanilla's pumpkin blur, the glass window is a fixed rectangle in
 * normalised screen coordinates that depends on the art option ({@link Option}; the owner picks A, B or C, B is the
 * shipped recommendation), and each crack decal is a square of {@link #DECAL_SIZE} x the screen height centred at the
 * crack's (u, v) inside the window, rotated by its angle and clipped to the window. The crack atlas is 4 x 2 cells
 * (any resolution; UVs are taken in cell units). Switching the option is the {@link #OPTION} line plus the matching
 * {@code visor_overlay.png}.
 * 面罩放下遮罩的纯布局（美术契约，{@code bear-full/art/README.md} 第 5-6 节）：遮罩贴图像原版南瓜模糊一样拉伸铺满整个缩放窗口；
 * 玻璃视窗是归一化屏幕坐标中的固定矩形，取决于美术方案（{@link Option}；所有者在 A、B、C 中选择，随包为推荐的 B）；每个裂纹
 * 贴花是边长为屏幕高度 × {@link #DECAL_SIZE} 的正方形，以裂纹 (u, v) 在视窗内的位置为中心，按其角度旋转并裁剪到视窗内。裂纹图集
 * 为 4 × 2 格（任意分辨率；UV 以格为单位）。切换方案只需改 {@link #OPTION} 一行并换上对应的 {@code visor_overlay.png}。
 */
public final class RysTVisorOverlayLayout {
    /** The art options' glass windows (x0, y0, x1, y1), normalised. / 各美术方案的玻璃视窗（归一化）。 */
    public enum Option {
        /** Thin titanium bevel frame, slight tint. / 细钛合金斜边框，轻微着色。 */
        A(0.100F, 0.305F, 0.900F, 0.690F),
        /** Heavy machined frame with screws (recommended). / 带螺丝的厚重机加工框（推荐）。 */
        B(0.135F, 0.325F, 0.865F, 0.675F),
        /** Soft out-of-focus edge vignette only. / 只有柔和的失焦边缘暗角。 */
        C(0.035F, 0.160F, 0.965F, 0.845F);

        final float x0;
        final float y0;
        final float x1;
        final float y1;

        Option(float x0, float y0, float x1, float y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }
    }

    /** The shipped option (must match {@code textures/gui/bear/visor_overlay.png}). / 随包方案（须与贴图一致）。 */
    public static final Option OPTION = Option.B;
    /** Crack decal edge as a fraction of the screen height. / 裂纹贴花边长占屏幕高度的比例。 */
    public static final float DECAL_SIZE = 0.26F;
    public static final int ATLAS_COLUMNS = 4;
    public static final int ATLAS_ROWS = 2;
    /** Nominal cell size used for UV math (the PNG may be any multiple). / UV 计算所用的名义格尺寸（PNG 可为任意倍数）。 */
    public static final int ATLAS_CELL = 256;

    private RysTVisorOverlayLayout() {
    }

    /** The glass window in scaled pixels. / 缩放像素中的玻璃视窗。 */
    public record Window(int x0, int y0, int x1, int y1) {
        public int width() {
            return x1 - x0;
        }

        public int height() {
            return y1 - y0;
        }
    }

    public static Window window(int screenWidth, int screenHeight) {
        return window(OPTION, screenWidth, screenHeight);
    }

    public static Window window(Option option, int screenWidth, int screenHeight) {
        return new Window(Math.round(option.x0 * screenWidth), Math.round(option.y0 * screenHeight),
                Math.round(option.x1 * screenWidth), Math.round(option.y1 * screenHeight));
    }

    /** Crack centre x for glass {@code u}. / 玻璃 {@code u} 对应的裂纹中心 x。 */
    public static float crackX(Window window, float u) {
        return window.x0() + u * window.width();
    }

    /** Crack centre y for glass {@code v}. / 玻璃 {@code v} 对应的裂纹中心 y。 */
    public static float crackY(Window window, float v) {
        return window.y0() + v * window.height();
    }

    /** Decal edge in scaled pixels (at least 1). / 缩放像素中的贴花边长（至少为 1）。 */
    public static int decalSize(int screenHeight) {
        return Math.max(1, Math.round(DECAL_SIZE * screenHeight));
    }

    /** Any style value wrapped into the art's style range. / 任意样式值回绕到美术样式范围内。 */
    public static int style(int style) {
        return Math.floorMod(style, RysTVisorGeometry.CRACK_STYLE_COUNT);
    }

    /** Atlas cell u (cell units) of {@code style}. / {@code style} 的图集格 u（格单位）。 */
    public static int cellU(int style) {
        return (style(style) % ATLAS_COLUMNS) * ATLAS_CELL;
    }

    /** Atlas cell v (cell units) of {@code style}. / {@code style} 的图集格 v（格单位）。 */
    public static int cellV(int style) {
        return (style(style) / ATLAS_COLUMNS) * ATLAS_CELL;
    }
}
