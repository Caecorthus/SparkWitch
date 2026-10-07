package dev.caecorthus.sparkwitch.client.scope;

/**
 * Pure rules of the scope module (no Minecraft state), unit-tested. Presentation only.
 * 开镜模块的纯规则（不读取 Minecraft 状态），有单元测试。仅用于展示。
 */
public final class ScopeRules {
    /**
     * False until the PiP renderer (WP4b) lands: PICTURE_IN_PICTURE then renders as ZOOM_BLUR.
     * 画中画渲染器（WP4b）完成前为 false：此时画中画按全画面放大渲染。
     */
    public static final boolean PICTURE_IN_PICTURE_AVAILABLE = false;
    /** The lens fades in over this long after scoping in. / 开镜后镜片效果在此时长内淡入。 */
    public static final long LENS_FADE_NANOS = 120_000_000L;
    /**
     * The private post processor is kept this long after scoping out, so a quick re-scope does not rebuild it.
     * 退出开镜后私有后处理器再保留这么久，快速再次开镜时无需重建。
     */
    public static final long RELEASE_AFTER_IDLE_NANOS = 2_000_000_000L;
    public static final int FIRST_RETRY_SECONDS = 2;
    public static final int MAX_RETRY_SECONDS = 30;
    /** GameRenderer clamps the eased FOV multiplier at 0.1; deeper requests gain nothing. / 原版把缓动后的倍率钳制在 0.1。 */
    public static final float MIN_FOV_MULTIPLIER = 0.1F;
    public static final float MAX_SENSITIVITY_MULTIPLIER = 4.0F;

    private ScopeRules() {
    }

    /**
     * The generic view gate checked before any provider is asked: a local player, first person, the camera is that
     * player, no screen is open, not a spectator.
     * 在询问任何提供者之前检查的通用视角条件：存在本地玩家、第一人称、相机即该玩家、未打开界面、不是旁观者。
     */
    public static boolean viewAllowsScope(boolean hasPlayer, boolean firstPerson, boolean cameraIsPlayer,
                                          boolean screenOpen, boolean spectator) {
        return hasPlayer && firstPerson && cameraIsPlayer && !screenOpen && !spectator;
    }

    /**
     * The mode that actually renders: a shader pack forces ZOOM_BLUR (its HUD fallback), and so does PiP before it is
     * available.
     * 实际渲染的模式：开光影包时强制为全画面放大（使用 HUD 回退），画中画尚不可用时同样如此。
     */
    public static ScopeMode effectiveMode(ScopeMode selected, boolean shaderPackInUse, boolean pictureInPictureAvailable) {
        if (selected == ScopeMode.PICTURE_IN_PICTURE && !shaderPackInUse && pictureInPictureAvailable) {
            return ScopeMode.PICTURE_IN_PICTURE;
        }
        return ScopeMode.ZOOM_BLUR;
    }

    /** A profile's FOV multiplier made safe: (0.1..1], anything unusable means no zoom. / 安全化的 FOV 乘数。 */
    public static float zoomFactor(float profileMultiplier) {
        if (!Float.isFinite(profileMultiplier) || profileMultiplier <= 0.0F) {
            return 1.0F;
        }
        return Math.max(MIN_FOV_MULTIPLIER, Math.min(1.0F, profileMultiplier));
    }

    /**
     * Mouse-look scale while scoped: the profile's base multiplier times the player's percentage.
     * 开镜时的鼠标视角缩放：配置的基础倍率乘以玩家设置的百分比。
     */
    public static double lookScale(float profileMultiplier, int sensitivityPercent) {
        double base = Float.isFinite(profileMultiplier)
                ? Math.max(0.0, Math.min(MAX_SENSITIVITY_MULTIPLIER, profileMultiplier))
                : 1.0;
        return base * ScopeSettings.clampPercent(sensitivityPercent) / 100.0;
    }

    /** Lens fade-in (0..1) after scoping in. / 开镜后的镜片淡入系数（0..1）。 */
    public static float lensFade(long scopedNanos) {
        if (scopedNanos <= 0L) {
            return 0.0F;
        }
        return (float) Math.min(1.0, (double) scopedNanos / LENS_FADE_NANOS);
    }

    /** Whether an idle processor should be closed. / 空闲的处理器是否应关闭。 */
    public static boolean releasesProcessor(long idleNanos) {
        return idleNanos >= RELEASE_AFTER_IDLE_NANOS;
    }

    /**
     * Seconds to wait before rebuilding a failed lens pipeline: 2, 4, 8, 16, then 30 (the Blind echo-view schedule). A
     * one-off error recovers quickly; a persistent one retries rarely, and the HUD fallback ring shows meanwhile.
     * 重建失败的镜片管线前等待的秒数：2、4、8、16，之后为 30（与盲人回声画面相同）。偶发错误很快恢复；持续错误
     * 很少重试，期间显示 HUD 回退环。
     */
    public static int retryDelaySeconds(int failures) {
        int doublings = Math.min(Math.max(failures, 1) - 1, 5);
        return Math.min(FIRST_RETRY_SECONDS << doublings, MAX_RETRY_SECONDS);
    }
}
