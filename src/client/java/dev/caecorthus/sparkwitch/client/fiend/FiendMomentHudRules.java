package dev.caecorthus.sparkwitch.client.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendRules;

/**
 * Pure layout and text rules for the Fiend Moment countdown that every player sees: one fixed, horizontally centred
 * line at the top of the screen. The rows above it are Wathe's round timer and NoellesRoles' Jester timer
 * ({@code y = 6}, digit roll to 26), the SparkTraits Depression timer ({@code y = 18}, roll to 38), the Seeker CCTV
 * warning ({@code y = 30 .. 40}) and, on the left, Wathe 1.5.6 {@code MoodRenderer}: four task rows (last at 36 .. 45),
 * the mood bar ({@code 17 + 10 * moodOffset}) and the breakdown warning three pixels below it with up to 3 px of shake,
 * whose English rows reach about {@code x = 212}. The line therefore sits below the mood block's deepest reach, so it
 * never jumps with the task count. On the right, the Tarot table ({@code x >= W - 104}, {@code y <= 78}) stays clear on
 * scaled widths above about 310 px; the bottom HUDs, the action bar and the title band ({@code H / 2 - 40}) are
 * untouched. Re-check these rows when a pinned Wathe, NoellesRoles or SparkTraits HUD moves.
 * 所有玩家可见的魔人时刻倒计时的纯布局与文本规则：屏幕顶部一行固定、水平居中的文本。其上方依次为 Wathe 回合计时与
 * NoellesRoles 小丑计时（{@code y = 6}，数字滚动至 26）、SparkTraits 抑郁计时（{@code y = 18}，滚动至 38）、搜寻者监控警告
 * （{@code y = 30 .. 40}），以及左侧的 Wathe 1.5.6 {@code MoodRenderer}：四行任务（最后一行 36 .. 45）、情绪条
 * （{@code 17 + 10 * moodOffset}）及其下方 3 像素、最多抖动 3 像素的崩溃警告，英文行最远约到 {@code x = 212}。因此本行
 * 位于情绪区块可能到达的最低处之下，不会随任务数量跳动。右侧塔罗表格（{@code x >= W - 104}，{@code y <= 78}）在缩放宽度
 * 约 310 像素以上时不受影响；底部 HUD、动作栏与标题区（{@code H / 2 - 40}）均不触及。锁定版本的 Wathe、NoellesRoles 或
 * SparkTraits HUD 位置变化时需重新核对。
 */
public final class FiendMomentHudRules {
    public static final String KEY = "hud.sparkwitch.fiend.moment";
    /**
     * Opaque lighter tint of {@link FiendRules#COLOR} for readable text; outlines keep the frozen theme color.
     * 为便于阅读而使用的 {@link FiendRules#COLOR} 不透明浅色调；描边仍使用冻结的主题色。
     */
    public static final int TEXT_COLOR = 0xFFE0506E;
    /** Deepest pixel of Wathe's mood block: 4 tasks, breakdown warning and shake. / Wathe 情绪区块最低像素：4 行任务、崩溃警告与抖动。 */
    public static final int MOOD_BLOCK_MAX_BOTTOM = 62;
    /** Top y of the countdown line. / 倒计时行的顶部 y。 */
    public static final int LINE_Y = MOOD_BLOCK_MAX_BOTTOM + 2;

    private FiendMomentHudRules() {
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int remainingTicks) {
        return remainingTicks <= 0 ? 0 : (remainingTicks + 19) / 20;
    }

    /** {@code m:ss} clock text for the {@code %s} argument, e.g. {@code 1:42}. / {@code %s} 参数的 {@code m:ss} 时钟文本。 */
    public static String clock(int remainingTicks) {
        int seconds = seconds(remainingTicks);
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes + ":" + (rest < 10 ? "0" : "") + rest;
    }

    /** Left x of a line centred on the screen. / 屏幕居中文本行的左侧 x。 */
    public static int lineX(int screenWidth, int textWidth) {
        return (screenWidth - textWidth) / 2;
    }
}
