package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Stable ids, tuning and pure rules of the Black Raven's second skill, Raven Form (化鸦, plan
 * {@code docs/plans/2026-10-21-black-raven-police.md} §0 and §3.6): two completed tasks unlock it, key 2 starts a
 * session of at most 30 s in which the player flies a crow entity while the body stays behind invisible and frozen, and
 * the 45 s cooldown starts when the session ends. Movement math lives in {@link BlackRavenCrowFlightRules}.
 * 黑羽鸦第二技能「化鸦」（计划 §0 与 §3.6）的稳定标识、数值与纯规则：完成两个任务后解锁，按技能键 2 开始最长 30 秒的会话，
 * 期间玩家操控乌鸦实体飞行，本体原地隐身并冻结；会话结束后开始 45 秒冷却。移动计算见 {@link BlackRavenCrowFlightRules}。
 */
public final class BlackRavenCrowRules {
    /** Skill id (replay, HUD, forced-cooldown store). / 技能 id（回放、HUD、强制冷却存储）。 */
    public static final Identifier SKILL_ID = SparkWitch.id("black_raven_crow");
    /** Stable entity id of the flown crow. / 被操控乌鸦的稳定实体 id。 */
    public static final Identifier ENTITY_ID = SparkWitch.id("black_raven_crow");
    /** Stable component id {@code sparkwitch:black_raven_crow}. / 稳定组件 id。 */
    public static final Identifier COMPONENT_ID = SparkWitch.id("black_raven_crow");
    /**
     * Reserved for crow-world: the one-use Homing Feather (归羽) item and its marker. Not registered here.
     * 为 crow-world 预留：一次性的归羽物品及其标记。此处不注册。
     */
    public static final Identifier HOMING_FEATHER_ID = SparkWitch.id("homing_feather");
    /** Reserved for crow-world: the black feather particle type. / 为 crow-world 预留：黑色羽毛粒子类型。 */
    public static final Identifier BLACK_FEATHER_PARTICLE_ID = SparkWitch.id("black_feather");

    public static final int UNLOCK_TASKS = 2;
    public static final int SESSION_MAX_TICKS = GameConstants.getInTicks(0, 30);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(0, 45);

    /** Crow hitbox (owner §2.5: about 0.5 x 0.5); the eye sits at its centre. / 乌鸦命中箱（约 0.5×0.5），眼睛位于中心。 */
    public static final float CROW_WIDTH = 0.5F;
    public static final float CROW_HEIGHT = 0.5F;
    public static final float CROW_EYE_HEIGHT = 0.25F;
    /** Fast and player-like: tracked every tick, 8 chunks. / 高速且类似玩家：每刻追踪，8 区块。 */
    public static final int CROW_TRACKING_RANGE = 8;
    public static final int CROW_TRACKING_INTERVAL = 1;
    /** Remote clients keep drawing the small crow up to 64 blocks. / 远端客户端在 64 格内保持绘制小乌鸦。 */
    public static final double CROW_RENDER_DISTANCE_SQUARED = 64.0 * 64.0;

