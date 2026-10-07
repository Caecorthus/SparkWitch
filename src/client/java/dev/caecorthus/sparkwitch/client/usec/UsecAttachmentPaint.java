package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentLayout.Column;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentLayout.Rect;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.BoltStatus;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Card;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentRifleArt.Ink;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentRifleArt.Run;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static dev.caecorthus.sparkwitch.client.usec.UsecAttachmentLayout.*;

/**
 * Client only. The owner-picked U2 "field manual blueprint" style (2026-10-07) of the USEC attachment screen: a navy
 * drafting sheet with a fine grid, pale lines, cyan accents for live slots, dotted outlines for unavailable buttons,
 * numbered callout balloons, dimension lines and a dash-dot bore line. Every colour and shape of the screen lives here
 * and every position in {@link UsecAttachmentLayout}, so a restyle touches these two classes only. Primitives are
 * fill-only and never draw text; the screen draws text afterwards in the colours exposed here. Shapes reuse
 * {@link InventoryCardPaint} helpers (which must stay byte-identical with SparkTraits, so nothing is added there).
 * 仅客户端。所有者选定（2026-10-07）的 USEC 配件界面 U2「野战手册蓝图」样式：深海军蓝图纸、细网格、浅色线条、有效槽位用青色
 * 强调、不可用按钮为点线框、编号引出圈、尺寸线与点划线枪膛中心线。界面的所有颜色与形状都在此处，所有位置都在
 * {@link UsecAttachmentLayout}，换皮只需改动这两个类。原语只做填充、从不绘制文字；界面随后用这里给出的颜色绘制文字。图形复用
 * {@link InventoryCardPaint} 的辅助方法（须与 SparkTraits 逐字节一致，因此不在其中添加任何内容）。
 */
public final class UsecAttachmentPaint {
    // ---- tokens (ARGB, README U2) ----
    public static final int SCRIM = 0xB003080F;
    public static final int SHADOW = 0x66000000;
    public static final int BODY = 0xFF0E1F33, BODY_DK = 0xFF0A1828;
    public static final int GRID = 0xFF12273F, GRID_MAJ = 0xFF18324F;
    public static final int LINE = 0xFFD3E9F6, LINE_DIM = 0xFF7DA2BF, LINE_FAINT = 0xFF33557A;
    public static final int CHAIN = 0xFF4F7394;
    public static final int CYAN = 0xFF8FD8F5, CYAN_DK = 0xFF3E7FA6;
    public static final int TEXT = 0xFFE6F2FA, TEXT_HI = 0xFFFFFFFF, MUTED = 0xFF9CB8CE, FAINT = 0xFF6E8EAA;
    public static final int OFF = 0xFF4F6E8C;
    public static final int READY = 0xFFA9E98C, COOL = 0xFFE3CC94;
    public static final int TIP_BG = 0xF40A1726;
    public static final int HOVER = 0x308FD8F5, ROW_HOVER = 0x148FD8F5;
    /** Live slot fill: BODY with 10% cyan. 有效槽位填充：BODY 混入 10% 青色。 */
    public static final int SLOT_FILL = InventoryCardPaint.mix(BODY, CYAN, 0.10);
    public static final int ROLE = 0xFF000000 | UsecRules.COLOR;

