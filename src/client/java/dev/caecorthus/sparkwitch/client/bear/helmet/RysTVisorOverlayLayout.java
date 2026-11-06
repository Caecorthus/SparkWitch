package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisorGeometry;

/**
 * Pure layout of the visor-down overlay (Astra art handoff 2026-11-06, "Overlay integration"): the overlay texture is
 * stretched over the whole scaled window like vanilla's pumpkin blur, the glass window is a fixed rectangle in
 * normalised screen coordinates (the nominal soft-edge boundary of the chosen art {@link Option} and the exact crack
 * clipping rectangle), and each crack decal is a square of {@link #DECAL_SIZE} x the screen height centred at the
 * crack's (u, v) inside the window, rotated by its angle and clipped to the window. The crack atlas is 4 x 2 cells
 * (any resolution; UVs are taken in cell units). The window numbers here are the single source of truth for the
 * shipped texture: switching to {@link Option#C2} is the {@link #OPTION} line plus the art package's
 * {@code visor_overlay_c2.png} copied over {@code visor_overlay.png}.
 * 面罩放下遮罩的纯布局（Astra 美术交付 2026-11-06，“Overlay integration”）：遮罩贴图像原版南瓜模糊一样拉伸铺满整个缩放窗口；
 * 玻璃视窗是归一化屏幕坐标中的固定矩形（所选美术方案 {@link Option} 柔和边缘的名义边界，也是裂纹裁剪的精确矩形）；每个裂纹贴花是
 * 边长为屏幕高度 × {@link #DECAL_SIZE} 的正方形，以裂纹 (u, v) 在视窗内的位置为中心，按其角度旋转并裁剪到视窗内。裂纹图集为
 * 4 × 2 格（任意分辨率；UV 以格为单位）。这里的视窗数值是随包贴图的唯一依据：切换到 {@link Option#C2} 只需改 {@link #OPTION}
 * 一行，并用美术包的 {@code visor_overlay_c2.png} 覆盖 {@code visor_overlay.png}。
 */
public final class RysTVisorOverlayLayout {
    /**
     * The art options' glass windows (x0, y0, x1, y1), normalised; both keep option C's soft out-of-focus edge with
     * more of the top and bottom covered. / 各美术方案的玻璃视窗（归一化）；两者都保留方案 C 的柔和失焦边缘，并遮住更多上下部分。
     */
    public enum Option {
        /** Window 48% of the screen height (owner's "C but more covered", shipped). / 视窗高 48%（随包）。 */
        C1(0.035F, 0.270F, 0.965F, 0.750F),
        /** Heavier slit, window 36% of the screen height. / 更窄的观察缝，视窗高 36%。 */
        C2(0.035F, 0.330F, 0.965F, 0.690F);

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
    public static final Option OPTION = Option.C1;
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
