package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of {@link MiniWitherSkinScreen}, from 320×240 (GUI-scaled) up: title and status line on top, the skin
 * list with the sound preview grid under it on the left, the preview square on the right, and Open Folder / Reload /
 * Done at the bottom. The content block is centred and at most {@link #MAX_CONTENT_WIDTH} wide; the sound grid uses
 * four columns once the list column is wide enough, otherwise two.
 * {@link MiniWitherSkinScreen} 的纯几何布局，从 320×240（GUI 缩放后）起适配：顶部为标题与状态行，左侧为皮肤列表及其下方的
 * 试听按钮网格，右侧为预览方框，底部为「打开文件夹 / 重新加载 / 完成」。内容区居中，最宽 {@link #MAX_CONTENT_WIDTH}；左列足够宽时
 * 试听网格为四列，否则为两列。
 *
 * @param statusWidth the status line's maximum width, centred on the screen / 状态行最大宽度（居中）
 * @param sounds      one cell per sound slot, in slot order / 每个声音槽位一个格子，按槽位顺序
 * @param footer      Open Folder, Reload, Done / 打开文件夹、重新加载、完成
 */
public record MiniWitherSkinLayout(int titleY, int statusY, int statusWidth, Rect list, Rect preview,
                                   int soundsLabelX, int soundsLabelY, List<Rect> sounds, List<Rect> footer) {
    public static final int MARGIN = 8;
    public static final int MAX_CONTENT_WIDTH = 420;
    public static final int PREVIEW_SIZE = 110;
    public static final int BUTTON_HEIGHT = 20;
    public static final int SOUND_HEIGHT = 14;
    static final int TITLE_Y = 8;
    static final int STATUS_Y = 20;
    static final int CONTENT_TOP = 34;
    static final int FOOTER_BOTTOM = 6;
    static final int GAP = 6;
    static final int COLUMN_GAP = 8;
    static final int SOUND_GAP = 2;
    static final int WIDE_SOUND_MIN = 70;
    static final int LABEL_HEIGHT = 12;
    static final int LIST_GAP = 4;
    static final int FOOTER_GAP = 4;
    static final int FOOTER_MAX = 100;
    static final int FOOTER_BUTTONS = 3;

    public MiniWitherSkinLayout {
        sounds = List.copyOf(sounds);
        footer = List.copyOf(footer);
    }

    public static MiniWitherSkinLayout compute(int width, int height, int soundCount) {
        int contentWidth = Math.max(0, Math.min(width - 2 * MARGIN, MAX_CONTENT_WIDTH));
        int left = (width - contentWidth) / 2;
        int footerY = height - FOOTER_BOTTOM - BUTTON_HEIGHT;
        int contentBottom = footerY - GAP;
        int contentHeight = Math.max(0, contentBottom - CONTENT_TOP);

        // The preview keeps its size first, but never takes more than half the content width.
        // 预览方框优先保持尺寸，但最多占内容宽度的一半。
        int previewSize = Math.max(0, Math.min(PREVIEW_SIZE, Math.min(contentHeight, contentWidth / 2)));
        int listWidth = Math.max(0, contentWidth - previewSize - COLUMN_GAP);
        Rect preview = new Rect(left + listWidth + COLUMN_GAP, CONTENT_TOP + (contentHeight - previewSize) / 2,
                previewSize, previewSize);

        int columns = listWidth >= 4 * WIDE_SOUND_MIN + 3 * SOUND_GAP ? 4 : 2;
        int rows = (Math.max(0, soundCount) + columns - 1) / columns;
        int soundWidth = Math.max(0, (listWidth - (columns - 1) * SOUND_GAP) / columns);
        int gridHeight = rows == 0 ? 0 : rows * SOUND_HEIGHT + (rows - 1) * SOUND_GAP;
        int gridTop = contentBottom - gridHeight;
        List<Rect> sounds = new ArrayList<>();
        for (int i = 0; i < soundCount; i++) {
            sounds.add(new Rect(left + (i % columns) * (soundWidth + SOUND_GAP),
                    gridTop + (i / columns) * (SOUND_HEIGHT + SOUND_GAP), soundWidth, SOUND_HEIGHT));
        }
        int labelY = gridTop - LABEL_HEIGHT;
        Rect list = new Rect(left, CONTENT_TOP, listWidth, Math.max(0, labelY - LIST_GAP - CONTENT_TOP));

        int footerWidth = Math.max(0, Math.min(FOOTER_MAX,
                (contentWidth - (FOOTER_BUTTONS - 1) * FOOTER_GAP) / FOOTER_BUTTONS));
        int footerLeft = (width - (FOOTER_BUTTONS * footerWidth + (FOOTER_BUTTONS - 1) * FOOTER_GAP)) / 2;
        List<Rect> footer = new ArrayList<>();
        for (int i = 0; i < FOOTER_BUTTONS; i++) {
            footer.add(new Rect(footerLeft + i * (footerWidth + FOOTER_GAP), footerY, footerWidth, BUTTON_HEIGHT));
        }
        return new MiniWitherSkinLayout(TITLE_Y, STATUS_Y, Math.max(0, width - 2 * MARGIN), list, preview, left,
                labelY, sounds, footer);
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

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }
}