    // ---- glyphs ----
    static final String[] RETICLE = {"...###...", ".##.#.##.", ".#..#..#.", "#.......#", "###...###",
            "#.......#", ".#..#..#.", ".##.#.##.", "...###..."};
    static final String[] BALLOON = {"...#####...", "..#.....#..", ".#.......#.", "#.........#", "#.........#",
            "#.........#", "#.........#", "#.........#", ".#.......#.", "..#.....#..", "...#####..."};
    static final String[][] DIGITS = {
            {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."},
            {".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"},
            {".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."}};
    static final String[] ARROW_L = {"...#", ".###", "####", ".###", "...#"};
    static final String[] ARROW_R = {"#...", "###.", "####", "###.", "#..."};
    static final String[] CHECK = {"....#", "...#.", "#.#..", ".#...", "....."};
    static final String[] HOURGLASS = {"#####", ".#.#.", "..#..", ".###.", "#####"};
    static final String[] RING = {".###.", "#...#", "#...#", "#...#", ".###."};
    /** Dash-dot centre line: long dash, gap, dot, gap. 点划线：长划、空、点、空。 */
    static final boolean[] CHAIN_PATTERN = {true, true, true, true, true, true, true, false, false, true, false, false};

    /** Button states. 按钮状态。 */
    public enum ButtonState {
        OFF,
        ON,
        HOT
    }

    private UsecAttachmentPaint() {
    }

    // ---- text colours / 文字颜色 ----

    public static int statusColor(BoltStatus status) {
        return switch (status) {
            case READY -> READY;
            case BOLTING, LOCKED -> COOL;
            case EMPTY -> MUTED;
        };
    }

    public static int buttonTextColor(ButtonState state) {
        return switch (state) {
            case OFF -> OFF;
            case ON -> TEXT;
            case HOT -> TEXT_HI;
        };
    }

    /**
     * A round label's colour on a button, lifted when hot; a disabled button greys its whole label (OFF).
     * 按钮上子弹标签的颜色，悬停时提亮；不可用按钮整段标签为灰色（OFF）。
     */
    public static int ammoColor(UsecAmmoType type, ButtonState state) {
        int color = 0xFF000000 | type.color();
        return switch (state) {
            case OFF -> OFF;
            case ON -> color;
            case HOT -> InventoryCardPaint.mix(color, 0xFFFFFFFF, 0.25);
        };
    }

    // ---- sheet / 图纸 ----

    public static void scrim(DrawContext c, int width, int height) {
        c.fill(0, 0, width, height, SCRIM);
    }

    /**
     * The drafting sheet: shadow, navy body, outer trim, the 8 px grid (major every 40), inner frame, zone ticks, the
     * title rule, the column divider and the footer rule. 图纸：投影、海军蓝底、外框、8 像素网格（每 40 像素主线）、内框、
     * 分区刻度、标题栏线、分栏线与页脚线。
     */
    public static void sheet(DrawContext c) {
        InventoryCardPaint.batch(c, () -> {
            c.fill(PANEL_W, 2, PANEL_W + 1, PANEL_H, SHADOW);
            c.fill(2, PANEL_H, PANEL_W, PANEL_H + 1, SHADOW);
            c.fill(0, 0, PANEL_W, PANEL_H, BODY);
            rect(c, 0, 0, PANEL_W, PANEL_H, LINE_DIM);
            for (int x = GRID_X0; x < GRID_X1; x += GRID_STEP) {
                c.fill(x, GRID_Y0, x + 1, GRID_Y1, (x - GRID_X0) % GRID_MAJOR == 0 ? GRID_MAJ : GRID);
            }
            for (int y = GRID_Y0; y < GRID_Y1; y += GRID_STEP) {
                c.fill(GRID_X0, y, GRID_X1, y + 1, (y - GRID_Y0) % GRID_MAJOR == 0 ? GRID_MAJ : GRID);
            }
            rect(c, INNER, INNER, PANEL_W - 2 * INNER, PANEL_H - 2 * INNER, LINE);
            for (int k = 1; INNER + ZONE_STEP * k < PANEL_W - INNER; k++) {
                int x = INNER + ZONE_STEP * k;
                c.fill(x, 1, x + 1, INNER, LINE_DIM);
                c.fill(x, PANEL_H - INNER, x + 1, PANEL_H - 1, LINE_DIM);
            }
            for (int k = 1; INNER + ZONE_STEP * k < PANEL_H - INNER; k++) {
                int y = INNER + ZONE_STEP * k;
                c.fill(1, y, INNER, y + 1, LINE_DIM);
                c.fill(PANEL_W - INNER, y, PANEL_W - 1, y + 1, LINE_DIM);
            }
            c.fill(GRID_X0, TITLE_RULE_Y, GRID_X1, TITLE_RULE_Y + 1, LINE);
            c.fill(DIVIDER_X, GRID_Y0, DIVIDER_X + 1, GRID_Y1, LINE);
            c.fill(GRID_X0, FOOTER_RULE_Y, DIVIDER_X, FOOTER_RULE_Y + 1, LINE);
            c.fill(GRID_X0, FOOTER_RULE_Y + 1, DIVIDER_X, GRID_Y1, BODY);
            InventoryCardPaint.bitmap(c, TITLE_ICON_X, TITLE_ICON_Y, CYAN, RETICLE);
        });
    }

    /** 5x5 ringed gem (USEC tag, notes). 5×5 带圈宝石（USEC 标签、注释）。 */
    public static void gem(DrawContext c, int x, int y, int rgb) {
        int core = 0xFF000000 | rgb;
        InventoryCardPaint.batch(c, () -> {
            InventoryCardPaint.bitmap(c, x, y, LINE_DIM, RING);
            c.fill(x + 1, y + 1, x + 4, y + 4, core);
            c.fill(x + 1, y + 1, x + 2, y + 2, InventoryCardPaint.mix(core, 0xFFFFFFFF, 0.45));
        });
    }

    // ---- side view / 主视图 ----

    /**
     * The rifle line art in drafting inks: BODY fill (hides the grid), pale outline, dim details; the inserted
     * magazine and a fitted suppressor are live slots (cyan line, tinted fill). {@code focus} brightens one slot.
     * 以制图墨色绘制步枪线稿：BODY 填充（遮住网格）、浅色轮廓、暗色细节；已装弹匣与已装消音器为有效槽位（青色轮廓、染色填充）。
     * {@code focus} 让一个槽位更亮。
     */
    public static void rifle(DrawContext c, UsecRifleState state, @Nullable Card focus) {
        boolean suppressed = state.suppressor();
        boolean magazine = state.hasMagazine();
        List<Run> runs = UsecAttachmentRifleArt.runs(suppressed, magazine);
        InventoryCardPaint.batch(c, () -> {
            for (Run run : runs) {
                int x = rifleX(run.x());
                int y = rifleY(run.y());
                c.fill(x, y, x + run.length(), y + 1, ink(run.ink(), suppressed, focus));
            }
        });
    }

    static int ink(Ink ink, boolean suppressed, @Nullable Card focus) {
        return switch (ink) {
            case BODY_FILL -> BODY;
            case LINE -> LINE;
            case DETAIL -> LINE_DIM;
            case MAG_FILL -> focus == Card.MAGAZINE ? InventoryCardPaint.mix(BODY, CYAN, 0.22) : SLOT_FILL;
            case MAG_LINE -> focus == Card.MAGAZINE ? TEXT_HI : CYAN;
            case MUZZLE_FILL -> !suppressed ? BODY
                    : focus == Card.MUZZLE ? InventoryCardPaint.mix(BODY, CYAN, 0.22) : SLOT_FILL;
            case MUZZLE_LINE -> !suppressed ? LINE : focus == Card.MUZZLE ? TEXT_HI : CYAN;
            case MAG_GHOST -> LINE_FAINT;
        };
    }

    /**
     * Hidden chamber (dashed, behind the receiver) with the chambered cartridge in its ammo colour.
     * 隐藏的弹膛（机匣后的虚线框），膛内子弹以其弹种颜色绘出。
     */
    public static void hiddenChamber(DrawContext c, @Nullable UsecAmmoType chamber) {
        int x0 = rifleX(CHAMBER_X0);
        int x1 = rifleX(CHAMBER_X1);
        int y = rifleY(19);
        InventoryCardPaint.batch(c, () -> {
            dashedRect(c, x0, y, x1 - x0 + 1, 7, LINE_DIM, 2, 1);
            if (chamber != null) {
                int color = 0xFF000000 | chamber.color();
                dashedH(c, x0 + 2, x0 + 9, y + 1, color, 2, 1);
                dashedH(c, x0 + 2, x0 + 9, y + 5, color, 2, 1);
                c.fill(x0 + 2, y + 1, x0 + 3, y + 6, color);
                c.fill(x0 + 9, y + 2, x0 + 11, y + 5, color);
            }
        });
    }

    /**
     * Dash-dot centre line along the bore. It never overwrites an outline or detail pixel; draw it before
     * {@link #hiddenChamber}, which then covers it.
     * 沿枪膛的点划中心线；从不覆盖轮廓或细节像素。须在 {@link #hiddenChamber} 之前绘制，由弹膛覆盖其上。
     */
    public static void boreLine(DrawContext c, UsecRifleState state) {
        int from = -3;
        int to = UsecAttachmentRifleArt.WIDTH + 2;
        InventoryCardPaint.batch(c, () -> {
            for (int x = from; x < to; x++) {
                if (!CHAIN_PATTERN[(x - from) % CHAIN_PATTERN.length]) {
                    continue;
                }
                Ink ink = UsecAttachmentRifleArt.inkAt(state.suppressor(), state.hasMagazine(), x, BORE_Y);
                if (ink == null || ink == Ink.BODY_FILL || ink == Ink.MAG_FILL || ink == Ink.MUZZLE_FILL) {
                    c.fill(rifleX(x), rifleY(BORE_Y), rifleX(x) + 1, rifleY(BORE_Y) + 1, CHAIN);
                }
            }
        });
    }

    /**
     * Overall-length and barrel-length dimensions: extension lines, arrowed dimension lines and a BODY plate under
     * each label (the screen draws the label text on it). 全长与枪管长尺寸：引出线、带箭头的尺寸线，以及每个标签下的 BODY
     * 底板（标签文字由界面绘制）。
     */
    public static void dimensions(DrawContext c, boolean suppressed, int topLabelWidth, int bottomLabelWidth) {
        int bx = rifleX(BUTT_TOP_X);
        int mx = rifleX(MUZZLE_X);
        int b0 = rifleX(CHAMBER_X0);
        int b1 = rifleX(BARREL_END_X);
        InventoryCardPaint.batch(c, () -> {
            c.fill(bx, DIM_TOP_Y - 2, bx + 1, rifleY(BUTT_TOP_Y - 1), LINE_FAINT);
            c.fill(mx, DIM_TOP_Y - 2, mx + 1, rifleY(17), LINE_FAINT);
            dimension(c, bx, mx + 1, DIM_TOP_Y, topLabelWidth);
            c.fill(b0, rifleY(38), b0 + 1, DIM_BOTTOM_Y + 3, LINE_FAINT);
            c.fill(b1, rifleY(suppressed ? 29 : 28), b1 + 1, DIM_BOTTOM_Y + 3, LINE_FAINT);
            dimension(c, b0, b1 + 1, DIM_BOTTOM_Y, bottomLabelWidth);
        });
    }

    private static void dimension(DrawContext c, int x1, int x2, int y, int labelWidth) {
        c.fill(x1 + 1, y, x2, y + 1, LINE_DIM);
        InventoryCardPaint.bitmap(c, x1, y - 2, LINE, ARROW_L);
        InventoryCardPaint.bitmap(c, x2 - 3, y - 2, LINE, ARROW_R);
        int cx = (x1 + x2) / 2;
        int ty = y - 9;
        c.fill(cx - labelWidth / 2 - 2, ty - 1, cx + labelWidth / 2 + 2, ty + 8, BODY);
    }

    /** Centre x and top y of a dimension label. 尺寸标签的中心 x 与顶部 y。 */
    public static int dimensionLabelX(boolean top) {
        return top ? (rifleX(BUTT_TOP_X) + rifleX(MUZZLE_X) + 1) / 2 : (rifleX(CHAMBER_X0) + rifleX(BARREL_END_X) + 1) / 2;
    }

    public static int dimensionLabelY(boolean top) {
        return (top ? DIM_TOP_Y : DIM_BOTTOM_Y) - 9;
    }

    /**
     * Numbered callout balloon with its leader to the part (2 x 2 dot). A live slot is pale, an empty one dim; the
     * hovered box's balloon turns cyan. 编号引出圈及其指向零件的引线（2×2 圆点）。有效槽位为浅色、空槽位为暗色；悬停详图框
     * 对应的引出圈变为青色。
     */
    public static void callout(DrawContext c, Card card, boolean live, boolean focus, boolean suppressed) {
        int[] leader = UsecAttachmentLayout.leader(card, suppressed);
        Rect at = UsecAttachmentLayout.balloon(card);
        balloon(c, at.x(), at.y(), UsecAttachmentLayout.number(card), live, focus);
        InventoryCardPaint.batch(c, () -> {
            line(c, leader[0], leader[1], leader[2], leader[3], focus ? CYAN : LINE_DIM);
            c.fill(leader[2] - 1, leader[3] - 1, leader[2] + 1, leader[3] + 1, focus ? CYAN : LINE);
        });
    }

    public static void balloon(DrawContext c, int x, int y, int number, boolean live, boolean focus) {
        int ring = focus ? CYAN : live ? LINE : LINE_DIM;
        int digit = focus || live ? TEXT_HI : MUTED;
        InventoryCardPaint.batch(c, () -> {
            for (int row = 1; row < BALLOON.length - 1; row++) {
                String line = BALLOON[row];
                c.fill(x + line.indexOf('#') + 1, y + row, x + line.lastIndexOf('#'), y + row + 1, BODY);
            }
            InventoryCardPaint.bitmap(c, x, y, ring, BALLOON);
            InventoryCardPaint.bitmap(c, x + 3, y + 2, digit, DIGITS[Math.max(0, Math.min(2, number - 1))]);
        });
    }

    // ---- detail boxes / 详图框 ----

    public static void box(DrawContext c, Rect box, boolean hovered) {
        InventoryCardPaint.batch(c, () -> {
            c.fill(box.x(), box.y(), box.right(), box.bottom(), BODY);
            if (hovered) {
                c.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, ROW_HOVER);
            }
            rect(c, box.x(), box.y(), box.w(), box.h(), hovered ? CYAN_DK : LINE_DIM);
            c.fill(box.x() + BOX_HEADER_W, box.y() + 1, box.x() + BOX_HEADER_W + 1, box.bottom() - 1, LINE_FAINT);
        });
    }