    /** Body anchor tolerance, as the Seeker: sqrt(2) blocks. / 本体锚点容差，与搜寻者一致：√2 格。 */
    public static final double BODY_MOVE_TOLERANCE_SQUARED = 2.0;
    /** Grace before an unsupported (hanging, not falling) or wet body ends the session. / 本体悬空（未下落）或入水多久后结束会话。 */
    public static final int UNGROUNDED_GRACE_TICKS = 10;
    /**
     * Start-of-session anchor grace, in ticks, scaled by the owner's ping ({@link #anchorGraceTicks}): the server anchors
     * the body when it handles the toggle, but the client freezes it only when the session sync lands, so moves the
     * client made before that (a sprint, a jump) arrive up to one round trip later. During the grace the anchor follows
     * the body and nothing counts as ungrounded; afterwards the body must stay put (only falling is followed).
     * 会话开始时的锚点宽限（刻），按主人延迟缩放（{@link #anchorGraceTicks}）：服务端在处理开关时锚定本体，但客户端要等会话同步到达
     * 才冻结本体，因此此前客户端的移动（疾跑、跳跃）最多晚一个往返才到达。宽限期内锚点跟随本体，且不计离地；之后本体必须原地不动
     * （只跟随下落）。
     */
    public static final int ANCHOR_GRACE_MIN_TICKS = 10;
    public static final int ANCHOR_GRACE_MAX_TICKS = 20;
    /** Ticks added to the ping's round trip (client tick, server tick, jitter). / 往返之外追加的刻数。 */
    public static final int ANCHOR_GRACE_MARGIN_TICKS = 4;
    /** A body this much lower than its anchor is falling. / 本体低于锚点超过此值即视为正在下落。 */
    public static final double BODY_DESCENT_EPSILON = 1.0E-3;
    /**
     * Server invisibility is refreshed to this length every tick while the session runs, so a stray effect clear
     * (Wathe, other mods) never leaves the body visible for more than one tick.
     * 会话期间每刻把服务端隐身刷新到该时长，即使有其他代码清除效果，本体也最多只显形一刻。
     */
    public static final int MAINTAINED_INVISIBILITY_TICKS = 40;

    /**
     * Vanilla damage that would kill an unarmoured, full-health player (20 HP). Only explosions at least this strong
     * kill the crow through {@code damage()}; Wathe weapons reach it through the kill link instead.
     * 足以杀死无护甲满血玩家（20 生命值）的原版伤害。只有不低于该值的爆炸会经 {@code damage()} 打死乌鸦；
     * Wathe 武器改经击杀接口命中它。
     */
    public static final float PLAYER_LETHAL_DAMAGE = 20.0F;

    /** Chunk-streaming tuning, copied from the Seeker remote view. / 区块流式加载参数，照搬搜寻者遥控视角。 */
    public static final int MAX_TICKET_VIEW_DISTANCE = 8;
    public static final int TICKET_MARGIN = 2;
    public static final int MAX_TICKET_RADIUS = 32;
    public static final int TICKET_EXPIRY_TICKS = 40;
    public static final int RESETTLE_TICKS = 40;

    private BlackRavenCrowRules() {
    }

    public static boolean isUnlocked(int completedTasks) {
        return completedTasks >= UNLOCK_TASKS;
    }

    /** True only on the task that reaches the threshold. / 只有达到阈值的那个任务返回 true。 */
    public static boolean unlocksOn(int before, int after) {
        return before < UNLOCK_TASKS && after >= UNLOCK_TASKS;
    }

    /** Whole seconds shown for a tick count (rounded up). / 刻数对应的显示秒数（向上取整）。 */
    public static int ceilSeconds(long ticks) {
        return ticks <= 0 ? 0 : (int) ((ticks + 19) / 20);
    }

    /** Session ticks left at {@code now}, never negative. / {@code now} 时会话剩余刻数，不为负。 */
    public static long remainingSessionTicks(long sessionEndTick, long now) {
        return Math.max(0L, sessionEndTick - now);
    }

    public static boolean sessionTimedOut(long sessionEndTick, long now) {
        return now >= sessionEndTick;
    }

    /** The body left its anchor by more than sqrt(2) blocks. / 本体偏离锚点超过 √2 格。 */
    public static boolean bodyMoved(Vec3d anchor, Vec3d body) {
        return anchor.squaredDistanceTo(body) > BODY_MOVE_TOLERANCE_SQUARED;
    }

