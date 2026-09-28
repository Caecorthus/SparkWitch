package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure presentation rules for the Seeker's remote CCTV view (overlay and post filter). Every value is derived from the
 * local owner's own synced state; nothing here is authoritative. Battery warnings are visual only: the single warning
 * sound is played by the server.
 * 搜寻者遥控 CCTV 画面（叠加层与后处理滤镜）的纯展示规则。所有数值都来自本地拥有者自己的同步状态，
 * 这里不做任何权威判断。电量警告仅为视觉效果：唯一的警告音由服务端播放。
 */
public final class SeekerCctvRules {
    /** Post-effect chain for the car look (warm/green night vision). / 小车画面（暖绿夜视）的后处理链。 */
    public static final Identifier CAR_POST_EFFECT = SparkWitch.id("shaders/post/seeker_car.json");
    /** Post-effect chain for the camera look (cold grey CCTV). / 摄像头画面（冷灰监控）的后处理链。 */
    public static final Identifier CAMERA_POST_EFFECT = SparkWitch.id("shaders/post/seeker_camera.json");
    /** The shared program under Minecraft's un-namespaced post-program lookup. / 原版后处理程序查找（无命名空间）下的共用程序。 */
    public static final String PROGRAM_NAME = "sparkwitch_seeker_cctv";

    public static final int CAR_FRAME_COLOR = 0xFFB9F5A6;
    public static final int CAMERA_FRAME_COLOR = 0xFFDDE5EE;
    public static final int REC_COLOR = 0xFFFF4040;
    public static final int NORMAL_BAR_COLOR = 0xFF8FE388;
    public static final int WARNING_COLOR = 0xFFFFB02E;
    public static final int CRITICAL_COLOR = 0xFFFF4D4D;
    public static final int SIGNAL_LOST_BACKGROUND = 0xFF0B0D10;
    /** Range fraction from which the feed starts to break up. / 画面开始出现干扰的距离比例。 */
    public static final double RANGE_INTERFERENCE_START = 0.8;
    public static final int BATTERY_SEGMENTS = 10;

    private SeekerCctvRules() {
    }

    /** Post-effect id for a session mode; {@code null} when nothing should be filtered. / 会话模式对应的后处理 id；无需滤镜时为 null。 */
    @Nullable
    public static Identifier postEffect(SeekerSessionMode mode) {
        return switch (mode) {
            case CAR -> CAR_POST_EFFECT;
            case CAMERA -> CAMERA_POST_EFFECT;
            case NONE -> null;
        };
    }

    /**
     * The feed is lost while the body is blind or in darkness, unless NoellesRoles' Gin immunity protects it (Gin cancels
     * the blackout blindness; this also covers blindness that arrives from another source while Gin is active).
     * 本体失明或处于黑暗时画面丢失，除非有 NoellesRoles 金酒免疫（金酒会取消停电失明；金酒生效期间
     * 其他来源的失明同样不遮挡画面）。
     */
    public static boolean signalLost(boolean blindness, boolean darkness, boolean ginImmune) {
        return (blindness || darkness) && !ginImmune;
    }