    /** Item well: dark square with crop marks. 物品井：带裁切角标的深色方块。 */
    public static void well(DrawContext c, Rect well) {
        InventoryCardPaint.batch(c, () -> {
            c.fill(well.x(), well.y(), well.right(), well.bottom(), BODY_DK);
            cropMarks(c, well.x(), well.y(), well.w(), well.h(), LINE_DIM);
        });
    }

    /** Empty-slot socket: a dashed 16 x 16 outline in the well. 空槽位插座：井内 16×16 虚线框。 */
    public static void socket(DrawContext c, int x, int y) {
        InventoryCardPaint.batch(c, () -> dashedRect(c, x, y, 16, 16, LINE_FAINT, 2, 1));
    }

    /** Firing-order cells, next round first (left). 发射顺序格，下一发在最左。 */
    public static int pips(DrawContext c, int x, int y, List<UsecAmmoType> firingOrder, int capacity) {
        InventoryCardPaint.batch(c, () -> {
            for (int i = 0; i < capacity; i++) {
                int cx = x + i * 6;
                if (i < firingOrder.size()) {
                    int color = 0xFF000000 | firingOrder.get(i).color();
                    c.fill(cx, y, cx + 5, y + 5, color);
                    c.fill(cx, y, cx + 5, y + 1, InventoryCardPaint.mix(color, 0xFFFFFFFF, 0.35));
                } else {
                    rect(c, cx, y, 5, 5, LINE_FAINT);
                }
            }
        });
        return capacity * 6 - 1;
    }