    /**
     * Anchor grace for an owner with {@code latencyMs} ping (round trip): the round trip in ticks plus
     * {@link #ANCHOR_GRACE_MARGIN_TICKS}, within [{@link #ANCHOR_GRACE_MIN_TICKS}, {@link #ANCHOR_GRACE_MAX_TICKS}].
     * 延迟（往返）为 {@code latencyMs} 的主人的锚点宽限：往返刻数加 {@link #ANCHOR_GRACE_MARGIN_TICKS}，限制在
     * [{@link #ANCHOR_GRACE_MIN_TICKS}, {@link #ANCHOR_GRACE_MAX_TICKS}] 内。
     */
    public static int anchorGraceTicks(int latencyMs) {
        int pingTicks = (int) Math.ceil(Math.max(0, Math.min(latencyMs, 60_000)) / 50.0);
        return Math.max(ANCHOR_GRACE_MIN_TICKS, Math.min(ANCHOR_GRACE_MAX_TICKS, pingTicks + ANCHOR_GRACE_MARGIN_TICKS));
    }

    /** The body is lower than its anchor: it is falling. / 本体低于锚点：正在下落。 */
    public static boolean isBodyDescending(Vec3d anchor, Vec3d body) {
        return body.y < anchor.y - BODY_DESCENT_EPSILON;
    }

    /**
     * The anchor after this tick's body position: during the grace it is the body itself; afterwards x/z stay fixed
     * and y follows the body only downward (gravity), so walking or rising off the anchor still ends the session.
     * 本刻本体位置之后的锚点：宽限期内就是本体位置；之后 x/z 固定，y 只向下跟随本体（重力），因此离开锚点行走或上升仍会结束会话。
     */
    public static Vec3d nextAnchor(Vec3d anchor, Vec3d body, boolean inGrace) {
        if (inGrace) {
            return body;
        }
        return isBodyDescending(anchor, body) ? new Vec3d(anchor.x, body.y, anchor.z) : anchor;
    }

    /** Start and per-tick footing rule: on the ground, not riding, not in water. / 起始与逐刻立足规则：站在地面、未乘骑、不在水中。 */
    public static boolean isBodyGrounded(boolean onGround, boolean hasVehicle, boolean touchingWater) {
        return onGround && !hasVehicle && !touchingWater;
    }

    /** The crow's feet y that puts its eye at the body's eye. / 使乌鸦眼睛与本体眼睛重合的乌鸦脚底 y。 */
    public static double crowYForEye(double bodyEyeY) {
        return bodyEyeY - CROW_EYE_HEIGHT;
    }

    /**
     * The engine view distance, as {@code ServerChunkLoadingManager#getViewDistance}. / 引擎视距，与原版一致。
     */
    public static int engineViewDistance(int serverViewDistance, int clientViewDistance) {
        int server = Math.max(2, Math.min(32, serverViewDistance));
        return Math.max(2, Math.min(server, clientViewDistance));
    }

    /** Crow ticket radius: min(view, cap) + margin. / 乌鸦票据半径：min(视距, 上限) + 余量。 */
    public static int focusTicketRadius(int serverViewDistance, int clientViewDistance) {
        int view = Math.min(engineViewDistance(serverViewDistance, clientViewDistance), MAX_TICKET_VIEW_DISTANCE);
        return Math.min(MAX_TICKET_RADIUS, view + TICKET_MARGIN);
    }

    /**
     * Ordered verdict of one key-2 press that would start a session (a press during a session always ends it first).
     * Checks run in declaration order (plan §2.7 start conditions, Mini Wither toggle order).
     * 一次将开始会话的技能键 2 按下的有序判定（会话中按下总是先结束会话）。按声明顺序检查（计划 §2.7 开始条件、小凋灵切换顺序）。
     */
    public enum ToggleVerdict {
        START(null),
        /** Not an ACTIVE round, not playing and alive, a spectator, or not the RAW Black Raven. / 静默。 */
        NOT_ELIGIBLE(null),
        /** crow-world's 3 s landing hold: no skills. / crow-world 的 3 秒落地定身：不能使用技能。 */
        LANDING_HELD(null),
        /** SparkTraits role-skill block (Last Escape, silence) or Taotie-swallowed: silent like other skills. / 静默。 */
        SILENT_BLOCKED(null),
        LOCKED("locked"),
        /** Grand Witch Fear; the Fear service sends its own message. / 大魔女恐惧；由恐惧服务发送提示。 */
        FEARED(null),
        STUNNED("blocked"),
        COOLDOWN("cooldown"),
        DISGUISED("disguised"),
        KIDNAPPED("blocked"),
        IN_RIFT_GATE("blocked"),
        IN_SEEKER_VIEW("blocked"),
        SLEEPING("sleeping"),
        RIDING("riding"),
        NOT_GROUNDED("not_grounded");

