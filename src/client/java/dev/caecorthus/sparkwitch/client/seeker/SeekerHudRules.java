package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCooldownReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure presentation rules for the owner-only Seeker status line (plan §3.18): car state, cooldown and its reason,
 * battery, camera and mark countdown, joined by the separator key. It never carries a player name. Colours are
 * opaque ARGB. Visibility follows the Control Expert HUD gates plus "hidden while the CCTV/possession overlay shows".
 * 仅拥有者可见的搜寻者状态行的纯展示规则（计划 §3.18）：小车状态、冷却及其原因、电量、摄像头与标记倒计时，
 * 以分隔符键连接。从不包含任何玩家名字。颜色为不透明 ARGB。可见性沿用控场专家 HUD 的门槛，另加
 * “CCTV/附身叠加层显示时隐藏”。
 */
public final class SeekerHudRules {
    public static final String CAR_READY_KEY = "hud.sparkwitch.seeker.car.ready";
    public static final String CAR_DEPLOYED_KEY = "hud.sparkwitch.seeker.car.deployed";
    public static final String CAR_COOLDOWN_KEY = "hud.sparkwitch.seeker.car.cooldown";
    public static final String CAR_SWALLOWED_KEY = "hud.sparkwitch.seeker.car.swallowed";
    public static final String CAMERA_PLACED_KEY = "hud.sparkwitch.seeker.camera.placed";
    public static final String CAMERA_NONE_KEY = "hud.sparkwitch.seeker.camera.none";
    public static final String MARK_KEY = "hud.sparkwitch.seeker.mark";
    public static final String SEPARATOR_KEY = "hud.sparkwitch.seeker.separator";
    public static final String BATTERY_LOW_KEY = "hud.sparkwitch.seeker.battery.low";
    public static final String BATTERY_CRITICAL_KEY = "hud.sparkwitch.seeker.battery.critical";

    public static final int BASE_COLOR = 0xFF000000 | SeekerRules.COLOR;
    public static final int COOLDOWN_COLOR = 0xFFAAAAAA;
    public static final int SWALLOWED_COLOR = 0xFFB07ACC;
    public static final int WARNING_COLOR = 0xFFFFAA00;
    public static final int CRITICAL_COLOR = 0xFFFF5555;
    public static final int MARK_COLOR = 0xFF000000 | SeekerRules.MARK_COLOR;
    public static final int SEPARATOR_COLOR = 0xFF777777;
    public static final int RIGHT_PADDING = 5;
    public static final int BOTTOM_PADDING = 5;

    private SeekerHudRules() {
    }

    /**
     * Owner-only snapshot of the synced status and the car item cooldown. / 同步状态与小车物品冷却的拥有者快照。
     */
    public record Snapshot(SeekerCarState carState, int cooldownTicks, SeekerCooldownReason cooldownReason,
                           int battery, boolean cameraPlaced, int markTicks) {
    }

    /** A translated argument nested inside a segment (the cooldown reason). / 片段中嵌套的翻译参数（冷却原因）。 */
    public record Translated(String key) {
    }

    /**
     * One coloured part of the line; {@code args} are {@link Integer} or {@link Translated}.
     * 状态行中的一个着色片段；{@code args} 为 {@link Integer} 或 {@link Translated}。
     */
    public record Segment(String key, List<Object> args, int color) {
        public Segment {
            args = List.copyOf(args);
        }
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }

    /**
     * HUD gates: a confirmed SparkWitch server, HUD not hidden (F1), Wathe's own HUD showing, a living Seeker, and no
     * remote view or its overlay on screen.
     * HUD 门槛：已确认的 SparkWitch 服务端、HUD 未隐藏（F1）、Wathe 自身 HUD 正在显示、存活的搜寻者，且没有遥控视角或其叠加层。
     */
    public static boolean visible(boolean confirmedServer, boolean hudHidden, boolean watheHud, boolean seeker,
                                  boolean playingAndAlive, boolean remoteOverlayActive) {
        return confirmedServer && !hudHidden && watheHud && seeker && playingAndAlive && !remoteOverlayActive;
    }

    /** Battery segment colour: critical, warning or normal. / 电量片段颜色：危急、警告或正常。 */
    public static int batteryColor(int battery) {
        int clamped = SeekerRules.clampBattery(battery);
        if (SeekerRules.isBatteryCritical(clamped)) {
            return CRITICAL_COLOR;
        }
        return SeekerRules.isBatteryWarning(clamped) ? WARNING_COLOR : BASE_COLOR;
    }

    /**
     * The line's segments, without separators; empty when there is nothing to report (no car, no camera, no mark).
     * 状态行的片段（不含分隔符）；没有任何可报告内容（无小车、无摄像头、无标记）时为空。
     */
    public static List<Segment> segments(Snapshot snapshot) {
        List<Segment> segments = new ArrayList<>(4);
        switch (snapshot.carState()) {
            case READY -> {
                if (snapshot.cooldownTicks() > 0) {
                    segments.add(new Segment(CAR_COOLDOWN_KEY, List.of(seconds(snapshot.cooldownTicks()),
                            new Translated(snapshot.cooldownReason().translationKey())), COOLDOWN_COLOR));
                } else {
                    segments.add(new Segment(CAR_READY_KEY, List.of(), BASE_COLOR));
                }
            }
            case DEPLOYED -> {
                int battery = SeekerRules.clampBattery(snapshot.battery());
                int color = batteryColor(battery);
                segments.add(new Segment(CAR_DEPLOYED_KEY, List.of(battery), color));
                if (SeekerRules.isBatteryCritical(battery)) {
                    segments.add(new Segment(BATTERY_CRITICAL_KEY, List.of(), color));
                } else if (SeekerRules.isBatteryWarning(battery)) {
                    segments.add(new Segment(BATTERY_LOW_KEY, List.of(), color));
                }
            }
            case SWALLOWED -> segments.add(new Segment(CAR_SWALLOWED_KEY, List.of(), SWALLOWED_COLOR));
            case NONE -> {
            }
        }
        boolean hasMark = snapshot.markTicks() > 0;
        if (segments.isEmpty() && !snapshot.cameraPlaced() && !hasMark) {
            return List.of();
        }
        segments.add(snapshot.cameraPlaced()
                ? new Segment(CAMERA_PLACED_KEY, List.of(), BASE_COLOR)
                : new Segment(CAMERA_NONE_KEY, List.of(), COOLDOWN_COLOR));
        if (hasMark) {
            segments.add(new Segment(MARK_KEY, List.of(seconds(snapshot.markTicks())), MARK_COLOR));
        }
        return List.copyOf(segments);
    }

    /** Left x of a right-aligned line. / 右对齐文本的左侧 x。 */
    public static int lineX(int screenWidth, int textWidth) {
        return screenWidth - RIGHT_PADDING - textWidth;
    }

    /** Top y of the bottom-anchored line. / 底部对齐文本的顶部 y。 */
    public static int lineY(int screenHeight, int fontHeight) {
        return screenHeight - BOTTOM_PADDING - fontHeight;
    }
}