    /** 0..1 fraction of the effective radius used by the focus; 0 when the radius is unknown. / 焦点已用的有效半径比例。 */
    public static double rangeFraction(double distance, int radius) {
        if (radius <= 0 || !Double.isFinite(distance)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, distance / radius));
    }

    /**
     * Shader interference strength 0..1: rises over the last fifth of the range, and in car mode when the battery is
     * low (warning 0.25, critical 0.6). Visual only.
     * 着色器干扰强度 0..1：在最后五分之一距离内逐渐增强；小车模式下电量低时也会出现（警告 0.25，危急 0.6）。仅视觉。
     */
    public static float interference(SeekerSessionMode mode, int battery, double distance, int radius) {
        double range = (rangeFraction(distance, radius) - RANGE_INTERFERENCE_START) / (1.0 - RANGE_INTERFERENCE_START);
        float rangePart = (float) Math.max(0.0, Math.min(1.0, range)) * 0.8F;
        float batteryPart = 0.0F;
        if (mode == SeekerSessionMode.CAR) {
            if (SeekerRules.isBatteryCritical(battery)) {
                batteryPart = 0.6F;
            } else if (SeekerRules.isBatteryWarning(battery)) {
                batteryPart = 0.25F;
            }
        }
        return Math.max(rangePart, batteryPart);
    }

    public static int rangeBarColor(double fraction) {
        if (fraction >= 0.9) {
            return CRITICAL_COLOR;
        }
        return fraction >= 0.75 ? WARNING_COLOR : NORMAL_BAR_COLOR;
    }

    public static int batteryColor(int battery) {
        if (SeekerRules.isBatteryCritical(battery)) {
            return CRITICAL_COLOR;
        }
        return SeekerRules.isBatteryWarning(battery) ? WARNING_COLOR : NORMAL_BAR_COLOR;
    }

    /**
     * Blink phase for the battery bar: steady above the warning level, slow blink at ≤20%, fast blink at ≤10%.
     * 电量条闪烁：高于警告线时常亮，≤20% 慢闪，≤10% 快闪。
     */
    public static boolean batteryVisible(int battery, long ticks) {
        if (SeekerRules.isBatteryCritical(battery)) {
            return Math.floorMod(ticks, 10L) < 5;
        }
        if (SeekerRules.isBatteryWarning(battery)) {
            return Math.floorMod(ticks, 20L) < 14;
        }
        return true;
    }

    /** Lit segments of the battery bar (rounded up, so 1% still shows one segment). / 电量条点亮的格数（向上取整）。 */
    public static int batterySegments(int battery) {
        int clamped = SeekerRules.clampBattery(battery);
        return (clamped * BATTERY_SEGMENTS + SeekerRules.BATTERY_MAX - 1) / SeekerRules.BATTERY_MAX;
    }

    /** REC dot blink: one second period. / REC 点每秒闪烁一次。 */
    public static boolean recVisible(long ticks) {
        return Math.floorMod(ticks, 20L) < 12;
    }

    /** Warn when the client's render distance shrank the synced radius below the mode's maximum. / 客户端渲染距离使有效半径低于上限时警告。 */
    public static boolean showsLowRenderDistance(SeekerSessionMode mode, int effectiveRadius) {
        int max = SeekerRules.maxRadius(mode);
        return max > 0 && effectiveRadius > 0 && effectiveRadius < max;
    }

    /**
     * Car ↔ camera switching is only offered while the car and at least one camera exist.
     * 只有小车与至少一台摄像头都存在时才提示可切换。
     */
    public static boolean showsSwitchHint(SeekerCarState carState, int cameraCount) {
        return carState == SeekerCarState.DEPLOYED && cameraCount > 0;
    }

    /**
     * Camera cycling is only offered while viewing a camera and another one exists.
     * 仅在观看摄像头且还有其他摄像头时提示可切换摄像头。
     */
    public static boolean showsCameraCycleHint(SeekerSessionMode mode, int cameraCount) {
        return mode == SeekerSessionMode.CAMERA && cameraCount > 1;
    }

    /**
     * Zero-padded camera number ("01"), or "--" for an unknown label; translations only take %s, never %02d.
     * 补零的摄像头编号（“01”），未知编号为“--”；翻译只支持 %s，不支持 %02d。
     */
    public static String cameraNumber(int label) {
        return label > 0 ? String.format(java.util.Locale.ROOT, "%02d", label) : "--";
    }

    public static int frameColor(SeekerSessionMode mode) {
        return mode == SeekerSessionMode.CAR ? CAR_FRAME_COLOR : CAMERA_FRAME_COLOR;
    }

    /** Session clock text "mm:ss". / 会话计时文本 "mm:ss"。 */
    public static String clock(long elapsedTicks) {
        long seconds = Math.max(0L, elapsedTicks) / 20L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", Math.min(99L, seconds / 60L), seconds % 60L);
    }
}
