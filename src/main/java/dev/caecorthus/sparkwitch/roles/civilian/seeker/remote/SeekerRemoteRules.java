package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.payloadpolicy.GeneratedPayloadPolicy;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Pure remote-view rules. {@link #BLOCKED_WHILE_VIEWING} is the Seeker's own deny-list (decoupled from the
 * Control Expert list; only a test couples them): lethal Wathe actions, the shop, UI actions that would leak through the
 * frozen body, and every known role-skill payload. It is a deny-list, so voice handshakes still pass.
 * WP-09 may extend but never shrink the list. The helpers below are the side-neutral area, anchor and timeout rules
 * shared by session open, the per-tick exit checks and the move validator. There is no distance limit between the
 * body and a device (owner decision 2026-10-04): the Wathe play area is the only spatial bound.
 * 纯遥控视角规则。{@link #BLOCKED_WHILE_VIEWING} 是搜寻者自有的拦截名单（与控场专家名单解耦，仅由测试关联）：
 * Wathe 致命行为、商店、会经冻结本体泄漏的界面操作，以及所有已知职业技能包。它是黑名单，语音握手等照常通过。
 * 名单只增不减。下列辅助方法是会话打开、逐刻退出检查与移动校验共用的两端通用区域、锚点与超时规则。
 * 本体与设备之间没有距离限制（所有者决定 2026-10-04）：Wathe 游戏区域是唯一的空间边界。
 */
public final class SeekerRemoteRules {
    // Generated SEEKER column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 SEEKER 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    public static final Set<Identifier> BLOCKED_WHILE_VIEWING = GeneratedPayloadPolicy.SEEKER;

    /** Grace before an unsupported or wet body ends the session (BODY_MOVED). / 本体离地或入水多久后结束会话。 */
    public static final int UNGROUNDED_GRACE_TICKS = 10;

    private SeekerRemoteRules() {
    }

    public static boolean isBlockedWhileViewing(@Nullable Identifier payloadId) {
        return payloadId != null && BLOCKED_WHILE_VIEWING.contains(payloadId);
    }

    /**
     * Inside the Wathe play area: horizontally within the box and not below its floor (the ceiling is not a limit).
     * The only spatial check of open and the per-tick exit (OUT_OF_RANGE); a null area (unknown) never refuses.
     * 位于 Wathe 游戏区域内：水平在盒内且不低于其底面（顶面不作限制）。这是打开与逐刻退出（OUT_OF_RANGE）唯一的
     * 空间检查；区域为 null（未知）时从不拒绝。
     */
    public static boolean insidePlayArea(@Nullable Box playArea, Vec3d position) {
        if (playArea == null) {
            return true;
        }
        return position.x >= playArea.minX && position.x <= playArea.maxX
                && position.z >= playArea.minZ && position.z <= playArea.maxZ
                && !isBelowPlayArea(playArea, position.y);
    }

    /** VOID rule: feet below the play-area floor. / 虚空规则：脚底低于游戏区域底面。 */
    public static boolean isBelowPlayArea(@Nullable Box playArea, double y) {
        return playArea != null && y < playArea.minY;
    }

    /** The body left its anchor by more than sqrt(2) blocks. / 本体偏离锚点超过 √2 格。 */
    public static boolean bodyMoved(Vec3d anchor, Vec3d body) {
        return anchor.squaredDistanceTo(body) > SeekerRules.BODY_MOVE_TOLERANCE_SQUARED;
    }

    /**
     * Open throttle (not a cooldown): at least 10 ticks between opens. A last open "in the future" (a restarted tick
     * counter, e.g. a new integrated-server world in the same JVM) never throttles.
     * 打开节流（非冷却）：两次打开至少间隔 10 刻。记录的上次打开若在“未来”（刻计数器重启，例如同一 JVM 中新开的内置服务端世界），
     * 则不节流。
     */
    public static boolean isOpenThrottled(long lastOpenTick, long now) {
        return lastOpenTick >= 0 && now >= lastOpenTick && now - lastOpenTick < SeekerRules.OPEN_THROTTLE_TICKS;
    }

    /**
     * CAR liveness: the first move (a keep-alive with an unchanged position counts) must arrive by the attach
     * deadline, then moves may never be more than {@link SeekerRules#MOVE_TIMEOUT_TICKS} apart.
     * 小车存活检查：第一次移动（位置不变的保活包也算）须在挂接截止前到达，之后相邻移动间隔不得超过
     * {@link SeekerRules#MOVE_TIMEOUT_TICKS}。
     */
    public static boolean carTimedOut(boolean attached, long attachDeadline, long lastMoveTick, long now) {
        return attached ? now - lastMoveTick > SeekerRules.MOVE_TIMEOUT_TICKS : now > attachDeadline;
    }

    /** Body footing rule of open and tick. / 打开与逐刻共用的本体立足规则。 */
    public static boolean isBodyGrounded(boolean onGround, boolean hasVehicle, boolean touchingWater) {
        return (onGround || hasVehicle) && !touchingWater;
    }

    /**
     * Maps a {@code SeekerTargeting.commonDenyReason} suffix to the exit reason used when the same gate fails
     * mid-session. Unknown suffixes fall back to BLOCKED.
     * 把 {@code SeekerTargeting.commonDenyReason} 的后缀映射为会话中同一门槛失败时的退出原因；未知后缀回退为 BLOCKED。
     */
    public static SeekerExitReason exitReasonForDeny(String denySuffix) {
        return switch (denySuffix) {
            case "stunned" -> SeekerExitReason.STUNNED;
            case "feared" -> SeekerExitReason.FEARED;
            case "swallowed" -> SeekerExitReason.BODY_SWALLOWED;
            default -> SeekerExitReason.BLOCKED;
        };
    }
}