        private final @Nullable String messageSuffix;

        ToggleVerdict(@Nullable String messageSuffix) {
            this.messageSuffix = messageSuffix;
        }

        /**
         * Action-bar key under {@code message.sparkwitch.black_raven_crow.}, or null when silent (FEARED is answered by
         * the Fear service, COOLDOWN uses the shared skill-cooldown key).
         * {@code message.sparkwitch.black_raven_crow.} 下的动作栏键；静默时为 null（FEARED 由恐惧服务答复，COOLDOWN 使用共用冷却键）。
         */
        public @Nullable String messageKey() {
            if (this == COOLDOWN) {
                return "message.sparkwitch.skill.cooldown";
            }
            return messageSuffix == null ? null : "message.sparkwitch.black_raven_crow." + messageSuffix;
        }
    }

    /**
     * Inputs of one start request, already resolved on the server thread. {@code eligible} covers an ACTIVE round,
     * playing and alive, not a spectator, and the RAW role {@code sparkwitch:black_raven} (never the acting overlay).
     * 服务端线程已解析的一次开始请求输入。{@code eligible} 涵盖 ACTIVE 回合、存活参与、非旁观者，且原始职业为黑羽鸦
     * （绝不使用伪装覆盖层）。
     */
    public record ToggleFacts(
            boolean eligible,
            boolean landingHeld,
            boolean skillBlocked,
            boolean swallowed,
            boolean unlocked,
            boolean feared,
            boolean stunned,
            int cooldownTicks,
            boolean disguised,
            boolean kidnapped,
            boolean inRiftGate,
            boolean inSeekerView,
            boolean sleeping,
            boolean riding,
            boolean grounded
    ) {
    }

    /**
     * Start gate. Specific states (kidnapped, gate, Seeker view, bed, vehicle) answer before the generic footing check
     * so the player is told the real reason.
     * 开始门槛。具体状态（被绑、门内、搜寻者视角、床、载具）先于通用立足检查，使玩家得知真正原因。
     */
    public static ToggleVerdict toggleVerdict(ToggleFacts facts) {
        if (!facts.eligible()) {
            return ToggleVerdict.NOT_ELIGIBLE;
        }
        if (facts.landingHeld()) {
            return ToggleVerdict.LANDING_HELD;
        }
        if (facts.skillBlocked() || facts.swallowed()) {
            return ToggleVerdict.SILENT_BLOCKED;
        }
        if (!facts.unlocked()) {
            return ToggleVerdict.LOCKED;
        }
        if (facts.feared()) {
            return ToggleVerdict.FEARED;
        }
        if (facts.stunned()) {
            return ToggleVerdict.STUNNED;
        }
        if (facts.cooldownTicks() > 0) {
            return ToggleVerdict.COOLDOWN;
        }
        if (facts.disguised()) {
            return ToggleVerdict.DISGUISED;
        }
        if (facts.kidnapped()) {
            return ToggleVerdict.KIDNAPPED;
        }
        if (facts.inRiftGate()) {
            return ToggleVerdict.IN_RIFT_GATE;
        }
        if (facts.inSeekerView()) {
            return ToggleVerdict.IN_SEEKER_VIEW;
        }
        if (facts.sleeping()) {
            return ToggleVerdict.SLEEPING;
        }
        if (facts.riding()) {
            return ToggleVerdict.RIDING;
        }
        if (!facts.grounded()) {
            return ToggleVerdict.NOT_GROUNDED;
        }
        return ToggleVerdict.START;
    }
}
