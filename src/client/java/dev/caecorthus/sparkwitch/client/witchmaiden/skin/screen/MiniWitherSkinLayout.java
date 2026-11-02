package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of {@link MiniWitherSkinScreen} (mockup A "暗夜面板"), from 320×240 GUI pixels up: the title and the
 * status pill on top; the skin list on the left (two fifths of the content width, full height); on the right the
 * square preview stage, its caption and the sound grid; Open Folder / Reload / Done centred at the bottom. The content
 * block is centred and at most {@link #MAX_CONTENT_WIDTH} wide. The stage is as large as the right column allows; the
 * sound grid is 4×2 once each button can hold its label, otherwise 2×4. Stage, caption and grid form one block, centred
 * vertically when the column's width limits the stage.
 * {@link MiniWitherSkinScreen}（方案 A「暗夜面板」）的纯几何布局，从 320×240 GUI 像素起适配：顶部为标题与状态胶囊；左侧为皮肤
 * 列表（内容宽度的五分之二，占满高度）；右侧为方形预览舞台、说明文字与试听网格；底部居中为「打开文件夹 / 重新加载 / 完成」。
 * 内容区居中，最宽 {@link #MAX_CONTENT_WIDTH}。舞台在右列允许的范围内尽量大；每个按钮放得下文字时试听网格为 4×2，否则为 2×4。
 * 舞台、说明文字与网格组成一个整体；舞台受右列宽度限制时，整体垂直居中。
 *
 * @param statusBand where the centred status pill may grow / 居中状态胶囊可占用的区域
 * @param caption    the caption line under the stage, as wide as the right column / 舞台下方的说明文字行，与右列同宽
 * @param sounds     one cell per sound slot, in slot order / 每个声音槽位一个格子，按槽位顺序
 * @param footer     Open Folder, Reload, Done / 打开文件夹、重新加载、完成
 */
public record MiniWitherSkinLayout(int titleY, Rect statusBand, Rect list, Rect stage, Rect caption,
                                   List<Rect> sounds, List<Rect> footer) {
    public static final int MARGIN = 8;
    public static final int MAX_CONTENT_WIDTH = 720;
    public static final int FOOTER_HEIGHT = 18;
    public static final int SOUND_HEIGHT = 15;
    public static final int PILL_HEIGHT = 13;
    /** A sound button narrower than this switches the grid to two columns. / 试听按钮窄于此值时网格改为两列。 */
    public static final int MIN_SOUND_WIDTH = 58;
    public static final int TEXT_HEIGHT = 9;
    static final int TITLE_Y = 7;
    static final int PILL_Y = 19;
    static final int CONTENT_TOP = 38;
    static final int FOOTER_BOTTOM = 7;
    static final int CONTENT_GAP = 7;
    static final int COLUMN_GAP = 8;
    static final int SOUND_GAP = 3;
    static final int CAPTION_HEIGHT = 11;
    static final int GRID_GAP = 5;
    static final int FOOTER_GAP = 6;
    static final int FOOTER_MAX = 100;
    static final int FOOTER_BUTTONS = 3;

    public MiniWitherSkinLayout {
        sounds = List.copyOf(sounds);
        footer = List.copyOf(footer);
    }

    public static MiniWitherSkinLayout compute(int width, int height, int soundCount) {
        int contentWidth = Math.max(0, Math.min(width - 2 * MARGIN, MAX_CONTENT_WIDTH));
        int left = (width - contentWidth) / 2;
        int footerY = height - FOOTER_BOTTOM - FOOTER_HEIGHT;
        int contentBottom = footerY - CONTENT_GAP;
        int contentHeight = Math.max(0, contentBottom - CONTENT_TOP);

        int listWidth = Math.max(0, (contentWidth - COLUMN_GAP) * 2 / 5);
        int rightX = left + listWidth + COLUMN_GAP;
        int rightWidth = Math.max(0, contentWidth - listWidth - COLUMN_GAP);
        Rect list = new Rect(left, CONTENT_TOP, listWidth, contentHeight);

        int columns = (rightWidth - 3 * SOUND_GAP) / 4 >= MIN_SOUND_WIDTH ? 4 : 2;
        int rows = (Math.max(0, soundCount) + columns - 1) / columns;
        int soundWidth = Math.max(0, (rightWidth - (columns - 1) * SOUND_GAP) / columns);
        int gridHeight = rows == 0 ? 0 : rows * SOUND_HEIGHT + (rows - 1) * SOUND_GAP;
        // The stage takes the room left by the caption and the grid, kept square; the block is centred vertically.
        // 舞台占用说明文字与网格之外的空间并保持正方形；整体垂直居中。
        int room = Math.max(0, contentHeight - gridHeight - GRID_GAP - CAPTION_HEIGHT);
        int stageSize = Math.min(rightWidth, room);
        int blockTop = CONTENT_TOP + (room - stageSize) / 2;
        Rect stage = new Rect(rightX + (rightWidth - stageSize) / 2, blockTop, stageSize, stageSize);
        int gridTop = stage.bottom() + CAPTION_HEIGHT + GRID_GAP;
        List<Rect> sounds = new ArrayList<>();
        for (int i = 0; i < soundCount; i++) {
            sounds.add(new Rect(rightX + (i % columns) * (soundWidth + SOUND_GAP),
                    gridTop + (i / columns) * (SOUND_HEIGHT + SOUND_GAP), soundWidth, SOUND_HEIGHT));
        }

        int footerWidth = Math.max(0, Math.min(FOOTER_MAX,
                (contentWidth - (FOOTER_BUTTONS - 1) * FOOTER_GAP) / FOOTER_BUTTONS));
        int footerLeft = (width - (FOOTER_BUTTONS * footerWidth + (FOOTER_BUTTONS - 1) * FOOTER_GAP)) / 2;
        List<Rect> footer = new ArrayList<>();
        for (int i = 0; i < FOOTER_BUTTONS; i++) {
            footer.add(new Rect(footerLeft + i * (footerWidth + FOOTER_GAP), footerY, footerWidth, FOOTER_HEIGHT));
        }
        Rect statusBand = new Rect(MARGIN, PILL_Y, Math.max(0, width - 2 * MARGIN), PILL_HEIGHT);
        Rect caption = new Rect(rightX, stage.bottom() + 2, rightWidth, TEXT_HEIGHT);
        return new MiniWitherSkinLayout(TITLE_Y, statusBand, list, stage, caption, sounds, footer);
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }
}
