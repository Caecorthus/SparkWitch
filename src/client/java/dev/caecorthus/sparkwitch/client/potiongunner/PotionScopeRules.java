package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionBallistics;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;

import java.util.List;

/**
 * Pure client rules for the Potion Gunner scope, range ticks and loaded-shell HUD. Presentation only: the server
 * decides every shot. Role-owned; never part of the witch skill inventory panel.
 * 药炮手瞄准镜、射程刻度与装填 HUD 的纯客户端规则。仅负责展示，每次发射都由服务端决定。职业自有，从不属于魔女技能
 * 背包面板。
 */
public final class PotionScopeRules {
    /**
     * The launcher scope's magnification, as {@code client/scope}'s {@code ScopeProfile#fovMultiplier} (0.25 = 4x).
     * The single place to retune it.
     * 炮筒瞄准镜的放大倍率，即 {@code client/scope} 的 {@code ScopeProfile#fovMultiplier}（0.25 = 4 倍）。唯一的调整处。
     */
    public static final float ZOOM_FACTOR = 0.25F;
    /**
     * Base mouse-look scale while scoped, matching the zoom, as {@code ScopeProfile#sensitivityMultiplier}; the player's
     * Scoped Sensitivity percentage multiplies it. The single place to retune it.
     * 开镜时的基础鼠标视角缩放，与放大倍率一致，即 {@code ScopeProfile#sensitivityMultiplier}；再乘以玩家的「开镜灵敏度」
     * 百分比。唯一的调整处。
     */
    public static final float SENSITIVITY_SCALE = 0.25F;
    /**
     * Drop ticks in horizontal blocks. Inside {@link PotionGunnerRules#FLAT_RANGE_BLOCKS} the shell flies straight, so
     * the crosshair centre itself is the 0-50 mark ({@link #CENTER_LABEL}); ticks start beyond it.
     * 下坠刻度（水平格数）。{@link PotionGunnerRules#FLAT_RANGE_BLOCKS} 格以内炮弹直线飞行，因此准星中心本身就是
     * 0-50 标记（{@link #CENTER_LABEL}）；刻度从其后开始。
     */
    public static final List<Integer> RANGE_TICKS = List.of(60, 70, 80, 90, 100);
    /** Label of the crosshair centre: the flat-flight range. / 准星中心的标签：平飞距离。 */
    public static final String CENTER_LABEL = "0-" + (int) PotionGunnerRules.FLAT_RANGE_BLOCKS;
    /** Drop ticks stay a little inside the lens radius. / 下坠刻度略微保持在镜片半径以内。 */
    public static final double TICK_RADIUS_SHARE = 0.92;
    /**
     * Gap (GUI px) between the lens edge and the loaded-shell line's outer corner.
     * 镜片边缘与装填行外角之间的间隙（GUI 像素）。
     */
    public static final double SHELL_LINE_INSET = 6.0;
    /**
     * The loaded-shell line never reaches left of this x (GUI px from the lens centre), clear of the vertical crosshair
     * arm and its one-pixel shadow (x 0..2).
     * 装填行左端不越过该 x（距镜片中心的 GUI 像素），避开竖直十字线及其一像素阴影（x 0..2）。
     */
    public static final double SHELL_LINE_MIN_X = 4.0;

    public static final String HUD_LOADED_KEY = "hud.sparkwitch.potion_gunner.loaded";
    public static final String HUD_EMPTY_KEY = "hud.sparkwitch.potion_gunner.empty";
    public static final int EMPTY_COLOR = 0xFFAAAAAA;
    /** Share of white mixed into a shell colour so dark tints stay readable. / 混入白色的比例，使深色弹种文字可读。 */
    public static final double TEXT_LIGHTEN = 0.35;
    /** Loaded-shell line: gap above Wathe's stamina/armour row (top at height - 39). / 装填行：位于 Wathe 体力/护甲行（顶部为 height - 39）上方。 */
    public static final int HUD_BOTTOM_OFFSET = 50;
    /** Tick labels stay below the horizontal crosshair line (y 0..1) and its shadow. / 刻度标签位于水平十字线（y 0..1）及其阴影之下。 */
    public static final double LABEL_MIN_TOP = 2.5;

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
     * Near ticks sit only a few pixels apart, so labels alternate sides: 60 left, 70 right, 80 left, and so on.
     * 近处刻度间距只有几个像素，因此标签左右交替：60 在左，70 在右，80 在左，依此类推。
     */
    public static boolean labelOnRight(int tickIndex) {
        return tickIndex % 2 == 1;
    }

