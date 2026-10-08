package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.client.scope.ScopeLensGeometry;
import dev.caecorthus.sparkwitch.client.scope.ScopeRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionBallistics;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Pure client rules for the Potion Gunner scope (true 2.7x zoom and the lit PGO-7 reticle), and the loaded-shell HUD.
 * Presentation only: the server decides every shot. Role-owned; never part of the witch skill inventory panel.
 * <p>
 * Reticle (owner decision 2026-10-08: the RPG-7's PGO-7, illuminated amber; spec {@code A_LIT_SPEC.md} of the
 * launcher-scope design pass). Every mark is placed by angle in mrad through the frame's exact tangent projection,
 * {@code m} mrad sitting {@code ppt * tan(m / 1000)} framebuffer px from the lens centre with
 * {@code ppt = (H / 2) / tan(fov / 2)}, then, while a lens composite distorts the picture, moved inward through the
 * barrel inverse {@link #undistortRadius} so it overlays the same world point. Range rows are labelled in tens of
 * blocks of LINE-OF-SIGHT range and re-solved at the current pitch every frame
 * ({@link PotionBallistics#angleBelowSightAtRange}); the centre row "5" is the 0-50 flat zone. Lead columns are 20
 * mrad apart (1 block/s of crossing speed, since lead = v / 50 rad inside the flat zone). Pixel constants are 1080p
 * framebuffer px, multiplied by {@link #strokeScale} = round(H / 1080) like the USEC reticle.
 * 药炮手瞄准镜（真实 2.7 倍放大与发光的 PGO-7 分划）及装填 HUD 的纯客户端规则。仅负责展示，每次发射都由服务端决定。
 * 职业自有，从不属于魔女技能背包面板。
 * <p>
 * 分划（所有者 2026-10-08 决定：RPG-7 的 PGO-7，琥珀色照明；规格见炮筒瞄准镜设计稿 {@code A_LIT_SPEC.md}）。每个标记都以
 * 密位角经本帧精确的正切投影定位：{@code m} 密位距镜片中心 {@code ppt * tan(m / 1000)} 帧缓冲像素，
 * {@code ppt = (H / 2) / tan(fov / 2)}；当镜片合成着色器使画面畸变时，再经桶形畸变反解 {@link #undistortRadius} 向内移动，
 * 使其覆盖同一个世界点。射程横线以视线距离（十格为单位）标注，并在每帧按当前俯仰角重新求解
 * （{@link PotionBallistics#angleBelowSightAtRange}）；中心横线“5”即 0-50 格平飞区。提前量竖线间隔 20 密位（横向 1 格/秒，
 * 因为平飞区内提前量 = v / 50 弧度）。像素常量为 1080p 帧缓冲像素，与 USEC 分划一样乘以 {@link #strokeScale} = round(H / 1080)。
 */
public final class PotionScopeRules {
    /**
     * True angular magnification of the launcher scope at the centre: the real PGO-7 is 2.7x (owner, 2026-10-08).
     * The single place to retune it.
     * 炮筒瞄准镜中心处的真实角放大倍率：真实的 PGO-7 为 2.7 倍（所有者 2026-10-08）。唯一的调整处。
     */
    public static final double MAGNIFICATION = 2.7;
    /** Vanilla's default FOV option, used for an unusable value. / 原版默认视场角选项，值不可用时使用。 */
    public static final double DEFAULT_OPTIONS_FOV_DEGREES = 70.0;

    // ---- Reticle layout (A_LIT_SPEC.md §2; mrad unless named PX) / 分划布局（密位，名称带 PX 的为像素） ----
    /** One lead division: 1 block/s of crossing speed. / 一格提前量：横向 1 格/秒。 */
    public static final double LEAD_DIVISION_MRAD = 20.0;
    /** Lead columns each side, or {@link #MIN_LEAD_COLUMNS} when five do not fit. / 每侧提前量竖线数。 */
    public static final int LEAD_COLUMNS = 5;
    public static final int MIN_LEAD_COLUMNS = 4;
    /** Walk (3.0 div) and sprint (4.3 div) columns, two strokes wide. / 步行与疾跑竖线，两倍线宽。 */
    public static final List<Integer> HEAVY_LEAD_COLUMNS = List.of(3, 4);
    /** Half gap of the doubled centre line (the aim point is the gap). / 双中心线的半间隙（瞄准点即间隙）。 */
    public static final double CENTRE_HALF_GAP_MRAD = 1.6;
    public static final int CENTRE_HALF_GAP_MIN_PX = 3;
    /**
     * Range rows in line-of-sight blocks. 60 is skipped: at 4.8 mrad it would sit on the centre row, which already
     * covers the 0-{@link PotionGunnerRules#FLAT_RANGE_BLOCKS} flat zone.
     * 射程横线（视线距离，格）。跳过 60：4.8 密位会与中心横线重叠，而中心横线已覆盖 0-50 格平飞区。
     */
    public static final List<Integer> RANGE_ROWS = List.of(70, 80, 90, 100, 110, 120, 130, 140, 150);
    /** The centre row's range, labelled "5". / 中心横线代表的距离，标注为“5”。 */
    public static final int FLAT_ROW_RANGE = (int) PotionGunnerRules.FLAT_RANGE_BLOCKS;
    /** The grid ends at this row when it fits; deeper rows hang short on the cant line. / 网格止于此行。 */
    public static final int GRID_MAX_ROW = 100;
    /** The doubled row (a second stroke {@link #DOUBLE_GAP_PX} above). / 双线横行（上方第二条线）。 */
    public static final int DOUBLED_ROW = 100;
    public static final int DOUBLE_GAP_PX = 4;
    /** Half width of the deep rows below the grid (centre aim only, no lead). / 网格下方短横线的半宽。 */
    public static final double SHORT_ROW_HALF_MRAD = 10.0;
    /** A deep row whose label top is closer than this below the number line is dropped. / 深行标签与数字线的最小间距。 */
    public static final int DEEP_CLEAR_PX = 2;
    /**
     * Rows are aim lines (coordinator decision 2026-10-08): a row is dropped only when its line (the doubled row's
     * upper stroke) comes within this many strokes of the previous kept row's line, i.e. the lines would merge.
     * 横线是瞄准线（协调者 2026-10-08 决定）：只有当某横线（双线行取其上方线条）与上一条保留横线相距不足此线宽数、即两线会
     * 并在一起时，才舍弃该横线。
     */
    public static final int ROW_LINE_CLEAR_PX = 3;
    /**
     * A row keeps its line but loses its label when the label centre is less than the digit height plus this
     * (strokes) below the previous labelled row's, so labels and their key rings never overlap (rows bunch up at
     * steep pitches and wide FOVs). The doubled 100 row's "10" wins: a neighbour it would touch loses its label.
     * 若某横线标签中心与上一条带标签横线的标签中心相距不足“数字高度 + 此值（线宽）”，则该横线保留线条但不画标签，使标签及其
     * 描边从不重叠（俯仰很大或视场角很宽时横线会挤在一起）。双线 100 行的“10”优先：与它相碰的相邻标签被省略。
     */
    public static final int ROW_CLEAR_PX = 2;
    /** The cant line ends this many lens radii below the centre. / 倾斜检查线止于中心下方的镜片半径倍数。 */
    public static final double CANT_END_RHO = 0.88;
    /** The "+" check cross sits this many lens radii above the centre, arms of {@link #CHECK_ARM_PX}. / “+”校验十字。 */
    public static final double CHECK_CROSS_RHO = 0.55;
    public static final int CHECK_ARM_PX = 7;
    /** Fit limit, in lens radii, of every layout decision. / 所有布局判定的适配上限（镜片半径倍数）。 */
    public static final double CULL_RHO = 0.90;
    /** Everything is clipped to pixel centres within the lens radius minus this. / 所有像素中心距中心不超过镜片半径减此值。 */
    public static final double CLIP_INSET_PX = 1.5;
    /** Framebuffer px per font texel at 1080p (the GUI-scale-2 look); digits are 7 texels tall. / 字体像素尺寸。 */
    public static final int TEXT_TEXEL_PX = 2;
    public static final int DIGIT_TEXELS = 7;
    /** Row end to the right edge of its label. / 横线末端到其标签右缘。 */
    public static final int LABEL_GAP_PX = 6;
    /** Bottom grid row to the top of the lead numbers, and to the number line. / 网格底行到提前量数字顶部及数字线。 */
    public static final int NUMBER_TOP_PX = 8;
    public static final int NUMBER_LINE_PX = NUMBER_TOP_PX + DIGIT_TEXELS * TEXT_TEXEL_PX + 5;
    /** Rangefinder (stadia for a standing player) ticks, in line-of-sight blocks. / 测距尺刻度（视线距离，格）。 */
    public static final List<Integer> RANGEFINDER_RANGES = List.of(50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150);
    /** Labelled (long) ticks; 60 stays short and unlabelled ("5" "6" read as "56"). / 带标签的长刻度。 */
    public static final Map<Integer, String> RANGEFINDER_LABELS = Map.of(50, "5", 80, "8", 100, "10", 120, "12",
            150, "15");
    public static final int MIN_RANGEFINDER_TICKS = 5;
    public static final double PLAYER_HEIGHT_BLOCKS = 1.8;
    public static final String RANGEFINDER_LEGEND = "1,8";
    public static final int RANGEFINDER_STEP_PX = 18;
    public static final int RANGEFINDER_TICK_LONG_PX = 6;
    public static final int RANGEFINDER_TICK_SHORT_PX = 3;
    /** Number line to the top of the rangefinder labels. / 数字线到测距尺标签顶部。 */
    public static final int RANGEFINDER_GAP_BELOW_GRID_PX = 12;
    /** Short-row end to the rangefinder's tall end. / 短横线末端到测距尺高端。 */
    public static final int RANGEFINDER_X_GAP_PX = 26;
    /** Long ticks stop this far below the label line. / 长刻度止于标签线下方的距离。 */
    public static final int RANGEFINDER_LABEL_GAP_PX = 3;

    // ---- Lit paint (A_LIT_SPEC.md §5) / 照明配色 ----
    /** PGO-7 bulb amber, the stroke and digit colour. / PGO-7 灯泡琥珀色，线条与数字颜色。 */
    public static final int LIT_COLOR = 0xFFFFC860;
    /** 1-stroke dark key ring around every stroke, at 55 %. / 每条线外一圈深色描边，不透明度 55%。 */
    public static final int LIT_KEY_COLOR = 0x8C221406;
    /**
     * The same key around digits, opaque: the eight outline copies of a label overlap, and the text layer re-sorts its
     * quads, so a translucent outline would build up unevenly.
     * 数字外的同色描边，不透明：标签的八个描边副本相互重叠，且文字渲染层会重新排序其四边形，半透明描边会叠加得不均匀。
     */
    public static final int LIT_LABEL_KEY_COLOR = 0xFF221406;

    // ---- Loaded-shell readout and HUD line / 装填显示与 HUD 行 ----
    /** The readout's right edge and top, in lens radii right of / above the centre. / 装填显示的右缘与顶部。 */
    public static final double SHELL_LINE_RIGHT_RHO = 0.60;
    public static final double SHELL_LINE_TOP_RHO = 0.66;
    /**
     * The loaded-shell line never reaches left of this x (GUI px from the lens centre), clear of the check cross.
     * 装填行左端不越过该 x（距镜片中心的 GUI 像素），避开校验十字。
     */
    public static final double SHELL_LINE_MIN_X = 4.0;
    public static final String HUD_LOADED_KEY = "hud.sparkwitch.potion_gunner.loaded";
    public static final String HUD_EMPTY_KEY = "hud.sparkwitch.potion_gunner.empty";
    public static final int EMPTY_COLOR = 0xFFAAAAAA;
    /** Share of white mixed into a shell colour so dark tints stay readable. / 混入白色的比例，使深色弹种文字可读。 */
    public static final double TEXT_LIGHTEN = 0.35;
    /** Loaded-shell line: gap above Wathe's stamina/armour row (top at height - 39). / 装填行：位于 Wathe 体力/护甲行（顶部为 height - 39）上方。 */
    public static final int HUD_BOTTOM_OFFSET = 50;

    private PotionScopeRules() {
    }

    /**
     * Whether a press edge the latch let through becomes a fire request. A press refused here is still swallowed, so
     * the launcher never attacks, mines or swings. Skipping during the launcher's synced item cooldown only saves a
     * packet; the server re-checks every condition.
     * 闩锁放行的按下沿是否变成发射请求。此处拒绝的按键仍会被吞掉，因此炮筒永远不会攻击、挖掘或挥动。在已同步的
     * 炮筒物品冷却期间跳过只是为了省一个数据包；服务端会复核所有条件。
     */
    public static boolean sendsFire(boolean screenOpen, boolean spectator, boolean cameraIsPlayer,
                                    boolean coolingDown, boolean canSend) {
        return !screenOpen && !spectator && cameraIsPlayer && !coolingDown && canSend;
    }

    /**
     * The loaded-shell line above the hotbar. Like the Time Stealer row it needs an ACTIVE round and a playing, alive
     * local player: {@code HudRenderCallback} draws above Wathe's round-end fade, and STOPPING still counts as playing.
     * 快捷栏上方的装填行。与窃时者的行一样，需要 ACTIVE 对局且本地玩家在局内存活：{@code HudRenderCallback} 绘制在
     * Wathe 回合结束黑幕之上，而 STOPPING 期间仍算作参与中。
     */
    public static boolean showsHudLine(boolean hudHidden, boolean trainHudActive, boolean gameActive,
                                       boolean playingAndAlive, boolean holdingLauncher, boolean scoped) {
        return !hudHidden && trainHudActive && gameActive && playingAndAlive && holdingLauncher && !scoped;
    }

    /** Opaque ARGB text colour for a shell, lightened toward white. / 弹种的不透明 ARGB 文字颜色，向白色提亮。 */
    public static int textColor(PotionShellType type) {
        return lighten(type.color(), TEXT_LIGHTEN);
    }

    static int lighten(int rgb, double share) {
        double t = Math.max(0.0, Math.min(1.0, share));
        int r = (int) Math.round(((rgb >> 16) & 0xFF) + (255 - ((rgb >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) + (255 - ((rgb >> 8) & 0xFF)) * t);
        int b = (int) Math.round((rgb & 0xFF) + (255 - (rgb & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Translation key of a shell's item name, without touching the registry. / 弹种物品名的翻译键，不访问注册表。 */
    public static String shellNameKey(PotionShellType type) {
        return type.itemId().toTranslationKey("item");
    }

    /**
     * Scale of the in-lens loaded-shell line ({@code lineWidth} GUI px wide at scale 1, swatch and shadow included),
     * right-aligned at {@link #SHELL_LINE_RIGHT_RHO} lens radii: 1, or less when the room right of
     * {@link #SHELL_LINE_MIN_X} is narrower; 0 when there is no room at all.
     * 镜内装填行的缩放（缩放为 1 时宽 {@code lineWidth} GUI 像素，含色块与阴影），右对齐于 {@link #SHELL_LINE_RIGHT_RHO} 个
     * 镜片半径处：为 1；若 {@link #SHELL_LINE_MIN_X} 右侧空间更窄则缩小；完全没有空间时为 0。
     */
    public static double shellLineScale(double lensRadius, double lineWidth) {
        double room = SHELL_LINE_RIGHT_RHO * lensRadius - SHELL_LINE_MIN_X;
        if (!(room > 0.0) || !(lineWidth > 0.0)) {
            return 0.0;
        }
        return lineWidth > room ? room / lineWidth : 1.0;
    }

    /**
     * The scope's FOV multiplier ({@code ScopeProfile#fovMultiplier}) for the player's FOV option, so the centre is
     * magnified exactly {@link #MAGNIFICATION} times: {@code tan(fov / 2) / tan(fov * m / 2) = 2.7}, i.e.
     * {@code m = 2 atan(tan(fov / 2) / 2.7) / fov} (0.415 at 70, 0.452 at 90). An unusable FOV counts as
     * {@link #DEFAULT_OPTIONS_FOV_DEGREES}; the result stays within [{@link ScopeRules#MIN_FOV_MULTIPLIER}, 1].
     * 按玩家的视场角选项给出瞄准镜的 FOV 倍率（{@code ScopeProfile#fovMultiplier}），使中心恰好放大 {@link #MAGNIFICATION}
     * 倍：{@code tan(fov / 2) / tan(fov * m / 2) = 2.7}，即 {@code m = 2 atan(tan(fov / 2) / 2.7) / fov}（70 时 0.415，90 时
     * 0.452）。不可用的视场角按 {@link #DEFAULT_OPTIONS_FOV_DEGREES} 处理；结果限制在 [{@link ScopeRules#MIN_FOV_MULTIPLIER}, 1]。
     */
    public static float zoomFactor(double optionsFovDegrees) {
        double fov = optionsFovDegrees > 0.0 && optionsFovDegrees < 180.0
                ? optionsFovDegrees : DEFAULT_OPTIONS_FOV_DEGREES;
        double half = Math.toRadians(fov) / 2.0;
        double factor = 2.0 * Math.atan(Math.tan(half) / MAGNIFICATION) / Math.toRadians(fov);
        return (float) Math.max(ScopeRules.MIN_FOV_MULTIPLIER, Math.min(1.0, factor));
    }

    /**
     * Base mouse-look scale while scoped ({@code ScopeProfile#sensitivityMultiplier}), before the player's Scoped
     * Sensitivity: the zoom's FOV multiplier itself, the convention USEC uses (its look scale is its FOV multiplier).
     * 开镜时的基础鼠标视角缩放（{@code ScopeProfile#sensitivityMultiplier}），再乘玩家的「开镜灵敏度」：即放大的 FOV 倍率
     * 本身，与 USEC 的约定一致（其视角缩放就是其 FOV 倍率）。
     */
    public static float sensitivityScale(double optionsFovDegrees) {
        return zoomFactor(optionsFovDegrees);
    }

    /** Stroke scale: {@code round(H / 1080)}, at least 1 (USEC convention). / 线宽倍数（与 USEC 一致）。 */
    public static int strokeScale(double heightPx) {
        return Double.isFinite(heightPx) ? Math.max(1, (int) Math.round(heightPx / 1080.0)) : 1;
    }

    /**
     * Framebuffer px per unit of tangent for this frame's vertical projection FOV; NaN when unusable.
     * 本帧竖直投影视场角下每单位正切对应的帧缓冲像素；不可用时为 NaN。
     */
    public static double pixelsPerTangent(double projectionFovDegrees, double heightPx) {
        if (!(projectionFovDegrees > 0.0 && projectionFovDegrees < 180.0) || !(heightPx > 0.0)
                || !Double.isFinite(heightPx)) {
            return Double.NaN;
        }
        return heightPx / 2.0 / Math.tan(Math.toRadians(projectionFovDegrees) / 2.0);
    }

    /**
     * Hold-over in mrad below the centre for the row labelled {@code range} line-of-sight blocks at this pitch; NaN
     * when the shell never gets that far (the row is then skipped).
     * 当前俯仰角下标注为视线距离 {@code range} 格的横线位于中心下方的密位；炮弹飞不到该距离时为 NaN（该行不画）。
     */
    public static double rangeRowMrad(double pitchDegrees, double range) {
        return PotionBallistics.angleBelowSightAtRange(pitchDegrees, range) * 1000.0;
    }

    /** Stadia height of a standing player at {@code range}: atan(1.8 / range), mrad. / 站立玩家的测距高度。 */
    public static double rangefinderHeightMrad(double range) {
        return range > 0.0 ? Math.atan(PLAYER_HEIGHT_BLOCKS / range) * 1000.0 : Double.NaN;
    }

    /**
     * The barrel-distortion inverse: the lens radius r (lens radii) whose pixel shows the picture at {@code rho}, from
     * {@code r (1 + k r^4) = rho} with k = {@link ScopeLensGeometry#BARREL_DISTORTION} (Newton, from r = rho).
     * 桶形畸变的反解：显示画面中 {@code rho} 处内容的镜片半径 r（镜片半径倍数），由 {@code r (1 + k r^4) = rho} 求得
     * （k = {@link ScopeLensGeometry#BARREL_DISTORTION}，自 r = rho 起牛顿迭代）。
     */
    public static double undistortRadius(double rho) {
        if (!(rho > 0.0) || !Double.isFinite(rho)) {
            return rho > 0.0 ? rho : 0.0;
        }
        double k = ScopeLensGeometry.BARREL_DISTORTION;
        double r = rho;
        for (int i = 0; i < 8; i++) {
            double r4 = r * r * r * r;
            double step = (r * (1.0 + k * r4) - rho) / (1.0 + 5.0 * k * r4);
            r -= step;
            if (Math.abs(step) < 1.0e-13) {
                break;
            }
        }
        return r;
    }

    /**
     * The part of the pixel rectangle [x0, x1) x [y0, y1) (framebuffer px from the lens centre) whose pixel centres
     * stay within {@code lensRadius - }{@link #CLIP_INSET_PX}, or null. Conservative: a wide rectangle keeps the chord of
     * its row farthest from the centre, a tall one that of its farthest column, so nothing ever leaves the lens.
     * 像素矩形 [x0, x1) x [y0, y1)（相对镜片中心的帧缓冲像素）中像素中心位于 {@code lensRadius - }{@link #CLIP_INSET_PX}
     * 以内的部分，或 null。保守裁剪：宽矩形按离中心最远一行的弦裁剪，高矩形按最远一列，因此任何内容都不会超出镜片。
     */
    public static @Nullable Rect clipToLens(int x0, int y0, int x1, int y1, double lensRadius) {
        double limit = lensRadius - CLIP_INSET_PX;
        if (!(limit > 0.0) || x1 <= x0 || y1 <= y0) {
            return null;
        }
        if (x1 - x0 >= y1 - y0) {
            int[] span = chord(Math.max(Math.abs(y0 + 0.5), Math.abs(y1 - 0.5)), limit);
            int left = Math.max(x0, span[0]);
            int right = Math.min(x1, span[1]);
            return span[1] > span[0] && right > left ? new Rect(left, y0, right, y1) : null;
        }
        int[] span = chord(Math.max(Math.abs(x0 + 0.5), Math.abs(x1 - 0.5)), limit);
        int top = Math.max(y0, span[0]);
        int bottom = Math.min(y1, span[1]);
        return span[1] > span[0] && bottom > top ? new Rect(x0, top, x1, bottom) : null;
    }

    /** Pixels [lo, hi) whose centres lie within the chord at {@code far} from the centre. / 弦内像素范围。 */
    private static int[] chord(double far, double limit) {
        if (far > limit) {
            return new int[]{0, 0};
        }
        double half = Math.sqrt(limit * limit - far * far);
        return new int[]{(int) Math.ceil(-half - 0.5), (int) Math.floor(half - 0.5) + 1};
    }

    /**
     * True when every pixel centre of [x0, x1) x [y0, y1) lies inside the clip circle (labels are kept whole or not
     * at all). / 矩形内所有像素中心都在裁剪圆内时为 true（标签要么完整绘制，要么不绘制）。
     */
    public static boolean insideLens(int x0, int y0, int x1, int y1, double lensRadius) {
        double limit = lensRadius - CLIP_INSET_PX;
        if (!(limit > 0.0) || x1 <= x0 || y1 <= y0) {
            return false;
        }
        double farX = Math.max(Math.abs(x0 + 0.5), Math.abs(x1 - 0.5));
        double farY = Math.max(Math.abs(y0 + 0.5), Math.abs(y1 - 0.5));
        return farX * farX + farY * farY <= limit * limit;
    }

    /**
     * The whole lit PGO-7 reticle for one frame, in framebuffer px relative to the lens centre (+y down), already
     * clipped to the lens. {@code projectionFovDegrees} is the vertical FOV the lens picture was projected with
     * ({@code ScopeFrame#projectionFovDegrees}), {@code heightPx} and {@code lensRadiusPx} are framebuffer px,
     * {@code distorted} says whether a lens composite barrel-distorts this frame ({@code ScopeClient#lensDistorts}),
     * and {@code inkWidthTexels} measures a label's inked width in font texels (the vanilla font's width minus its
     * trailing gap). The rangefinder's first tick is chosen at pitch 0 and kept at every pitch, so it never jumps while
     * the view tilts (it only matters below an options FOV of about 60). An unusable FOV or lens yields nothing.
     * 一帧完整的发光 PGO-7 分划，单位为相对镜片中心的帧缓冲像素（+y 向下），已裁剪到镜片内。{@code projectionFovDegrees}
     * 为镜内画面投影所用的竖直视场角（{@code ScopeFrame#projectionFovDegrees}），{@code heightPx} 与 {@code lensRadiusPx} 为
     * 帧缓冲像素，{@code distorted} 表示本帧镜片合成着色器是否做了桶形畸变（{@code ScopeClient#lensDistorts}），
     * {@code inkWidthTexels} 给出标签实际着墨宽度（字体像素，即原版字体宽度减去末尾间隙）。测距尺的起始刻度在俯仰 0 时选定并
     * 在所有俯仰角下保持，因此视角倾斜时不会跳变（仅在视场角选项约 60 以下才有影响）。视场角或镜片不可用时不画任何东西。
     */
    public static Reticle layout(double pitchDegrees, double projectionFovDegrees, double heightPx,
                                 double lensRadiusPx, boolean distorted, ToIntFunction<String> inkWidthTexels) {
        int s = strokeScale(heightPx);
        double ppt = pixelsPerTangent(projectionFovDegrees, heightPx);
        if (!(ppt > 0.0) || !Double.isFinite(ppt) || !(lensRadiusPx > 8.0) || !Double.isFinite(lensRadiusPx)
                || inkWidthTexels == null) {
            return Reticle.empty(s);
        }
        Lens lens = new Lens(ppt, lensRadiusPx, distorted, s, inkWidthTexels);
        Plan plan = plan(lens, Double.isFinite(pitchDegrees) ? pitchDegrees : 0.0);
        Plan level = pitchDegrees == 0.0 ? plan : plan(lens, 0.0);
        int start = rangefinderStart(lens, level.numberLineY());
        List<Integer> ranges = start < 0 ? List.of()
                : RANGEFINDER_RANGES.subList(start, RANGEFINDER_RANGES.size());
        Builder out = new Builder(lens);
        draw(out, lens, plan);
        double x0 = Double.NaN;
        double base = Double.NaN;
        if (!ranges.isEmpty()) {
            x0 = rangefinderX0(lens);
            base = rangefinderBase(lens, plan.numberLineY(), ranges.getFirst());
            rangefinder(out, lens, x0, base, ranges);
        }
        return new Reticle(s, lens.textScale(), Collections.unmodifiableList(out.strokes),
                Collections.unmodifiableList(out.labels), plan.bottom().range(), plan.columns(), plan.grid(),
                plan.deep(), plan.numberLineY(), ranges, x0, base);
    }

    /**
     * Pure grid decisions for one pitch (A_LIT_SPEC.md §4). Rows the shell cannot reach are skipped, and so is a row
     * whose line would merge with the previous kept row's ({@link #ROW_LINE_CLEAR_PX}); a row whose label would touch
     * the previous label keeps its line without the label ({@link #labelRows}).
     * 某一俯仰角下的网格判定（规格第 4 节）。炮弹到不了的横线不画；线条会与上一条保留横线并在一起的横线也不画
     * （{@link #ROW_LINE_CLEAR_PX}）；标签会碰到上一个标签的横线保留线条但不画标签（{@link #labelRows}）。
     */
    private static Plan plan(Lens lens, double pitch) {
        List<RangeRow> rows = new ArrayList<>();
        double previousLine = lens.pt(0.0, 0.0)[1];
        for (int range : RANGE_ROWS) {
            double mrad = rangeRowMrad(pitch, range);
            if (Double.isNaN(mrad)) {
                continue;
            }
            double line = lens.pt(0.0, mrad)[1];
            double top = line - (range == DOUBLED_ROW ? DOUBLE_GAP_PX * lens.s() : 0.0);
            if (top - previousLine >= ROW_LINE_CLEAR_PX * lens.s()) {
                rows.add(new RangeRow(range, mrad, true));
                previousLine = line;
            }
        }
        RangeRow bottom = null;
        int columns = 0;
        for (int candidate = LEAD_COLUMNS; candidate >= MIN_LEAD_COLUMNS && bottom == null; candidate--) {
            for (int index = rows.size() - 1; index >= 0; index--) {
                RangeRow row = rows.get(index);
                if (row.range() <= GRID_MAX_ROW && gridFits(lens, candidate, row.mrad())) {
                    bottom = row;
                    columns = candidate;
                    break;
                }
            }
        }
        if (bottom == null) {
            // No row fits (a very narrow FOV option): the grid is the centre row with as many columns as fit.
            // 没有横线能放下（极窄的视场角选项）：网格只剩中心横线，并放下尽可能多的提前量竖线。
            bottom = new RangeRow(FLAT_ROW_RANGE, 0.0, true);
            for (int candidate = LEAD_COLUMNS; candidate >= 1 && columns == 0; candidate--) {
                if (gridFits(lens, candidate, 0.0)) {
                    columns = candidate;
                }
            }
        }
        double numberLineY = lens.pt(0.0, bottom.mrad())[1] + NUMBER_LINE_PX * lens.s();
        List<RangeRow> grid = new ArrayList<>();
        List<RangeRow> deep = new ArrayList<>();
        double half = lens.digitHeight() / 2.0;
        for (RangeRow row : rows) {
            if (row.range() <= bottom.range()) {
                grid.add(row);
                continue;
            }
            double[] end = lens.pt(-SHORT_ROW_HALF_MRAD, row.mrad());
            double labelLeft = end[0] - LABEL_GAP_PX * lens.s() - lens.inkWidth(rowLabel(row.range()));
            boolean clear = end[1] - half >= numberLineY + DEEP_CLEAR_PX * lens.s();
            if (clear && lens.rho(labelLeft, end[1] + half) <= CULL_RHO && lens.rho(end[0], end[1]) <= CULL_RHO) {
                deep.add(row);
            }
        }
        boolean centreLabelled = labelRows(lens, grid, deep);
        return new Plan(bottom, columns, Collections.unmodifiableList(grid), Collections.unmodifiableList(deep),
                numberLineY, centreLabelled);
    }

    /**
     * Decides which labels draw, top to bottom from the centre row's "5": a label closer than the digit height +
     * {@link #ROW_CLEAR_PX} strokes to the previous drawn label is left out, except the doubled row's "10", which
     * instead removes the labels it would touch. Rows in {@code grid} and {@code deep} are replaced by their labelled
     * or unlabelled copies; returns whether "5" draws.
     * 自中心横线“5”起自上而下决定哪些标签绘制：与上一个绘制的标签相距不足“数字高度 + {@link #ROW_CLEAR_PX} 线宽”的标签被
     * 省略，但双线行的“10”例外，改为省略它会碰到的标签。{@code grid} 与 {@code deep} 中的横线替换为带或不带标签的副本；
     * 返回“5”是否绘制。
     */
    private static boolean labelRows(Lens lens, List<RangeRow> grid, List<RangeRow> deep) {
        int count = grid.size() + deep.size();
        double[] centres = new double[count + 1];
        boolean[] drawn = new boolean[count + 1];
        centres[0] = labelCentre(lens, FLAT_ROW_RANGE, 0.0);
        drawn[0] = true;
        double minGap = lens.digitHeight() + ROW_CLEAR_PX * lens.s();
        int previous = 0;
        for (int index = 1; index <= count; index++) {
            RangeRow row = index <= grid.size() ? grid.get(index - 1) : deep.get(index - 1 - grid.size());
            centres[index] = labelCentre(lens, row.range(), row.mrad());
            if (row.range() == DOUBLED_ROW) {
                while (previous >= 0 && centres[index] - centres[previous] < minGap) {
                    drawn[previous] = false;
                    do {
                        previous--;
                    } while (previous >= 0 && !drawn[previous]);
                }
                drawn[index] = true;
                previous = index;
            } else if (previous < 0 || centres[index] - centres[previous] >= minGap) {
                drawn[index] = true;
                previous = index;
            }
        }
        for (int index = 1; index <= count; index++) {
            List<RangeRow> list = index <= grid.size() ? grid : deep;
            int at = index <= grid.size() ? index - 1 : index - 1 - grid.size();
            RangeRow row = list.get(at);
            if (row.labelled() != drawn[index]) {
                list.set(at, new RangeRow(row.range(), row.mrad(), drawn[index]));
            }
        }
        return drawn[0];
    }

    /** Vertical centre of a row's label, less the common half-stroke offset. / 横线标签的竖直中心（不含共同的半线宽偏移）。 */
    private static double labelCentre(Lens lens, int range, double mrad) {
        return lens.pt(0.0, mrad)[1] - (range == DOUBLED_ROW ? DOUBLE_GAP_PX * lens.s() / 2.0 : 0.0);
    }

    /** The grid's lower corner and its number line fit inside {@link #CULL_RHO}. / 网格下角与数字线位于适配上限内。 */
    private static boolean gridFits(Lens lens, int columns, double mrad) {
        double[] corner = lens.pt(columns * LEAD_DIVISION_MRAD, mrad);
        return lens.rho(corner[0] + lens.digitHeight() / 2.0, corner[1] + NUMBER_LINE_PX * lens.s()) <= CULL_RHO;
    }

    /** Index of the first rangefinder tick whose scale fits, or -1. / 能放下的测距尺第一个刻度的索引，或 -1。 */
    private static int rangefinderStart(Lens lens, double numberLineY) {
        int s = lens.s();
        double x0 = rangefinderX0(lens);
        for (int start = 0; RANGEFINDER_RANGES.size() - start >= MIN_RANGEFINDER_TICKS; start++) {
            List<Integer> ranges = RANGEFINDER_RANGES.subList(start, RANGEFINDER_RANGES.size());
            double base = rangefinderBase(lens, numberLineY, ranges.getFirst());
            double xEnd = x0 + RANGEFINDER_STEP_PX * s * (ranges.size() - 1);
            double topRight = base - lens.px(rangefinderHeightMrad(ranges.getLast()))
                    - (RANGEFINDER_TICK_LONG_PX + RANGEFINDER_LABEL_GAP_PX) * s - lens.digitHeight();
            if (lens.rho(xEnd + 4 * s, base + s) <= CULL_RHO
                    && lens.rho(x0 - 4 * s, base + 4 * s + lens.digitHeight()) <= CULL_RHO
                    && lens.rho(xEnd + 6 * s, topRight) <= CULL_RHO) {
                return start;
            }
        }
        return -1;
    }

    private static double rangefinderX0(Lens lens) {
        return lens.px(SHORT_ROW_HALF_MRAD) + RANGEFINDER_X_GAP_PX * lens.s();
    }

    private static double rangefinderBase(Lens lens, double numberLineY, int firstRange) {
        return numberLineY + (RANGEFINDER_GAP_BELOW_GRID_PX + RANGEFINDER_LABEL_GAP_PX + RANGEFINDER_TICK_LONG_PX)
                * lens.s() + lens.digitHeight() + lens.px(rangefinderHeightMrad(firstRange));
    }

    /** Range label of a row: tens of blocks. / 横线的距离标签：以十格为单位。 */
    private static String rowLabel(int range) {
        return Integer.toString(range / 10);
    }

    /** Elements 1-9 of A_LIT_SPEC.md §3. / 规格第 3 节的第 1-9 项。 */
    private static void draw(Builder out, Lens lens, Plan plan) {
        int s = lens.s();
        int columns = plan.columns();
        double gx = columns > 0 ? columns * LEAD_DIVISION_MRAD : SHORT_ROW_HALF_MRAD;
        double bottomMrad = plan.bottom().mrad();
        // 1-2. The centre row "5" and the grid rows, the 100 row doubled. / 中心横线与网格横线，100 行为双线。
        out.row(-gx, gx, 0.0, 0.0);
        for (RangeRow row : plan.grid()) {
            out.row(-gx, gx, row.mrad(), 0.0);
            if (row.range() == DOUBLED_ROW) {
                out.row(-gx, gx, row.mrad(), -DOUBLE_GAP_PX * s);
            }
        }
        // 3. Lead columns, from the centre row down to the grid bottom. / 提前量竖线。
        for (int index = 1; index <= columns; index++) {
            int width = HEAVY_LEAD_COLUMNS.contains(index) ? 2 : 1;
            for (int side = -1; side <= 1; side += 2) {
                double ax = side * index * LEAD_DIVISION_MRAD;
                double x = lens.pt(ax, bottomMrad / 2.0)[0];
                out.vertical(x, lens.pt(ax, 0.0)[1], lens.pt(ax, bottomMrad)[1], width * s);
            }
        }
        // 4. The doubled centre line, continuing below the grid as the cant line. / 双中心线，向下延伸为倾斜检查线。
        double halfGap = Math.max(CENTRE_HALF_GAP_MIN_PX * s, lens.px(CENTRE_HALF_GAP_MRAD));
        double top = lens.pt(0.0, 0.0)[1];
        double bottom = Math.max(lens.pt(0.0, bottomMrad)[1], CANT_END_RHO * lens.radius());
        out.vertical(-halfGap, top, bottom, s);
        out.vertical(halfGap, top, bottom, s);
        if (columns > 0) {
            // 5-6. Lead numbers under the grid, then the number line. / 网格下方的提前量数字与数字线。
            for (int index = 1; index <= columns; index++) {
                for (int side = -1; side <= 1; side += 2) {
                    double[] p = lens.pt(side * index * LEAD_DIVISION_MRAD, bottomMrad);
                    out.label(Integer.toString(index), out.strokeCentre(p[0]), p[1] + NUMBER_TOP_PX * s,
                            Align.CENTER, VAlign.TOP);
                }
            }
            out.horizontal(lens.pt(-gx, bottomMrad)[0], lens.pt(gx, bottomMrad)[0], plan.numberLineY());
        }
        // 7. Deep rows: short, on the cant line, centre aim only. / 深行：挂在倾斜检查线上的短横线，只用中心瞄准。
        for (RangeRow row : plan.deep()) {
            out.row(-SHORT_ROW_HALF_MRAD, SHORT_ROW_HALF_MRAD, row.mrad(), 0.0);
            if (row.range() == DOUBLED_ROW) {
                out.row(-SHORT_ROW_HALF_MRAD, SHORT_ROW_HALF_MRAD, row.mrad(), -DOUBLE_GAP_PX * s);
            }
        }
        // 8. Range labels left of their rows, vertically centred. / 横线左侧垂直居中的距离标签。
        if (plan.centreLabelled()) {
            double[] five = lens.pt(-gx, 0.0);
            out.label(rowLabel(FLAT_ROW_RANGE), five[0] - LABEL_GAP_PX * s, five[1] + 0.5 * s, Align.RIGHT,
                    VAlign.MIDDLE);
        }
        for (RangeRow row : plan.grid()) {
            if (row.labelled()) {
                rangeLabel(out, lens, row, -gx);
            }
        }
        for (RangeRow row : plan.deep()) {
            if (row.labelled()) {
                rangeLabel(out, lens, row, -SHORT_ROW_HALF_MRAD);
            }
        }
        // 9. The "+" check cross high above the grid. / 网格上方高处的“+”校验十字。
        int crossY = (int) Math.floor(-CHECK_CROSS_RHO * lens.radius());
        int arm = CHECK_ARM_PX * s;
        out.rect(-arm, crossY, arm + s, crossY + s);
        out.rect(0, crossY - arm, s, crossY + arm + s);
    }

    private static void rangeLabel(Builder out, Lens lens, RangeRow row, double leftMrad) {
        int s = lens.s();
        double[] p = lens.pt(leftMrad, row.mrad());
        double lift = row.range() == DOUBLED_ROW ? DOUBLE_GAP_PX * s / 2.0 : 0.0;
        out.label(rowLabel(row.range()), p[0] - LABEL_GAP_PX * s, p[1] + 0.5 * s - lift, Align.RIGHT, VAlign.MIDDLE);
    }

    /**
     * Element 10: the PGO-7 stadia for a 1.8-block standing player under the grid, right of the cant line. Solid
     * baseline, a dashed curve at the player's head height above it, ticks rising from the curve (labelled ones up to a
     * common label line), and the "1,8" legend. Heads are distortion-exact ({@link #headY}).
     * 第 10 项：网格下方、倾斜检查线右侧的 PGO-7 测距尺，对应 1.8 格高的站立玩家。实线基线、其上方玩家头顶高度处的虚线
     * 曲线、自曲线向上的刻度（带标签的刻度延伸到共同的标签线），以及“1,8”图例。头顶位置按畸变精确计算（{@link #headY}）。
     */
    private static void rangefinder(Builder out, Lens lens, double x0, double base, List<Integer> ranges) {
        int s = lens.s();
        double step = RANGEFINDER_STEP_PX * s;
        double[] heads = new double[ranges.size()];
        double highest = Double.POSITIVE_INFINITY;
        for (int index = 0; index < ranges.size(); index++) {
            heads[index] = headY(lens, x0 + step * index, base, ranges.get(index));
            highest = Math.min(highest, heads[index]);
        }
        double labelBottom = highest - (RANGEFINDER_TICK_LONG_PX + RANGEFINDER_LABEL_GAP_PX) * s;
        double xEnd = x0 + step * (ranges.size() - 1);
        out.horizontal(x0 - 4 * s, xEnd + 4 * s, base);
        for (int index = 0; index < ranges.size(); index++) {
            double x = x0 + step * index;
            String label = RANGEFINDER_LABELS.get(ranges.get(index));
            if (label != null) {
                out.vertical(x, labelBottom + 2 * s, heads[index], s);
                out.label(label, out.strokeCentre(x), labelBottom, Align.CENTER, VAlign.BOTTOM);
            } else {
                out.vertical(x, heads[index] - RANGEFINDER_TICK_SHORT_PX * s, heads[index], s);
            }
            if (index + 1 < ranges.size()) {
                // One dash per interval along the head-height curve, the range linear in x. / 每段间隔一条虚线。
                int from = ranges.get(index);
                int to = ranges.get(index + 1);
                double previousX = Double.NaN;
                double previousY = Double.NaN;
                for (double xx = x + 3 * s; xx <= x + step - 3 * s + 0.01; xx += 1.0) {
                    double yy = headY(lens, xx, base, from + (to - from) * (xx - x) / step);
                    if (!Double.isNaN(previousX)) {
                        out.segment(previousX, previousY, xx, yy);
                    }
                    previousX = xx;
                    previousY = yy;
                }
                out.endPolyline();
            }
        }
        out.label(RANGEFINDER_LEGEND, x0 - 3 * s, base + 4 * s, Align.LEFT, VAlign.TOP);
    }

    /**
     * Screen y of a standing player's head whose feet are on the baseline at screen x: feet to the undistorted picture
     * (the lens shows radius r at r (1 + k r^4)), up by the stadia height, back through the inverse. Without
     * distortion this is just {@code base - px(atan(1.8 / range))}.
     * 脚踩在基线上屏幕 x 处的站立玩家头顶的屏幕 y：先把脚的位置换到未畸变画面（镜片在半径 r 处显示 r (1 + k r^4) 处的内容），
     * 再向上移动测距高度，然后经反解映射回来。无畸变时即 {@code base - px(atan(1.8 / range))}。
     */
    private static double headY(Lens lens, double x, double base, double range) {
        double k = 1.0;
        if (lens.distorted()) {
            double rho = lens.rho(x, base);
            k += ScopeLensGeometry.BARREL_DISTORTION * rho * rho * rho * rho;
        }
        return lens.place(x * k, base * k - lens.px(rangefinderHeightMrad(range)))[1];
    }

    /** Filled pixel rectangle [x0, x1) x [y0, y1), framebuffer px from the lens centre. / 填充像素矩形。 */
    public record Rect(int x0, int y0, int x1, int y1) {
    }

    /**
     * A label in the vanilla font, top-left at ({@code x}, {@code y}) px, {@code width} inked px wide at
     * {@link Reticle#textScale()} px per texel. / 原版字体标签，左上角位于（{@code x}, {@code y}），着墨宽 {@code width} 像素。
     */
    public record Label(String text, int x, int y, int width) {
    }

    /**
     * One frame of the reticle: clipped strokes and labels, plus the layout decisions it was built from (the plan's
     * own lists, no extra per-frame work). {@code gridBottomRange} is {@link #FLAT_ROW_RANGE} when only the centre row
     * fits; without a rangefinder {@code rangefinderRanges} is empty and its tall-end x and baseline y are NaN.
     * 一帧分划：裁剪后的线条与标签，以及构建它所用的布局判定（即规划自身的列表，无额外的逐帧开销）。只放得下中心横线时
     * {@code gridBottomRange} 为 {@link #FLAT_ROW_RANGE}；没有测距尺时 {@code rangefinderRanges} 为空，其高端 x 与基线 y 为 NaN。
     */
    public record Reticle(int strokeScale, int textScale, List<Rect> strokes, List<Label> labels, int gridBottomRange,
                          int leadColumns, List<RangeRow> gridRows, List<RangeRow> deepRows, double numberLineY,
                          List<Integer> rangefinderRanges, double rangefinderX0, double rangefinderBase) {
        static Reticle empty(int strokeScale) {
            return new Reticle(strokeScale, TEXT_TEXEL_PX * strokeScale, List.of(), List.of(), FLAT_ROW_RANGE, 0,
                    List.of(), List.of(), Double.NaN, List.of(), Double.NaN, Double.NaN);
        }
    }

    private enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    private enum VAlign {
        TOP,
        MIDDLE,
        BOTTOM
    }

    /**
     * A drawn range row: line-of-sight blocks, its hold-over in mrad, and whether its label draws.
     * 已绘制的射程横线：视线距离、抬枪密位，以及是否绘制其标签。
     */
    public record RangeRow(int range, double mrad, boolean labelled) {
    }

    private record Plan(RangeRow bottom, int columns, List<RangeRow> grid, List<RangeRow> deep, double numberLineY,
                        boolean centreLabelled) {
    }

    /** The frame's projection, lens and text metrics. / 本帧的投影、镜片与文字尺寸。 */
    private record Lens(double ppt, double radius, boolean distorted, int s, ToIntFunction<String> inkWidthTexels) {
        /** Undistorted offset of an angle, px. / 角度对应的未畸变偏移（像素）。 */
        double px(double mrad) {
            return ppt * Math.tan(mrad / 1000.0);
        }

        /** Where to draw the mark at angles (ax, ay) mrad. / 角度 (ax, ay) 处标记的绘制位置。 */
        double[] pt(double ax, double ay) {
            return place(px(ax), px(ay));
        }

        /** Where to draw a mark whose undistorted offset is (dx, dy). / 未畸变偏移为 (dx, dy) 的标记的绘制位置。 */
        double[] place(double dx, double dy) {
            if (!distorted) {
                return new double[]{dx, dy};
            }
            double rho = Math.hypot(dx, dy) / radius;
            if (rho < 1.0e-9) {
                return new double[]{0.0, 0.0};
            }
            double scale = undistortRadius(rho) / rho;
            return new double[]{dx * scale, dy * scale};
        }

        double rho(double x, double y) {
            return Math.hypot(x, y) / radius;
        }

        int textScale() {
            return TEXT_TEXEL_PX * s;
        }

        int digitHeight() {
            return DIGIT_TEXELS * textScale();
        }

        int inkWidth(String text) {
            return Math.max(0, inkWidthTexels.applyAsInt(text)) * textScale();
        }
    }

    /**
     * Rasterises the spec's 1-px strokes at {@code s} px: pixel = floor(coordinate); a vertical stroke right of the
     * centre (or on it) grows right, one left of it grows left (heavy columns gain their extra width outward, and
     * mirrored strokes stay symmetric); a horizontal stroke grows down, its ends outward. At s = 1 this is the spec's
     * pixel grid exactly.
     * 以 {@code s} 像素光栅化规格中的 1 像素线条：像素 = floor(坐标)；中心右侧（或正中）的竖线向右加宽，左侧的向左加宽
     * （加粗竖线向外加宽，镜像线条保持对称）；横线向下加宽，两端向外延伸。s = 1 时与规格的像素网格完全一致。
     */
    private static final class Builder {
        final Lens lens;
        final int s;
        final List<Rect> strokes = new ArrayList<>();
        final List<Label> labels = new ArrayList<>();
        private boolean inRun;
        private int runX;
        private int runTop;
        private int runBottom;

        Builder(Lens lens) {
            this.lens = lens;
            this.s = lens.s();
        }

        /** A row at angle {@code ay} from {@code ax0} to {@code ax1}, straight through its centre-column y. / 横线。 */
        void row(double ax0, double ax1, double ay, double dyPx) {
            double y = lens.pt(0.0, ay)[1] + dyPx;
            horizontal(lens.pt(ax0, ay)[0], lens.pt(ax1, ay)[0], y);
        }

        void horizontal(double xa, double xb, double y) {
            double low = Math.min(xa, xb);
            double high = Math.max(xa, xb);
            int left = (int) Math.floor(low) - (low < 0.0 ? s - 1 : 0);
            int right = (int) Math.floor(high) + 1 + (high >= 0.0 ? s - 1 : 0);
            int top = (int) Math.floor(y);
            rect(left, top, right, top + s);
        }

        void vertical(double x, double ya, double yb, int thickness) {
            int column = (int) Math.floor(x);
            int left = x < 0.0 ? column + 1 - thickness : column;
            rect(left, (int) Math.floor(Math.min(ya, yb)), left + thickness, (int) Math.floor(Math.max(ya, yb)) + s);
        }

        /**
         * One 1-px Bresenham segment of the current polyline, between floored points. Pixels gather into one run per
         * column across the whole polyline (a shared end pixel is not drawn twice); {@link #endPolyline} draws the
         * last run.
         * 当前折线中的一段 1 像素布雷森汉姆线段（端点取整）。像素在整条折线内按列合并为一段（共享端点不会重复绘制）；
         * {@link #endPolyline} 绘制最后一段。
         */
        void segment(double xa, double ya, double xb, double yb) {
            int x0 = (int) Math.floor(xa);
            int y0 = (int) Math.floor(ya);
            int x1 = (int) Math.floor(xb);
            int y1 = (int) Math.floor(yb);
            int dx = Math.abs(x1 - x0);
            int dy = -Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int error = dx + dy;
            while (true) {
                runPixel(x0, y0);
                if (x0 == x1 && y0 == y1) {
                    return;
                }
                int doubled = 2 * error;
                if (doubled >= dy) {
                    error += dy;
                    x0 += sx;
                }
                if (doubled <= dx) {
                    error += dx;
                    y0 += sy;
                }
            }
        }

        private void runPixel(int x, int y) {
            if (inRun && x == runX) {
                runTop = Math.min(runTop, y);
                runBottom = Math.max(runBottom, y);
                return;
            }
            endPolyline();
            inRun = true;
            runX = x;
            runTop = y;
            runBottom = y;
        }

        /** Draws the pending column run of the current polyline. / 绘制当前折线尚未绘制的列。 */
        void endPolyline() {
            if (inRun) {
                rect(runX, runTop, runX + s, runBottom + s);
                inRun = false;
            }
        }

        void rect(int x0, int y0, int x1, int y1) {
            Rect clipped = clipToLens(x0, y0, x1, y1, lens.radius());
            if (clipped != null) {
                strokes.add(clipped);
            }
        }

        /** The centre x of a vertical stroke drawn at {@code x}. / 在 {@code x} 处绘制的竖线的中心 x。 */
        double strokeCentre(double x) {
            return x + (x < 0.0 ? 1.0 - 0.5 * s : 0.5 * s);
        }

        /** A label anchored at (x, y); kept only when it and its key ring lie inside the lens. / 标签。 */
        void label(String text, double x, double y, Align align, VAlign valign) {
            int width = lens.inkWidth(text);
            int height = lens.digitHeight();
            double left = switch (align) {
                case LEFT -> x;
                case CENTER -> x - width / 2.0;
                case RIGHT -> x - width;
            };
            double top = switch (valign) {
                case TOP -> y;
                case MIDDLE -> y - height / 2.0;
                case BOTTOM -> y - height;
            };
            int lx = (int) Math.round(left);
            int ty = (int) Math.round(top);
            if (insideLens(lx - s, ty - s, lx + width + s, ty + height + s, lens.radius())) {
                labels.add(new Label(text, lx, ty, width));
            }
        }
    }
}
