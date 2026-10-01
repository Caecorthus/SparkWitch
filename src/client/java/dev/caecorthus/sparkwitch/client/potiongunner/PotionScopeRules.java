package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionBallistics;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Pure client rules for the Potion Gunner scope, range ticks and loaded-shell HUD. Presentation only: the server
 * decides every shot. Role-owned; never part of the witch skill inventory panel.
 * 药炮手瞄准镜、射程刻度与装填 HUD 的纯客户端规则。仅负责展示，每次发射都由服务端决定。职业自有，从不属于魔女技能
 * 背包面板。
 */
public final class PotionScopeRules {
    /** FOV multiplier while scoped (about 4x zoom). / 开镜时的视场倍率（约 4 倍放大）。 */
    public static final float ZOOM_FACTOR = 0.25F;
    /** Mouse look scale while scoped, matching the zoom. / 开镜时的鼠标视角缩放，与放大倍率一致。 */
    public static final double SENSITIVITY_SCALE = 0.25;
    /**
     * Drop ticks in horizontal blocks. Inside {@link PotionGunnerRules#FLAT_RANGE_BLOCKS} the shell flies straight, so
     * the crosshair centre itself is the 0-50 mark ({@link #CENTER_LABEL}); ticks start beyond it.
     * 下坠刻度（水平格数）。{@link PotionGunnerRules#FLAT_RANGE_BLOCKS} 格以内炮弹直线飞行，因此准星中心本身就是
     * 0-50 标记（{@link #CENTER_LABEL}）；刻度从其后开始。
     */
    public static final List<Integer> RANGE_TICKS = List.of(60, 70, 80, 90, 100);
    /** Label of the crosshair centre: the flat-flight range. / 准星中心的标签：平飞距离。 */
    public static final String CENTER_LABEL = "0-" + (int) PotionGunnerRules.FLAT_RANGE_BLOCKS;
    /**
     * WP5 mask, 256x256 and drawn with blur: fully clear inside a radius of about 108 px, then a faint dark ring (alpha
     * up to about 72/255) out to about 119 px, then a soft edge that is opaque from about 122 px.
     * WP5 遮罩，256x256，以模糊方式绘制：半径约 108 像素内完全透明，向外是一圈淡暗环（透明度最高约 72/255）至约
     * 119 像素，随后是柔和边缘，约 122 像素起完全不透明。
     */
    public static final Identifier MASK_TEXTURE = SparkWitch.id("textures/gui/potion_scope_mask.png");
    /**
     * Radius of the mask's soft edge at half opacity (about 120 px) as a fraction of the drawn mask side; the crosshair
     * arms reach it and the range ticks stay inside it.
     * 遮罩柔和边缘半不透明处的半径（约 120 像素）占遮罩边长的比例；十字线延伸到此处，射程刻度保持在其以内。
     */
    public static final double MASK_CLEAR_RADIUS = 120.0 / 256.0;

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
     * Local scoped state: using the launcher, in first person through the player's own eyes, no screen, not a
     * spectator. Every input is local-client state.
     * 本地开镜状态：正在使用炮筒、以玩家自己的眼睛第一人称观看、未打开界面、不是旁观者。所有输入都是本地客户端状态。
     */
    public static boolean isScoped(boolean usingLauncher, boolean firstPerson, boolean cameraIsPlayer,
                                   boolean screenOpen, boolean spectator) {
        return usingLauncher && firstPerson && cameraIsPlayer && !screenOpen && !spectator;
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
     * Pixels below the screen centre at which the {@code distance}-block tick sits for the current {@code pitch} and
     * the effective vertical FOV of this frame; NaN when the shell cannot reach that distance or the tick would leave
     * the circle of radius {@code clearRadius}.
     * 在当前俯仰角与本帧实际竖直视场角下，{@code distance} 格刻度位于屏幕中心下方的像素数；炮弹无法到达该距离或刻度
     * 超出半径为 {@code clearRadius} 的圆时返回 NaN。
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
}
