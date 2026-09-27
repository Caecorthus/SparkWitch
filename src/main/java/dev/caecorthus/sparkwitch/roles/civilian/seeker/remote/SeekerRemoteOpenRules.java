package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import org.jetbrains.annotations.Nullable;

/**
 * Pure, ordered {@code seeker_remote_open} checklist (plan §3.6). The service gathers the facts on the server thread;
 * this class only decides, so the order is testable without a world. The first failing check wins and returns the
 * suffix of {@code message.sparkwitch.seeker.remote.denied.<suffix>}; null means the open is allowed.
 * 纯粹、有序的 {@code seeker_remote_open} 检查清单（计划 §3.6）。服务在服务端线程上收集事实，本类只负责判定，
 * 因此无需世界即可测试顺序。第一个失败的检查生效，返回 {@code message.sparkwitch.seeker.remote.denied.<suffix>} 的后缀；
 * 返回 null 表示允许打开。
 */
public final class SeekerRemoteOpenRules {
    public static final String DENY_BLOCKED = "blocked";
    public static final String DENY_SWALLOWED = "swallowed";
    public static final String DENY_NOT_GROUNDED = "not_grounded";
    public static final String DENY_BUSY = "busy";
    public static final String DENY_TOO_FAST = "too_fast";
    public static final String DENY_NO_TABLET = "no_tablet";
    public static final String DENY_NO_DEVICE = "no_device";
    public static final String DENY_OUT_OF_RANGE = "out_of_range";

    private SeekerRemoteOpenRules() {
    }

    /**
     * Facts about one open request, all read on the server.
     * 一次打开请求的全部事实，均在服务端读取。
     *
     * @param commonDeny     {@code SeekerTargeting.commonDenyReason} (null = passed) / 公共门槛结果
     * @param requested      decoded mode; NONE for an unknown byte / 解码后的模式，未知字节为 NONE
     * @param current        current session mode / 当前会话模式
     * @param swallowed      body swallowed by a Taotie / 本体被饕餮吞噬
     * @param lastStand      SparkTraits Last Stand pending / 最后一搏待定
     * @param grounded       {@link SeekerRemoteRules#isBodyGrounded} / 本体立足
     * @param throttled      {@link SeekerRemoteRules#isOpenThrottled} / 打开节流中
     * @param consoleDevice  a console device anywhere in the inventory / 背包任意位置持有控制台设备
     * @param deviceUsable   target device exists, is owned and alive / 目标设备存在、归属本人且存活
     * @param inRange        within the effective radius of the body / 在本体有效半径内
     * @param inPlayArea     inside the Wathe play area / 在 Wathe 游戏区域内
     */
    public record Facts(@Nullable String commonDeny, SeekerSessionMode requested, SeekerSessionMode current,
                        boolean swallowed, boolean lastStand, boolean grounded, boolean throttled,
                        boolean consoleDevice, boolean deviceUsable, boolean inRange, boolean inPlayArea) {
    }

    @Nullable
    public static String denyReason(Facts facts) {
        if (facts.commonDeny() != null) {
            return facts.commonDeny();
        }
        if (facts.requested() == SeekerSessionMode.NONE) {
            return DENY_BLOCKED;
        }
        if (facts.swallowed()) {
            return DENY_SWALLOWED;
        }
        if (facts.lastStand()) {
            return DENY_BLOCKED;
        }
        if (!facts.grounded()) {
            return DENY_NOT_GROUNDED;
        }
        if (facts.current() == facts.requested()) {
            return DENY_BUSY;
        }
        if (facts.throttled()) {
            return DENY_TOO_FAST;
        }
        if (!facts.consoleDevice()) {
            return DENY_NO_TABLET;
        }
        if (!facts.deviceUsable()) {
            return DENY_NO_DEVICE;
        }
        if (!facts.inRange() || !facts.inPlayArea()) {
            return DENY_OUT_OF_RANGE;
        }
        return null;
    }

    /** A different mode while a session is open: an atomic switch. / 会话中请求另一模式：原子切换。 */
    public static boolean isSwitch(SeekerSessionMode current, SeekerSessionMode requested) {
        return current != SeekerSessionMode.NONE && requested != SeekerSessionMode.NONE && current != requested;
    }
}
