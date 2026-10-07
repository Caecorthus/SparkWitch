package dev.caecorthus.sparkwitch.client.bellringer;

/**
 * Pure presentation rules for the Bell Ringer's Echo HUD. The layout mirrors Wathe 1.5.6
 * {@code MoodRenderer.renderHud}: task rows at {@code y = 6 + 10 * offset}, the mood icon at
 * {@code y = 6 .. 23} shifted by {@code 3 * moodOffset}, the mood bar at {@code 8 + fontHeight} and the
 * civilian breakdown warning three pixels below it (both shifted by {@code 10 * moodOffset}); the psycho
 * banner occupies {@code y = 6 .. 23}. Re-check these numbers when the pinned Wathe jar changes.
 * 敲钟人回响 HUD 的纯展示规则。布局对应 Wathe 1.5.6 {@code MoodRenderer.renderHud}：任务行位于
 * {@code y = 6 + 10 * offset}，情绪图标位于 {@code y = 6 .. 23} 并随 {@code 3 * moodOffset} 下移，
 * 情绪条位于 {@code 8 + fontHeight}，平民崩溃警告在其下方 3 像素（两者随 {@code 10 * moodOffset} 下移）；
 * 狂暴横幅占用 {@code y = 6 .. 23}。更换 Wathe 固定版本时需重新核对这些数值。
 */
public final class BellEchoHudRules {
    /** Wathe's task/warning text column. / Wathe 任务与警告文本所在列。 */
    public static final int TEXT_X = 22;
    /** Top row used when Wathe draws no mood block. / Wathe 未绘制情绪区块时使用的顶行。 */
    public static final int TOP_Y = 6;
    /** Vertical gap below Wathe's block. / 与 Wathe 区块之间的垂直间距。 */
    public static final int GAP = 3;
    public static final String TASK_KEY_PREFIX = "task.sparkwitch.bell_echo.";

    private static final int ICON_TOP = 6;
    private static final int ICON_HEIGHT = 17;
    private static final int ICON_BOTTOM = ICON_TOP + ICON_HEIGHT;
    private static final int ICON_OFFSET_SCALE = 3;
    private static final int ROW_OFFSET_SCALE = 10;
    private static final int BAR_TOP_PADDING = 8;
    private static final int BAR_HEIGHT = 1;
    private static final int WARNING_GAP = 3;
    private static final float SHAKE_SCALE = 3.0F;

    private BellEchoHudRules() {
    }

    /**
     * Top y of the heard hint so it sits below Wathe's mood/task block or the psycho banner.
     * {@code moodBlockVisible} is false when Wathe skips its block (non-murder mode), which leaves the
     * statics stale. The civilian breakdown warning is reserved whenever {@code moodRender < 0}, plus its
     * shake amplitude, so the hint never overlaps it.
     * “听到钟声”提示的顶部 y，使其位于 Wathe 情绪/任务区块或狂暴横幅下方。Wathe 跳过区块（非谋杀模式）时
     * {@code moodBlockVisible} 为 false，此时静态值已过期。只要 {@code moodRender < 0} 就为平民崩溃警告及其
     * 抖动幅度预留空间，保证提示不与其重叠。
     */
    public static int hintY(
            boolean moodBlockVisible,
            boolean psychoActive,
            float moodOffset,
            float moodRender,
            int fontHeight
    ) {
        if (!moodBlockVisible) {
            return TOP_Y;
        }
        if (psychoActive) {
            return ICON_BOTTOM + GAP;
        }
        float offset = Float.isFinite(moodOffset) ? Math.max(0.0F, moodOffset) : 0.0F;
        float iconBottom = ICON_BOTTOM + ICON_OFFSET_SCALE * offset;
        float barBottom = ROW_OFFSET_SCALE * offset + BAR_TOP_PADDING + fontHeight + BAR_HEIGHT;
        float bottom = Math.max(iconBottom, barBottom);
        if (Float.isFinite(moodRender) && moodRender < 0.0F) {
            float warningBottom = ROW_OFFSET_SCALE * offset + BAR_TOP_PADDING + fontHeight + WARNING_GAP + fontHeight;
            float shake = Math.min(1.0F, -moodRender) * SHAKE_SCALE;
            bottom = Math.max(bottom, warningBottom + shake);
        }
        return (int) Math.ceil(bottom) + GAP;
    }

    /** Whole seconds shown on the Echo task line, rounded up, never negative. / 回响任务行显示的整秒数，向上取整且不为负。 */
    public static int taskSeconds(int remainingTicks) {
        if (remainingTicks <= 0) {
            return 0;
        }
        return (remainingTicks + 19) / 20;
    }

    /** Lang key for a Wathe task name ({@code sleep/outside/eat/drink}). / Wathe 任务名对应的语言键。 */
    public static String taskKey(String watheTaskName) {
        return TASK_KEY_PREFIX + watheTaskName;
    }
}