    /**
     * Drafting button: OFF is a dotted outline, ON a dim line box, HOT a cyan box with a soft fill and corner
     * notches. 制图风按钮：OFF 为点线框，ON 为暗色线框，HOT 为带浅填充与角点的青色框。
     */
    public static void button(DrawContext c, Rect button, ButtonState state) {
        int x = button.x();
        int y = button.y();
        int w = button.w();
        int h = button.h();
        InventoryCardPaint.batch(c, () -> {
            c.fill(x, y, x + w, y + h, BODY);
            switch (state) {
                case OFF -> dottedRect(c, x, y, w, h, LINE_FAINT);
                case ON -> rect(c, x, y, w, h, LINE_DIM);
                case HOT -> {
                    c.fill(x + 1, y + 1, x + w - 1, y + h - 1, HOVER);
                    rect(c, x, y, w, h, CYAN);
                    c.fill(x - 1, y - 1, x, y, CYAN);
                    c.fill(x + w, y - 1, x + w + 1, y, CYAN);
                    c.fill(x - 1, y + h, x, y + h + 1, CYAN);
                    c.fill(x + w, y + h, x + w + 1, y + h + 1, CYAN);
                }
            }
        });
    }

    // ---- parts table / 明细表 ----

    /**
     * Table rules: top and header lines and the column dividers down to {@code bottom} (see {@link #tableClose}).
     * 表格线：顶线、表头线，以及延伸到 {@code bottom} 的列分隔线（收尾线见 {@link #tableClose}）。
     */
    public static void table(DrawContext c, int bottom) {
        InventoryCardPaint.batch(c, () -> {
            c.fill(BOM_X1, TABLE_TOP + 1, BOM_X2, bottom, BODY);
            c.fill(BOM_X1, TABLE_TOP, BOM_X2, TABLE_TOP + 1, LINE);
            c.fill(BOM_X1, HEADER_RULE_Y, BOM_X2, HEADER_RULE_Y + 1, LINE);
            for (Column column : Column.values()) {
                int to = UsecAttachmentLayout.columnTo(column);
                if (to < BOM_X2) {
                    c.fill(to, TABLE_TOP + 1, to + 1, bottom, LINE_DIM);
                }
            }
        });
    }

