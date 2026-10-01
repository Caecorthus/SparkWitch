package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Pure remote-view rules. {@link #BLOCKED_WHILE_VIEWING} is the Seeker's own literal deny-list (decoupled from the
 * Control Expert list; only a test couples them): lethal Wathe actions, the shop, UI actions that would leak through the
 * frozen body, and every known role-skill payload. It is a deny-list, so voice handshakes still pass.
 * WP-09 may extend but never shrink the list. The helpers below are the side-neutral distance, area, anchor and
 * timeout rules shared by session open, the per-tick exit checks and the move validator. Distances are horizontal
 * because vanilla entity tracking (the engine limit behind the effective radius) measures only x/z from the body.
 * 纯遥控视角规则。{@link #BLOCKED_WHILE_VIEWING} 是搜寻者自有的字面量拦截名单（与控场专家名单解耦，仅由测试关联）：
 * Wathe 致命行为、商店、会经冻结本体泄漏的界面操作，以及所有已知职业技能包。它是黑名单，语音握手等照常通过。
 * 名单只增不减。下列辅助方法是会话打开、逐刻退出检查与移动校验共用的两端通用距离、区域、锚点与超时规则。
 * 距离按水平计算，因为原版实体追踪（有效半径背后的引擎限制）只以本体的 x/z 计算距离。
 */
public final class SeekerRemoteRules {
    public static final Set<Identifier> BLOCKED_WHILE_VIEWING = Set.copyOf(List.of(
            Identifier.of("wathe", "knifestab"),
            Identifier.of("wathe", "gunshoot"),
            Identifier.of("wathe", "storebuy"),
            Identifier.of("wathe", "note"),
            Identifier.of("wathe", "walkie_talkie_channel"),

            Identifier.of("noellesroles", "ability"),
            Identifier.of("noellesroles", "assassin_guess_role"),
            Identifier.of("noellesroles", "detective_investigate"),
            Identifier.of("noellesroles", "morph"),
            Identifier.of("noellesroles", "morph_corpse_toggle"),
            Identifier.of("noellesroles", "party_animal_buzz"),
            Identifier.of("noellesroles", "reporter_mark"),
            Identifier.of("noellesroles", "silencer_silence"),
            Identifier.of("noellesroles", "spirit_project"),
            Identifier.of("noellesroles", "swapper"),
            Identifier.of("noellesroles", "taotie_swallow"),
            Identifier.of("noellesroles", "vulture"),
            Identifier.of("noellesroles", "demon_hunter_shoot"),
            Identifier.of("noellesroles", "shadow_ally_request"),

            Identifier.of("sparkwitch", "use_skill"),
            Identifier.of("sparkwitch", "emma_factor"),
            Identifier.of("sparkwitch", "fire_death_ray"),
            Identifier.of("sparkwitch", "fire_potion_launcher"),
            Identifier.of("sparkwitch", "use_curser_ability"),
            Identifier.of("sparkwitch", "use_orthopedist_skill"),
            Identifier.of("sparkwitch", "use_saboteur_skill"),
            Identifier.of("sparkwitch", "throw_kidnapper_body"),
            Identifier.of("sparkwitch", "guardian"),
            Identifier.of("sparkwitch", "vendetta_knife_stab"),
            Identifier.of("sparkwitch", "recruit_accomplice"),
            Identifier.of("sparkwitch", "open_judge_selection"),
            Identifier.of("sparkwitch", "confirm_judge_selection"),
            Identifier.of("sparkwitch", "submit_tarot_divination_selection"),
            Identifier.of("sparkwitch", "seeker_car_swallow"),
            Identifier.of("sparkwitch", "seeker_car_recall"),
            Identifier.of("sparkwitch", "select_black_raven_disguise"),

            Identifier.of("sparkstrength", "noisemaker_glow"),
            Identifier.of("sparkstrength", "phantom_backpack_invisibility"),
            Identifier.of("sparkstrength", "coroner_morph"),
            Identifier.of("sparkstrength", "professor_remote_feed"),
            Identifier.of("sparkstrength", "demon_hunter_sniff"),
            Identifier.of("sparkstrength", "call_tablet_meeting"),
            Identifier.of("sparkstrength", "cast_tablet_vote"),
            Identifier.of("sparkstrength", "confirm_tablet_vote"),
            Identifier.of("sparkstrength", "approve_suspect_removal"),
            Identifier.of("sparkstrength", "select_criminologist_target")));

    /** Grace before an unsupported or wet body ends the session (BODY_MOVED). / 本体离地或入水多久后结束会话。 */
    public static final int UNGROUNDED_GRACE_TICKS = 10;
    /**
     * Extra room for the per-tick range check: the body may drift sqrt(2) from its anchor while the car stays clamped
     * to the radius, so the check never ends a session for that drift alone.
     * 逐刻距离检查的额外余量：本体可在锚点 √2 内漂移而小车仍被钳制在半径内，因此不会仅因这点漂移结束会话。
     */
    public static final double RANGE_CHECK_SLACK = Math.sqrt(SeekerRules.BODY_MOVE_TOLERANCE_SQUARED);

    private SeekerRemoteRules() {
    }

    public static boolean isBlockedWhileViewing(@Nullable Identifier payloadId) {
        return payloadId != null && BLOCKED_WHILE_VIEWING.contains(payloadId);
    }

    public static double horizontalDistanceSquared(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    /** Open check: device within the effective radius of the body. / 打开检查：设备在本体的有效半径内。 */
    public static boolean withinEffectiveRadius(Vec3d body, Vec3d device, int effectiveRadius) {
        return SeekerRules.withinRadius(horizontalDistanceSquared(body, device), effectiveRadius);
    }

    /** Tick check: as {@link #withinEffectiveRadius} plus {@link #RANGE_CHECK_SLACK}. / 逐刻检查：带余量。 */
    public static boolean withinSessionRange(Vec3d body, Vec3d device, int effectiveRadius) {
        if (effectiveRadius <= 0) {
            return false;
        }
        double limit = effectiveRadius + RANGE_CHECK_SLACK;
        return horizontalDistanceSquared(body, device) <= limit * limit;
    }

    /**
     * Inside the Wathe play area: horizontally within the box and not below its floor (the ceiling is not a limit).
     * 位于 Wathe 游戏区域内：水平在盒内且不低于其底面（顶面不作限制）。
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
