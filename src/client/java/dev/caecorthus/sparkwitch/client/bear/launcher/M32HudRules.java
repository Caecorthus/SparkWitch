package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Ballistics;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Cylinder;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the M32A1 HUD row ({@link M32Hud}), from the BEAR art package (README §8): when it shows, the sprite
 * sheet, where each chamber is drawn, the ring and dim rules, and the bottom-right row layout. Texts are fixed ASCII
 * ("4/6", "100 m"), precomputed so a frame allocates nothing. Layout numbers are GUI px.
 * <ul>
 *   <li>Cylinder: chamber {@code i} is drawn in slot {@code (i - next) mod 6} (slot 0 at 12 o'clock under the fixed
 *   pointer, then clockwise), so the round that fires next always sits at the top; the cylinder sprite already shows
 *   every recess empty, so only loaded rounds are drawn over it ({@link #LOADED_DIM} while the launcher's item cooldown
 *   runs). The ring marks slot 0 only while anything is loaded.</li>
 *   <li>Row: {@code "<n>/6"}, 4 px, {@code "<dial> m"}, 5 px, the 25 x 29 icon (pointer row on top); right-aligned at
 *   {@code scaledWidth - 6}, the icon's bottom 7 px above the bottom edge and the texts' bottom 3 px above that. It
 *   moves exactly like the USEC ammo row ({@code UsecAmmoHudRules}): one text row up while the shared corner line is
 *   drawn, and above the hotbar and Wathe's stamina row when it is wide enough to reach the hotbar column. The
 *   cooldown bar copies USEC's, 2 px under the count.</li>
 * </ul>
 * M32A1 HUD 行（{@link M32Hud}）的纯规则，依据 BEAR 美术包（README 第 8 节）：何时显示、精灵图、每个弹膛的绘制位置、加圈与
 * 变暗规则，以及右下角的行布局。文本为固定 ASCII（"4/6"、"100 m"），预先生成，每帧不分配内存。布局数值为 GUI 像素。转轮：第
 * {@code i} 号弹膛画在第 {@code (i - next) mod 6} 个槽位（0 号槽位在固定指针下方的 12 点钟方向，其后顺时针），因此下一发击发
 * 的榴弹总在顶部；转轮精灵本身已画出全部空膛，因此只在其上叠画已装填的榴弹（发射器物品冷却期间用 {@link #LOADED_DIM}）。仅在有
 * 装填时给 0 号槽位加圈。行：{@code "<n>/6"}、4 像素、{@code "<刻度> m"}、5 像素、25 x 29 图标（顶部为指针行）；右对齐于
 * {@code scaledWidth - 6}，图标底边距底部 7 像素，文字底边再高 3 像素。其移动规则与 USEC 弹药行（{@code UsecAmmoHudRules}）
 * 完全相同：共享角落行显示时上移一行文字高度；行宽伸入快捷栏列时抬升到快捷栏与 Wathe 体力行之上。冷却进度条照搬 USEC，
 * 位于弹数下方 2 像素。
 */
public final class M32HudRules {
    /** The one texture id of the sprite sheet (64 x 32). / 精灵图的唯一贴图 id（64 x 32）。 */
    public static final Identifier TEXTURE = SparkWitch.id("textures/gui/bear/m32_chambers.png");
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 32;
    public static final Sprite CYLINDER = new Sprite(0, 0, 25, 25);
    public static final Sprite LOADED = new Sprite(26, 0, 7, 7);
    public static final Sprite EMPTY = new Sprite(34, 0, 7, 7);
    public static final Sprite NEXT_RING = new Sprite(42, 0, 9, 9);
    public static final Sprite POINTER = new Sprite(52, 0, 5, 3);
    public static final Sprite LOADED_DIM = new Sprite(26, 8, 7, 7);

    /** Top-left of each slot's 7 x 7 chamber inside the cylinder, slot 0 at the top, clockwise. / 各槽位左上角。 */
    private static final int[][] SLOTS = {{9, 1}, {16, 5}, {16, 13}, {9, 17}, {2, 13}, {2, 5}};
    public static final int POINTER_X = 10;
    public static final int POINTER_Y = -4;
    public static final int RING_OFFSET = -1;
    /** The icon: the pointer row (4 px) above the 25 x 25 cylinder. / 图标：25 x 25 转轮上方的指针行（4 像素）。 */
    public static final int ICON_CYLINDER_TOP = -POINTER_Y;
    public static final int ICON_WIDTH = 25;
    public static final int ICON_HEIGHT = ICON_CYLINDER_TOP + 25;

    public static final int COUNT_COLOR = 0xFFE0E0E0;
    public static final int EMPTY_COUNT_COLOR = 0xFFAAAAAA;
    /** BEAR olive 0x6E7F3F lightened for a dark HUD. / BEAR 橄榄色提亮。 */
    public static final int DIAL_COLOR = 0xFFA4B86A;
    public static final int BAR_TRACK_COLOR = 0x96000000;
    public static final int BAR_FILL_COLOR = DIAL_COLOR;
    public static final int BAR_FILL_SHADE_COLOR = 0xFF6E7F3F;
    public static final int BAR_HEIGHT = 2;
    public static final int COUNT_GAP = 4;
    public static final int ICON_GAP = 5;
    /** Texts' bottom above the icon's bottom. / 文字底边高于图标底边的像素。 */
    public static final int TEXT_LIFT = 3;

    // Placement, the same numbers as UsecAmmoHudRules (pinned by a test). / 布局数值与 UsecAmmoHudRules 相同（由测试固定）。
    public static final int RIGHT_PADDING = 6;
    public static final int BOTTOM_PADDING = 7;
    public static final int ROW_GAP = 2;
    public static final int HOTBAR_HALF_WIDTH = 91;
    public static final int HOTBAR_GAP = 4;
    public static final int LIFTED_BOTTOM_OFFSET = 41;

    private static final String[] COUNT_TEXTS = new String[M32Cylinder.CHAMBERS + 1];
    private static final String[] DIAL_TEXTS;
    /** The widest dial text, so the lift decision does not flicker as the dial turns. / 最宽的刻度文字。 */
    public static final String WIDEST_DIAL_TEXT;

    static {
        for (int i = 0; i <= M32Cylinder.CHAMBERS; i++) {
            COUNT_TEXTS[i] = i + "/" + M32Cylinder.CHAMBERS;
        }
        int[] settings = M32Ballistics.dialSettings();
        DIAL_TEXTS = new String[settings.length];
        for (int i = 0; i < settings.length; i++) {
            DIAL_TEXTS[i] = settings[i] + " m";
        }
        WIDEST_DIAL_TEXT = DIAL_TEXTS[settings.length - 1];
    }

    private M32HudRules() {
    }

    /**
     * Whether the HUD shows: not under F1, a confirmed SparkWitch server, the launcher in the main hand, and either no
     * Wathe round at all ({@code gameIdle}: INACTIVE, so lobby and off-match holders see it) or a live round (train HUD
     * shown, ACTIVE, the local player playing and alive). STARTING and STOPPING hide it, like the other role HUD lines
     * ({@code HudRenderCallback} draws above Wathe's round-end fade). Shown while aiming too.
     * HUD 是否显示：未按 F1、已确认的 SparkWitch 服务器、主手持发射器，并且要么完全没有 Wathe 对局（{@code gameIdle}：
     * INACTIVE，因此大厅与局外持有者也能看到），要么处于进行中的对局（列车 HUD 显示、ACTIVE、本地玩家在局内存活）。STARTING
     * 与 STOPPING 时隐藏，与其他职业 HUD 行一致（{@code HudRenderCallback} 绘制在 Wathe 回合结束黑幕之上）。瞄准时同样显示。
     */
    public static boolean showsHud(boolean hudHidden, boolean confirmedServer, boolean holdingLauncher,
                                   boolean gameIdle, boolean trainHudActive, boolean gameActive,
                                   boolean playingAndAlive) {
        if (hudHidden || !confirmedServer || !holdingLauncher) {
            return false;
        }
        return gameIdle || (trainHudActive && gameActive && playingAndAlive);
    }

    /** The slot chamber {@code chamber} is drawn in: {@code (chamber - next) mod 6}. / 弹膛所在槽位。 */
    public static int slotOf(int chamber, int next) {
        return Math.floorMod(chamber - next, M32Cylinder.CHAMBERS);
    }

    /** Slot {@code slot}'s chamber top-left x inside the cylinder. / 槽位弹膛左上角 x（转轮内）。 */
    public static int slotX(int slot) {
        return SLOTS[Math.floorMod(slot, M32Cylinder.CHAMBERS)][0];
    }

    /** As {@link #slotX}. / 同 {@link #slotX}。 */
    public static int slotY(int slot) {
        return SLOTS[Math.floorMod(slot, M32Cylinder.CHAMBERS)][1];
    }

    /** The ring over slot 0 shows while anything is loaded. / 有装填时 0 号槽位加圈。 */
    public static boolean showsRing(@Nullable M32Cylinder cylinder) {
        return cylinder != null && !cylinder.isEmpty();
    }

    /**
     * The sprite drawn over chamber {@code chamber}'s recess: a loaded round ({@link #LOADED_DIM} while the launcher
     * cools down) or null for an empty one (the cylinder sprite already shows it).
     * 叠画在弹膛凹槽上的精灵：已装填榴弹（发射器冷却时为 {@link #LOADED_DIM}），空膛为 null（转轮精灵已画出）。
     */
    public static @Nullable Sprite chamberSprite(@Nullable M32Cylinder cylinder, int chamber, boolean coolingDown) {
        if (cylinder == null || !cylinder.isLoaded(chamber)) {
            return null;
        }
        return coolingDown ? LOADED_DIM : LOADED;
    }

    /** "4/6". / "4/6"。 */
    public static String countText(int loaded) {
        return COUNT_TEXTS[Math.max(0, Math.min(M32Cylinder.CHAMBERS, loaded))];
    }

    /** "100 m" for a dial setting (clamped onto the grid). / 刻度读数（钳制到网格）。 */
    public static String dialText(int metres) {
        int clamped = M32Ballistics.clampDial(metres);
        return DIAL_TEXTS[(clamped - M32Ballistics.DIAL_MIN_METRES) / M32Ballistics.DIAL_STEP_METRES];
    }

    public static int countColor(int loaded) {
        return loaded > 0 ? COUNT_COLOR : EMPTY_COUNT_COLOR;
    }

    /** Count, gap, dial, gap, icon. / 弹数、间隙、刻度、间隙、图标。 */
    public static int rowWidth(int countWidth, int dialWidth) {
        return Math.max(0, countWidth) + COUNT_GAP + Math.max(0, dialWidth) + ICON_GAP + ICON_WIDTH;
    }

    /** Left x of the right-aligned row; never negative. / 右对齐行的左侧 x；不为负。 */
    public static int rowX(int scaledWidth, int rowWidth) {
        return Math.max(0, scaledWidth - RIGHT_PADDING - Math.max(0, rowWidth));
    }

    /** Whether a row starting at {@code rowX} reaches the centred hotbar column. / 该行是否伸入快捷栏列。 */
    public static boolean overlapsHotbar(int scaledWidth, int rowX) {
        return rowX < scaledWidth / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
    }

    /**
     * The row's bottom (the icon's bottom edge, exclusive): 7 px above the bottom edge in the corner, one text row
     * higher while the shared corner line is drawn, or lifted above the hotbar when {@code layoutWidth} reaches its
     * column. {@code layoutWidth} should use {@link #WIDEST_DIAL_TEXT}, so the lift never flickers as the dial turns.
     * 该行底边（图标底边，不含）：角落中距底部 7 像素；共享角落行显示时再高一行文字；{@code layoutWidth} 伸入快捷栏列时抬升到
     * 快捷栏之上。{@code layoutWidth} 应按 {@link #WIDEST_DIAL_TEXT} 计算，使转动刻度时抬升不闪烁。
     */
    public static int rowBottom(int scaledWidth, int scaledHeight, int layoutWidth, int fontHeight,
                                boolean sharedLineOccupied) {
        int bottomOffset;
        if (overlapsHotbar(scaledWidth, rowX(scaledWidth, layoutWidth))) {
            bottomOffset = LIFTED_BOTTOM_OFFSET;
        } else {
            bottomOffset = BOTTOM_PADDING + (sharedLineOccupied ? fontHeight + ROW_GAP : 0);
        }
        return scaledHeight - bottomOffset;
    }

    /** Top y of the texts for a row bottom. / 给定行底边时文字的顶部 y。 */
    public static int textY(int rowBottom, int fontHeight) {
        return rowBottom - TEXT_LIFT - fontHeight;
    }

    /** Top y of the icon (its pointer row) for a row bottom. / 给定行底边时图标（指针行）的顶部 y。 */
    public static int iconY(int rowBottom) {
        return rowBottom - ICON_HEIGHT;
    }

    /** The bar shows while the launcher's item cooldown runs. / 发射器物品冷却期间显示进度条。 */
    public static boolean showsBar(boolean coolingDown) {
        return coolingDown;
    }

    /**
     * Filled px of a bar as wide as the count, growing left to right as the cooldown runs out ({@code remainingShare}
     * 1 = just started); at least 1 px while shown. Same rule as USEC's.
     * 与弹数等宽的进度条的填充像素，随冷却结束自左向右增长（{@code remainingShare} 1 = 刚开始）；显示时至少 1 像素。与 USEC 相同。
     */
    public static int barFill(float remainingShare, int width) {
        if (width <= 0) {
            return 0;
        }
        float share = Float.isFinite(remainingShare) ? Math.max(0.0F, Math.min(1.0F, remainingShare)) : 1.0F;
        return Math.max(1, Math.round((1.0F - share) * width));
    }

    /** A sprite rect on the sheet. / 精灵图上的矩形。 */
    public record Sprite(int u, int v, int width, int height) {
    }
}