    /** The table's closing line, drawn after the clipped rows. 表格收尾线，在裁剪后的各行之后绘制。 */
    public static void tableClose(DrawContext c, int bottom) {
        c.fill(BOM_X1, bottom - 1, BOM_X2, bottom, LINE);
    }

    /** Row hover tint and the faint rule under the row. 行悬停底色与行下细线。 */
    public static void row(DrawContext c, Rect row, boolean hovered) {
        InventoryCardPaint.batch(c, () -> {
            if (hovered) {
                c.fill(row.x(), row.y(), row.right(), row.bottom() - 1, ROW_HOVER);
            }
            c.fill(row.x(), row.bottom() - 1, row.right(), row.bottom(), LINE_FAINT);
        });
    }

    public static void scrollbar(DrawContext c, Rect rod, Rect thumb) {
        InventoryCardPaint.batch(c, () -> {
            c.fill(rod.x(), rod.y(), rod.right(), rod.bottom(), LINE_FAINT);
            c.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), CYAN);
        });
    }

    /** Title block grid under the notes. 注释下方的标题栏表格。 */
    public static void titleBlock(DrawContext c) {
        int bottom = GRID_Y1;
        InventoryCardPaint.batch(c, () -> {
            c.fill(BOM_X1, BLOCK_TOP + 1, BOM_X2, bottom, BODY);
            c.fill(BOM_X1, BLOCK_TOP, BOM_X2, BLOCK_TOP + 1, LINE);
            c.fill(BOM_X1, BLOCK_TOP + BLOCK_ROW, BOM_X2, BLOCK_TOP + BLOCK_ROW + 1, LINE_DIM);
            c.fill(BOM_X1, BLOCK_TOP + 2 * BLOCK_ROW, BOM_X2, BLOCK_TOP + 2 * BLOCK_ROW + 1, LINE_DIM);
            c.fill(BLOCK_SPLIT_X, BLOCK_TOP + 1, BLOCK_SPLIT_X + 1, bottom, LINE_DIM);
            c.fill(BLOCK_COL2_X, BLOCK_TOP + 1, BLOCK_COL2_X + 1, BLOCK_TOP + 2 * BLOCK_ROW, LINE_DIM);
            c.fill(BLOCK_COL3_X, BLOCK_TOP + 1, BLOCK_COL3_X + 1, BLOCK_TOP + 2 * BLOCK_ROW, LINE_DIM);
        });
    }

    // ---- footer / 页脚 ----

    /** Status pill frame and glyph; returns its width. 状态胶囊外框与图标；返回宽度。 */
    public static int pill(DrawContext c, int x, int y, int labelWidth, BoltStatus status) {
        int color = statusColor(status);
        int w = 3 + 5 + 2 + labelWidth + 3;
        InventoryCardPaint.batch(c, () -> {
            rect(c, x, y, w, BUTTON_H, InventoryCardPaint.mix(color, BODY, 0.35));
            InventoryCardPaint.bitmap(c, x + 3, y + 3, color, switch (status) {
                case READY -> CHECK;
                case BOLTING, LOCKED -> HOURGLASS;
                case EMPTY -> RING;
            });
        });
        return w;
    }

    public static int pillTextX(int x) {
        return x + 10;
    }

    /** Hatched progress gauge with quarter ticks. 带四分刻度的斜纹进度条。 */
    public static void gauge(DrawContext c, int x, int y, float progress) {
        int w = GAUGE_W;
        int lit = Math.round((w - 2) * Math.max(0.0F, Math.min(1.0F, progress)));
        InventoryCardPaint.batch(c, () -> {
            rect(c, x, y + 3, w, 5, LINE_DIM);
            for (int k = 0; k < 5; k++) {
                int tx = x + k * (w - 1) / 4;
                c.fill(tx, y + 1, tx + 1, y + 3, LINE_DIM);
            }
            int dim = InventoryCardPaint.mix(COOL, BODY, 0.6);
            for (int k = 0; k < lit; k++) {
                for (int r = 0; r < 3; r++) {
                    c.fill(x + 1 + k, y + 4 + r, x + 2 + k, y + 5 + r, (k + r) % 3 != 2 ? COOL : dim);
                }
            }
        });
    }

    // ---- tooltip / 提示框 ----

    /**
     * Blueprint tooltip frame around content (x, y, w, h); the rule under the name only with more lines.
     * 包住内容区的蓝图风提示框；有多行时才在名称下画分隔线。
     */
    public static void tooltip(DrawContext c, int x, int y, int w, int h, boolean ruled) {
        InventoryCardPaint.batch(c, () -> {
            c.fill(x - 4, y - 4, x + w + 4, y + h + 4, TIP_BG);
            rect(c, x - 4, y - 4, w + 8, h + 8, CYAN_DK);
            cropMarks(c, x - 4, y - 4, w + 8, h + 8, CYAN);
            if (ruled) {
                c.fill(x, y + 10, x + w, y + 11, LINE_FAINT);
            }
        });
    }

    /**
     * Item lore recoloured into the sheet palette: vanilla gray becomes MUTED, dark gray FAINT; explicit colours (round
     * labels) are kept. 物品说明改用图纸配色：原版灰色改为 MUTED、深灰改为 FAINT；显式颜色（子弹标签）保持不变。
     */
    public static int loreColor(int rgb) {
        return switch (rgb) {
            case 0xAAAAAA -> MUTED & 0xFFFFFF;
            case 0x555555 -> FAINT & 0xFFFFFF;
            default -> rgb;
        };
    }

    // ---- shapes / 图形 ----

    static void rect(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void dottedRect(DrawContext c, int x, int y, int w, int h, int color) {
        for (int k = 0; k < w; k += 2) {
            c.fill(x + k, y, x + k + 1, y + 1, color);
            c.fill(x + k, y + h - 1, x + k + 1, y + h, color);
        }
        for (int k = 0; k < h; k += 2) {
            c.fill(x, y + k, x + 1, y + k + 1, color);
            c.fill(x + w - 1, y + k, x + w, y + k + 1, color);
        }
    }

    private static void dashedH(DrawContext c, int x1, int x2, int y, int color, int on, int off) {
        for (int x = x1; x < x2; x++) {
            if ((x - x1) % (on + off) < on) {
                c.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private static void dashedV(DrawContext c, int x, int y1, int y2, int color, int on, int off) {
        for (int y = y1; y < y2; y++) {
            if ((y - y1) % (on + off) < on) {
                c.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private static void dashedRect(DrawContext c, int x, int y, int w, int h, int color, int on, int off) {
        dashedH(c, x, x + w, y, color, on, off);
        dashedH(c, x, x + w, y + h - 1, color, on, off);
        dashedV(c, x, y, y + h, color, on, off);
        dashedV(c, x + w - 1, y, y + h, color, on, off);
    }

    private static void cropMarks(DrawContext c, int x, int y, int w, int h, int color) {
        int[][] corners = {{x, y, 1, 1}, {x + w - 1, y, -1, 1}, {x, y + h - 1, 1, -1}, {x + w - 1, y + h - 1, -1, -1}};
        for (int[] corner : corners) {
            for (int k = 0; k < 3; k++) {
                c.fill(corner[0] + k * corner[2], corner[1], corner[0] + k * corner[2] + 1, corner[1] + 1, color);
                c.fill(corner[0], corner[1] + k * corner[3], corner[0] + 1, corner[1] + k * corner[3] + 1, color);
            }
        }
    }

    /** 1 px Bresenham line, endpoints inclusive. 1 像素 Bresenham 直线，含端点。 */
    static void line(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            c.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
