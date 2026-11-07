package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Ballistics;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Cylinder;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the M32A1 slot badge ({@link M32Hud}), from the BEAR art package (README §8): when it shows, the sprite
 * sheet, where each chamber is drawn, the ring and dim rules, and the badge layout above the launcher's hotbar slot
 * (owner, 2026-11-06: the bottom-right row could overlap the skill lines, so the readout moved above the hotbar like
 * the Ceremonial Sword badge, only while held). Texts are fixed ASCII ("4/6", "100 m"), precomputed so a frame
 * allocates nothing. Layout numbers are GUI px.
 * <ul>
 *   <li>Cylinder: chamber {@code i} is drawn in slot {@code (i - next) mod 6} (slot 0 at 12 o'clock under the fixed
 *   pointer, then clockwise), so the round that fires next always sits at the top; the cylinder sprite already shows
 *   every recess empty, so only loaded rounds are drawn over it ({@link #LOADED_DIM} while the launcher's item cooldown
 *   runs). The ring marks slot 0 only while anything is loaded.</li>
 *   <li>Badge ({@link #badge}): the 25 x 29 icon (pointer row on top), 4 px, then a text column as wide as the
 *   widest of the count and {@link #WIDEST_DIAL_TEXT} (so nothing moves as the dial turns): {@code "<dial> m"} on
 *   top, {@code "<n>/6"} under it and the cooldown bar under the count. Centred on the selected slot's centre (the x
 *   Wathe centres its cooldown number on), clamped inside the screen. Its bottom is the hotbar selection frame's top
 *   row ({@code h - 23}), so the count sits on Wathe's cooldown-number line ({@code h - 35}) and the bar on the
 *   sword's pip row ({@code h - 26}). When Wathe's stamina row is drawn and the badge would reach its columns, the
 *   badge is lifted above it ({@link #LIFTED_BOTTOM_OFFSET}), so the stamina icons are never hidden.</li>
 *   <li>Plate: the texts and the bar sit on one translucent dark rounded plate exactly as tall as the cylinder, so the
 *   light count and the olive dial keep their contrast over bright ground, vanilla or under a shader pack; the texts
 *   keep their vanilla drop shadow on top.</li>
 *   <li>Following badge (owner, 2026-11-06, option B 跟随枪身; the slot badge is now the fallback): for an anchor on
 *   the drawn launcher ({@code M32BadgeAnchor}) the ideal top-left is centred above it with its bottom
 *   {@link #FOLLOW_HIP_GAP} up at the hip and {@link #FOLLOW_AIM_GAP} right of it, vertically centred, at full aim
 *   (lerped by the aim blend); {@link #follow} smooths it over real time with {@link #FOLLOW_TIME_CONSTANT_SECONDS},
 *   snapping on the first frame or a jump over {@link #FOLLOW_SNAP_SHARE} of the width; {@link #followBadge} clamps
 *   it to a {@link #FOLLOW_MARGIN} and above {@code h - }{@link #FOLLOW_HOTBAR_BAND}; {@link #leader} is the 1 px
 *   line from the nearest edge midpoint to a hollow ring on the anchor.</li>
 * </ul>
 * M32A1 物品栏格标记（{@link M32Hud}）的纯规则，依据 BEAR 美术包（README 第 8 节）：何时显示、精灵图、每个弹膛的绘制位置、
 * 加圈与变暗规则，以及发射器所在快捷栏格上方的标记布局（所有者 2026-11-06：右下角的行可能与技能行重合，因此改为像仪礼剑
 * 标记一样在物品栏上方显示，且仅在手持时显示）。文本为固定 ASCII（"4/6"、"100 m"），预先生成，每帧不分配内存。布局数值
 * 为 GUI 像素。转轮：第 {@code i} 号弹膛画在第 {@code (i - next) mod 6} 个槽位（0 号槽位在固定指针下方的 12 点钟方向，其后
 * 顺时针），因此下一发击发的榴弹总在顶部；转轮精灵本身已画出全部空膛，因此只在其上叠画已装填的榴弹（发射器物品冷却期间用
 * {@link #LOADED_DIM}）。仅在有装填时给 0 号槽位加圈。标记（{@link #badge}）：25 x 29 图标（顶部为指针行）、4 像素，再接
 * 一列文字，列宽取弹数与 {@link #WIDEST_DIAL_TEXT} 中较宽者（转动刻度时不移动）：上方 {@code "<刻度> m"}，其下
 * {@code "<n>/6"}，弹数下方为冷却进度条。以选中格中心（即 Wathe 冷却数字的居中位置）为中心，并钳制在屏幕内。底边为快捷栏
 * 选中框的顶行（{@code h - 23}），因此弹数位于 Wathe 冷却数字行（{@code h - 35}），进度条位于仪礼剑点阵行
 * （{@code h - 26}）。Wathe 体力行显示且标记会伸入其所在列时，标记抬升到体力行之上（{@link #LIFTED_BOTTOM_OFFSET}），
 * 体力图标永不被遮挡。底板：文字与进度条共用一块与转轮等高的半透明深色圆角底板，使浅色弹数与橄榄色刻度在明亮地面上
 * （原版或光影包下）仍保持对比度；文字其上仍保留原版投影。
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
    /**
     * The text plate: a 63 % near-black with a faint BEAR olive cast (the Grand Witch sword's pip plate, lighter).
     * Padding is 2 px of air around the glyphs (the right side's last advance column already holds the shadow) and
     * room for the cooldown bar, so the plate never changes size when the bar appears.
     * 文字底板：63% 不透明的近黑色，略带 BEAR 橄榄色调（大魔女仪礼剑点阵底板的浅一档）。字形四周留 2 像素空隙（右侧
     * 最后一个字宽列已含投影），并为冷却进度条预留位置，因此进度条出现时底板尺寸不变。
     */
    public static final int PLATE_COLOR = 0xA00C1008;
    public static final int PLATE_PAD_LEFT = 2;
    public static final int PLATE_PAD_RIGHT = 1;
    public static final int PLATE_PAD_TOP = 2;
    public static final int PLATE_PAD_BOTTOM = 1;

    // Slot badge placement. / 物品栏格标记布局。
    public static final int HOTBAR_SLOTS = 9;
    /** Icon right edge to the texts: 2 px of air, then the plate's 2 px pad. / 图标右缘到文字：2 像素空隙加底板 2 像素内边距。 */
    public static final int TEXT_GAP = 4;
    /** Dial line bottom to count line top. / 刻度行底边到弹数行顶边的间距。 */
    public static final int LINE_GAP = 2;
    /**
     * Badge bottom (exclusive) above the screen bottom: the hotbar selection frame's top row, the same bottom as the
     * sword badge's pip plate.
     * 标记底边（不含）距屏幕底部的距离：快捷栏选中框的顶行，与仪礼剑标记点阵底板的底边相同。
     */
    public static final int BADGE_BOTTOM_OFFSET = 23;
    /**
     * Lifted badge bottom: above Wathe's stamina row (top at h - 39) + 2 px, the lifted offset every corner row uses
     * ({@code UsecAmmoHudRules}, pinned by a test).
     * 抬升后的标记底边：位于 Wathe 体力行（顶端 h - 39）之上再加 2 像素，与各角落行的抬升偏移相同（{@code UsecAmmoHudRules}，
     * 由测试固定）。
     */
    public static final int LIFTED_BOTTOM_OFFSET = 41;
    /**
     * Wathe's {@code StaminaRenderer} columns: ten 9 px icons 8 px apart from {@code w/2 + 10} to {@code w/2 + 91}
     * (exclusive), rows {@code h - 39} to {@code h - 30}.
     * Wathe {@code StaminaRenderer} 所在列：十个 9 像素图标间隔 8 像素，自 {@code w/2 + 10} 至 {@code w/2 + 91}（不含），
     * 行为 {@code h - 39} 至 {@code h - 30}。
     */
    public static final int STAMINA_ROW_LEFT = 10;
    public static final int STAMINA_ROW_RIGHT = 91;

    private static final String[] COUNT_TEXTS = new String[M32Cylinder.CHAMBERS + 1];
    private static final String[] DIAL_TEXTS;
    /**
     * The widest dial text: the text column reserves it, so the badge never moves as the dial turns.
     * 最宽的刻度文字：文字列按其预留宽度，转动刻度时标记不移动。
     */
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

    /**
     * Centre x of hotbar slot {@code slot} (clamped to 0..8): the x Wathe's {@code CooldownRenderer} and the sword
     * badge centre on.
     * 快捷栏第 {@code slot} 格（钳制到 0..8）的中心 x：即 Wathe {@code CooldownRenderer} 与仪礼剑标记的居中位置。
     */
    public static int slotCentreX(int scaledWidth, int slot) {
        int clamped = Math.max(0, Math.min(HOTBAR_SLOTS - 1, slot));
        return scaledWidth / 2 - 80 + clamped * 20;
    }

    /**
     * Text column width: the wider of the count and the widest dial text (pass {@link #WIDEST_DIAL_TEXT}'s width).
     * 文字列宽：弹数与最宽刻度文字中较宽者（传入 {@link #WIDEST_DIAL_TEXT} 的宽度）。
     */
    public static int columnWidth(int countWidth, int widestDialWidth) {
        return Math.max(0, Math.max(countWidth, widestDialWidth));
    }

    /** Icon, gap, text column, the plate's right pad. / 图标、间隙、文字列、底板右侧内边距。 */
    public static int badgeWidth(int columnWidth) {
        return ICON_WIDTH + TEXT_GAP + Math.max(0, columnWidth) + PLATE_PAD_RIGHT;
    }

    /** Left x of a badge centred on the slot, clamped inside the screen. / 以该格为中心、钳制在屏幕内的标记左侧 x。 */
    public static int badgeX(int scaledWidth, int slot, int badgeWidth) {
        int centred = slotCentreX(scaledWidth, slot) - Math.max(0, badgeWidth) / 2;
        return Math.max(0, Math.min(centred, scaledWidth - Math.max(0, badgeWidth)));
    }

    /**
     * Whether Wathe draws its stamina row this frame: the same gate as its {@code StaminaRenderer} (inside the train
     * HUD, the local player playing and alive, stamina not infinite).
     * Wathe 本帧是否绘制体力行：与其 {@code StaminaRenderer} 的条件相同（列车 HUD 内、本地玩家在局内存活、体力非无限）。
     */
    public static boolean staminaRowShown(boolean trainHudActive, boolean playingAndAlive, boolean infiniteStamina) {
        return trainHudActive && playingAndAlive && !infiniteStamina;
    }

    /** Whether a badge spanning {@code [x, x + width)} reaches the stamina row's columns. / 标记是否伸入体力行所在列。 */
    public static boolean reachesStaminaRow(int scaledWidth, int badgeX, int badgeWidth) {
        int centre = scaledWidth / 2;
        return badgeX < centre + STAMINA_ROW_RIGHT && badgeX + badgeWidth > centre + STAMINA_ROW_LEFT;
    }

    /** Badge bottom edge (exclusive). / 标记底边（不含）。 */
    public static int badgeBottom(int scaledHeight, boolean lifted) {
        return scaledHeight - (lifted ? LIFTED_BOTTOM_OFFSET : BADGE_BOTTOM_OFFSET);
    }

    /**
     * The whole badge for the selected slot: centred and clamped ({@link #badgeX}), on the selection frame's top row
     * unless Wathe's stamina row is drawn under its columns ({@link #reachesStaminaRow}), then lifted above it. From the
     * bottom up: the plate's 1 px pad, the 2 px bar, the count line, 2 px, the dial line, the plate's 2 px pad; the icon
     * shares the bottom edge.
     * 选中格对应的整个标记：居中并钳制（{@link #badgeX}），默认位于选中框顶行之上；若 Wathe 体力行绘制在其所在列下方
     * （{@link #reachesStaminaRow}），则抬升到体力行之上。自下而上：底板 1 像素内边距、2 像素进度条、弹数行、2 像素、刻度行、
     * 底板 2 像素内边距；图标与其共用底边。
     */
    public static Badge badge(int scaledWidth, int scaledHeight, int selectedSlot, int columnWidth, int fontHeight,
                              boolean staminaRowShown) {
        int column = Math.max(0, columnWidth);
        int width = badgeWidth(column);
        int x = badgeX(scaledWidth, selectedSlot, width);
        boolean lifted = staminaRowShown && reachesStaminaRow(scaledWidth, x, width);
        return badgeAt(x, badgeBottom(scaledHeight, lifted), column, fontHeight, lifted);
    }

    // ---- Following badge (owner, 2026-11-06, option B 跟随枪身): the badge rides the launcher drawn in first person ----
    // ---- 跟随枪身的标记（所有者 2026-11-06，方案 B）：标记随第一人称绘制的发射器移动 ----
    /** The badge's height: the icon is its tallest part and the plate shares its bottom. / 标记高度：图标最高，底板共用底边。 */
    public static final int BADGE_HEIGHT = ICON_HEIGHT;
    /** Screen margin the following badge keeps. / 跟随标记与屏幕边缘的最小距离。 */
    public static final int FOLLOW_MARGIN = 4;
    /**
     * The following badge's bottom stays above {@code h - 45}: clear of the hotbar, Wathe's cooldown number
     * ({@code h - 35}) and its stamina row ({@code h - 39}) wherever it is, so no column test is needed.
     * 跟随标记的底边保持在 {@code h - 45} 之上：无论在哪一列都避开快捷栏、Wathe 冷却数字（{@code h - 35}）与体力行（{@code h - 39}），
     * 因此无需按列判断。
     */
    public static final int FOLLOW_HOTBAR_BAND = 45;
    /** Hip: the badge bottom this far above the anchor. / 腰射：标记底边位于锚点上方的距离。 */
    public static final int FOLLOW_HIP_GAP = 18;
    /** Aim: the badge left this far right of the anchor. / 瞄准：标记左缘位于锚点右侧的距离。 */
    public static final int FOLLOW_AIM_GAP = 10;
    /** Time constant of the exponential smoothing of the badge position. / 标记位置指数平滑的时间常数。 */
    public static final double FOLLOW_TIME_CONSTANT_SECONDS = 0.1;
    /** A target jump over this share of the screen width snaps instead of gliding. / 目标跳动超过屏幕宽度的这一比例时直接跳转。 */
    public static final float FOLLOW_SNAP_SHARE = 1.0F / 3.0F;
    /** Leader line and ring: {@link #DIAL_COLOR} at 65 %. / 引线与圆环：65% 透明度的 {@link #DIAL_COLOR}。 */
    public static final int LEADER_COLOR = 0xA6A4B86A;
    public static final int LEADER_RING_SIZE = 5;
    /** The line stops this far short of the anchor, on the ring's edge. / 引线在锚点前这么远处停下，即圆环边缘。 */
    public static final int LEADER_RING_CLEARANCE = 3;

    /**
     * Ideal badge left for an anchor, unclamped: centred on it at the hip (blend 0), {@link #FOLLOW_AIM_GAP} right of
     * it at full aim (blend 1), lerped between so the placement moves with the hip-to-aim blend instead of switching.
     * 锚点对应的理想标记左侧（未钳制）：腰射（混合 0）时居中于锚点，完全瞄准（混合 1）时位于锚点右侧 {@link #FOLLOW_AIM_GAP}，
     * 其间线性插值，使布局随腰射到瞄准的过渡移动而不是切换。
     */
    public static float followTargetX(float anchorX, int badgeWidth, float aimBlend) {
        float t = clamp01(aimBlend);
        float hip = anchorX - Math.max(0, badgeWidth) / 2.0F;
        float aim = anchorX + FOLLOW_AIM_GAP;
        return hip + (aim - hip) * t;
    }

    /**
     * Ideal badge top, unclamped: bottom {@link #FOLLOW_HIP_GAP} above the anchor at the hip, vertically centred on
     * it at full aim, lerped between.
     * 理想标记顶边（未钳制）：腰射时底边位于锚点上方 {@link #FOLLOW_HIP_GAP}，完全瞄准时垂直居中于锚点，其间线性插值。
     */
    public static float followTargetY(float anchorY, int badgeHeight, float aimBlend) {
        float t = clamp01(aimBlend);
        int height = Math.max(0, badgeHeight);
        float hip = anchorY - FOLLOW_HIP_GAP - height;
        float aim = anchorY - height / 2.0F;
        return hip + (aim - hip) * t;
    }

    /**
     * Whether the badge snaps to its target this frame: its first appearance, or a target jump over
     * {@link #FOLLOW_SNAP_SHARE} of the screen width (an unusable distance snaps too).
     * 本帧标记是否直接跳到目标：首次出现，或目标跳动超过屏幕宽度的 {@link #FOLLOW_SNAP_SHARE}（距离不可用时同样跳转）。
     */
    public static boolean followSnaps(boolean hadTarget, float previousX, float previousY, float targetX, float targetY,
                                      int scaledWidth) {
        if (!hadTarget) {
            return true;
        }
        float dx = targetX - previousX;
        float dy = targetY - previousY;
        float limit = Math.max(0, scaledWidth) * FOLLOW_SNAP_SHARE;
        return !(dx * dx + dy * dy <= limit * limit);
    }

    /**
     * One axis of the smoothing: {@code target + (previous - target) * exp(-seconds / tau)}. A snap, an unusable
     * previous value or unusable time answers the target; no elapsed time keeps the previous value.
     * 平滑的一个轴：{@code target + (previous - target) * exp(-seconds / tau)}。跳转、先前值不可用或时间不可用时返回目标；
     * 没有经过时间则保持先前值。
     */
    public static float follow(float previous, float target, double seconds, boolean snap) {
        if (snap || !Float.isFinite(previous) || !Double.isFinite(seconds)) {
            return target;
        }
        if (seconds <= 0.0) {
            return previous;
        }
        return (float) (target + (previous - target) * Math.exp(-seconds / FOLLOW_TIME_CONSTANT_SECONDS));
    }

    /** Badge left clamped to the margin on both sides (the left margin wins on a tiny screen). / 左侧 x 两侧钳制到边距。 */
    public static int followX(int scaledWidth, int x, int badgeWidth) {
        return Math.max(FOLLOW_MARGIN, Math.min(x, scaledWidth - FOLLOW_MARGIN - Math.max(0, badgeWidth)));
    }

    /**
     * Badge top clamped to the margin and above the hotbar band ({@link #FOLLOW_HOTBAR_BAND}; the top margin wins).
     * 顶边钳制到边距并位于快捷栏区域（{@link #FOLLOW_HOTBAR_BAND}）之上（顶部边距优先）。
     */
    public static int followTop(int scaledHeight, int top, int badgeHeight) {
        return Math.max(FOLLOW_MARGIN, Math.min(top, scaledHeight - FOLLOW_HOTBAR_BAND - Math.max(0, badgeHeight)));
    }

    /**
     * The following badge from its smoothed ideal top-left: clamped ({@link #followX}, {@link #followTop}), never
     * lifted (the band keeps it clear of the stamina row), laid out by {@link #badgeAt}.
     * 由平滑后的理想左上角得到的跟随标记：钳制（{@link #followX}、{@link #followTop}），从不抬升（该区域已避开体力行），由
     * {@link #badgeAt} 布局。
     */
    public static Badge followBadge(int scaledWidth, int scaledHeight, int targetX, int targetTop, int columnWidth,
                                    int fontHeight) {
        int column = Math.max(0, columnWidth);
        int x = followX(scaledWidth, targetX, badgeWidth(column));
        int top = followTop(scaledHeight, targetTop, BADGE_HEIGHT);
        return badgeAt(x, top + BADGE_HEIGHT, column, fontHeight, false);
    }

    /**
     * The leader from the badge to its anchor: a 1 px line from the midpoint of the badge edge nearest the anchor
     * (bottom, left, right, top; the first wins a tie), stopping {@link #LEADER_RING_CLEARANCE} short of the anchor,
     * and a {@link #LEADER_RING_SIZE} hollow ring centred on the anchor. No line when the anchor is that close.
     * Always drawn: when the badge was clamped away, the line explains the offset.
     * 从标记到锚点的引线：自离锚点最近的标记边（下、左、右、上；相同时取前者）中点出发的 1 像素线，在锚点前
     * {@link #LEADER_RING_CLEARANCE} 处停下，以及以锚点为中心的 {@link #LEADER_RING_SIZE} 空心圆环。锚点足够近时不画线。
     * 总是绘制：标记被钳制移开时，引线说明了偏移。
     */
    public static Leader leader(Badge badge, int badgeWidth, int anchorX, int anchorY) {
        int width = Math.max(0, badgeWidth);
        int left = badge.iconX();
        int top = badge.iconY();
        int right = left + width;
        int bottom = top + BADGE_HEIGHT;
        int centreX = left + width / 2;
        int centreY = top + BADGE_HEIGHT / 2;
        int[][] midpoints = {{centreX, bottom}, {left, centreY}, {right, centreY}, {centreX, top}};
        int startX = centreX;
        int startY = bottom;
        long best = Long.MAX_VALUE;
        for (int[] midpoint : midpoints) {
            long dx = midpoint[0] - anchorX;
            long dy = midpoint[1] - anchorY;
            long distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                startX = midpoint[0];
                startY = midpoint[1];
            }
        }
        double length = Math.sqrt(best);
        boolean line = length > LEADER_RING_CLEARANCE;
        int endX = anchorX;
        int endY = anchorY;
        if (line) {
            endX = (int) Math.round(anchorX - (anchorX - startX) / length * LEADER_RING_CLEARANCE);
            endY = (int) Math.round(anchorY - (anchorY - startY) / length * LEADER_RING_CLEARANCE);
        }
        int half = LEADER_RING_SIZE / 2;
        return new Leader(startX, startY, endX, endY, anchorX - half, anchorY - half, line);
    }

    private static float clamp01(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }

    /**
     * The badge laid out from its left x and bottom edge (exclusive). From the bottom up: the plate's 1 px pad, the 2
     * px bar, the count line, 2 px, the dial line, the plate's 2 px pad; the icon shares the bottom edge.
     * 由左侧 x 与底边（不含）布局的标记。自下而上：底板 1 像素内边距、2 像素进度条、弹数行、2 像素、刻度行、底板 2 像素内边距；
     * 图标与其共用底边。
     */
    public static Badge badgeAt(int x, int bottom, int columnWidth, int fontHeight, boolean lifted) {
        int column = Math.max(0, columnWidth);
        int barY = bottom - PLATE_PAD_BOTTOM - BAR_HEIGHT;
        int countY = barY - fontHeight;
        int dialY = countY - LINE_GAP - fontHeight;
        int textX = x + ICON_WIDTH + TEXT_GAP;
        int plateY = dialY - PLATE_PAD_TOP;
        Plate plate = new Plate(textX - PLATE_PAD_LEFT, plateY, PLATE_PAD_LEFT + column + PLATE_PAD_RIGHT,
                bottom - plateY);
        return new Badge(x, bottom - ICON_HEIGHT, textX, dialY, countY, barY, plate, lifted);
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

    /** A GUI rect (top-left, size). / GUI 矩形（左上角与尺寸）。 */
    public record Plate(int x, int y, int width, int height) {
    }

    /**
     * One frame's badge: icon top-left, the text column's x, the dial, count and bar tops, the plate, and whether it
     * was lifted above Wathe's stamina row.
     * 单帧的标记：图标左上角、文字列 x、刻度/弹数/进度条的顶边、底板，以及是否抬升到 Wathe 体力行之上。
     */
    public record Badge(int iconX, int iconY, int textX, int dialY, int countY, int barY, Plate plate,
                        boolean lifted) {
    }

    /**
     * One frame's leader: the line from the badge edge midpoint {@code (startX, startY)} to {@code (endX, endY)} on
     * the ring's edge (drawn only when {@code line}), and the ring's top-left on the anchor.
     * 单帧引线：从标记边中点 {@code (startX, startY)} 到圆环边缘 {@code (endX, endY)} 的线（仅 {@code line} 为真时绘制），
     * 以及位于锚点上的圆环左上角。
     */
    public record Leader(int startX, int startY, int endX, int endY, int ringX, int ringY, boolean line) {
    }
}