    /**
     * Top of a tick label centred on the tick, but never above the horizontal crosshair line and its shadow.
     * 刻度标签的顶部：以刻度为中心，但不高于水平十字线及其阴影。
     */
    public static double labelTop(double tickOffset, double labelHeight) {
        return Math.max(LABEL_MIN_TOP, tickOffset - labelHeight / 2.0 + 0.5);
    }

    /**
     * Pixels below the lens centre at which the {@code distance}-block tick sits for the current {@code pitch} and
     * the vertical FOV the world was projected with this frame ({@code ScopeFrame#projectionFovDegrees}, over a
     * projection {@code halfHeight} px tall above the centre); NaN when the shell cannot reach that distance or the
     * tick would leave the circle of radius {@code clearRadius}.
     * 在当前俯仰角与本帧世界投影实际使用的竖直视场角（{@code ScopeFrame#projectionFovDegrees}，中心以上投影高度为
     * {@code halfHeight} 像素）下，{@code distance} 格刻度位于镜片中心下方的像素数；炮弹无法到达该距离或刻度超出半径为
     * {@code clearRadius} 的圆时返回 NaN。
     */
    public static double tickOffset(double pitchDegrees, double distance, double verticalFovDegrees,
                                    double halfHeight, double clearRadius) {
        double offset = PotionBallistics.screenOffset(PotionBallistics.angleBelowSight(pitchDegrees, distance),
                verticalFovDegrees, halfHeight);
        if (Double.isNaN(offset) || Math.abs(offset) > clearRadius) {
            return Double.NaN;
        }
        return offset;
    }

    /**
     * Length of each crosshair arm (GUI px from the lens centre) so the arm, one pixel thick plus its one-pixel shadow,
     * ends just inside the lens edge.
     * 每条十字线臂的长度（距镜片中心的 GUI 像素），使一像素粗的线臂连同一像素阴影正好止于镜片边缘以内。
     */
    public static int crosshairArm(double lensRadius) {
        if (!(lensRadius > 0.0) || !Double.isFinite(lensRadius)) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(lensRadius) - 2);
    }

    /**
     * True when the whole rectangle (GUI px relative to the lens centre) lies inside the lens circle, so the interim
     * reticle never draws outside the lens.
     * 矩形（相对镜片中心的 GUI 像素）完全位于镜片圆内时为 true，使过渡分划从不画到镜片外。
     */
    public static boolean insideLens(double x0, double y0, double x1, double y1, double lensRadius) {
        if (!(lensRadius > 0.0)) {
            return false;
        }
        double farX = Math.max(Math.abs(x0), Math.abs(x1));
        double farY = Math.max(Math.abs(y0), Math.abs(y1));
        return farX * farX + farY * farY <= lensRadius * lensRadius;
    }

    /**
     * The loaded-shell line's top-right corner sits at (+c, -c) from the lens centre: the lens's upper-right 45-degree
     * point, {@link #SHELL_LINE_INSET} inside the edge.
     * 装填行右上角位于镜片中心的 (+c, -c)：镜片右上 45 度点，距边缘 {@link #SHELL_LINE_INSET}。
     */
    public static double shellLineCorner(double lensRadius) {
        return Math.max(0.0, lensRadius - SHELL_LINE_INSET) * Math.sqrt(0.5);
    }

    /**
     * Scale of the loaded-shell line ({@code lineWidth} GUI px wide at scale 1, swatch and shadow included): 1, or less
     * when the upper-right quadrant between {@link #SHELL_LINE_MIN_X} and {@link #shellLineCorner} is narrower, so the
     * right-aligned line stays inside the lens and clear of the vertical arm.
     * 装填行的缩放（缩放为 1 时宽 {@code lineWidth} GUI 像素，含色块与阴影）：为 1；若右上象限中 {@link #SHELL_LINE_MIN_X}
     * 至 {@link #shellLineCorner} 之间更窄则缩小，使右对齐的装填行留在镜片内并避开竖直十字线。
     */
    public static double shellLineScale(double lensRadius, double lineWidth) {
        double room = shellLineCorner(lensRadius) - SHELL_LINE_MIN_X;
        if (!(room > 0.0)) {
            return 0.0;
        }
        return lineWidth > room ? room / lineWidth : 1.0;
    }
}
