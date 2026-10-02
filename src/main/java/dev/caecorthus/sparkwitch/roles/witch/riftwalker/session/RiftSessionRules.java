package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftGateUser;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateCloseReason;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Pure rules of the "inside a Rift Gate" session (plan §6–§7; D3, D5, D5b, D11, D14, C1–C3): entry refusal order,
 * per-exit-reason body handling, the game-mode ownership rule of the alive-spectator body model, the re-entry
 * cooldown, the occupant payload deny-list and the SparkFactionAPI actions an occupant still receives. Tuning numbers
 * that are not frozen in {@code RiftwalkerRules} live here.
 * 「在裂隙门内」会话的纯规则（plan §6–§7；D3、D5、D5b、D11、D14、C1–C3）：进门拒绝顺序、各出门原因的本体处理、活着的旁观者
 * 模型的游戏模式归属规则、再次进门冷却、门内玩家的数据包拦截名单，以及门内玩家仍会受到的 SparkFactionAPI 行为。
 * 未在 {@code RiftwalkerRules} 中冻结的调参数值放在这里。
 */
public final class RiftSessionRules {
    public static final int TICKS_PER_SECOND = 20;
    /**
     * A body farther than sqrt(2) from its anchor after a foreign server teleport ends the session as BODY_MOVED (the
     * Seeker body tolerance); closer foreign moves and every client-side drift are snapped back to the anchor.
     * 外部服务端传送使本体偏离锚点超过 √2 时以 BODY_MOVED 结束会话（与搜寻者本体容差相同）；更近的外部移动与所有客户端漂移
     * 都被拉回锚点。
     */
    public static final double BODY_MOVE_TOLERANCE_SQUARED = 2.0;
    /** Below this squared drift the body is "at the anchor" (no snap). / 偏移平方小于此值视为在锚点上（不拉回）。 */
    public static final double ANCHOR_EPSILON_SQUARED = 1.0E-4;
    /** Entry/exit cue volume: audible nearby, never loud (plan §5.3). / 进出门提示音量：附近可闻但不响。 */
    public static final float CUE_VOLUME = 0.4F;

    public static final String MESSAGE_PREFIX = "message.sparkwitch.riftwalker.session.";
    public static final String COOLDOWN_KEY = MESSAGE_PREFIX + "cooldown";
    public static final String NOT_ENOUGH_MANA_KEY = MESSAGE_PREFIX + "not_enough_mana";
    public static final String UNAVAILABLE_KEY = MESSAGE_PREFIX + "unavailable";
    public static final String TOO_FAR_KEY = MESSAGE_PREFIX + "too_far";
    public static final String NO_OTHER_GATE_KEY = MESSAGE_PREFIX + "no_other_gate";
    public static final String EXIT_BLOCKED_KEY = MESSAGE_PREFIX + "exit_blocked";
    public static final String STAY_EXPIRED_KEY = MESSAGE_PREFIX + "stay_expired";
    public static final String GATE_CLOSED_KEY = MESSAGE_PREFIX + "gate_closed";
    public static final String INELIGIBLE_KEY = MESSAGE_PREFIX + "ineligible";
    /** Shop denial reason shown by Wathe's shop screen. / Wathe 商店界面显示的拒绝原因。 */
    public static final String SHOP_BLOCKED_KEY = MESSAGE_PREFIX + "shop_blocked";

    /**
     * Riftwalker-owned literal deny-list for occupants (separate from the Seeker, Control Expert and Fear lists): the
     * Seeker session list (lethal Wathe actions, the shop, UI actions, every known role-skill payload) minus
     * {@code sparkwitch:rift_hop}, which is the occupant's own control. {@code sparkwitch:rift_exit} is never listed:
     * leaving must always pass. The console snapshot request stays allowed (UI only); closing a gate is denied.
     * 隙行者自有的门内拦截字面量名单（独立于搜寻者、控场专家与恐惧名单）：即搜寻者会话名单（Wathe 致命行为、商店、界面操作、
     * 所有已知职业技能包）去掉门内玩家自己的操作 {@code sparkwitch:rift_hop}。{@code sparkwitch:rift_exit} 从不在列：出门必须
     * 始终放行。控制台快照请求保持放行（仅界面），关门被拦截。
     */
    public static final Set<Identifier> BLOCKED_WHILE_INSIDE = Set.copyOf(List.of(
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
            Identifier.of("sparkwitch", "rift_gate_close"),

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

    /**
     * SparkFactionAPI actions that may still target an occupant. {@code noellesroles:swapper} must pass so the D13
     * portal crush (P9) runs; {@code wathe:poison} is the poisoner-attributed poison death (Wathe
     * {@code PlayerPoisonComponent}, routed through SFA's {@code killPlayer} veto with the death reason as the action),
     * which D3 keeps lethal inside. Everything else targeting an occupant is denied.
     * 仍可作用于门内玩家的 SparkFactionAPI 行为。必须放行 {@code noellesroles:swapper}，D13 的传送门夹死（P9）才能运行；
     * {@code wathe:poison} 是可归因到下毒者的毒杀（Wathe {@code PlayerPoisonComponent}，经 SFA 的 {@code killPlayer} 否决，
     * 以死因作为行为 id），D3 规定门内仍会被毒死。其余作用于门内玩家的行为一律拒绝。
     */
    public static final Set<Identifier> AFFECT_ALLOWED_ON_OCCUPANT = Set.of(
            Identifier.of("noellesroles", "swapper"),
            Identifier.of("wathe", "poison"));

    private RiftSessionRules() {
    }

    public static boolean isBlockedWhileInside(@Nullable Identifier payloadId) {
        return payloadId != null && BLOCKED_WHILE_INSIDE.contains(payloadId);
    }

    /** Fail closed: an unknown or null action never reaches an occupant. / 失败即关闭：未知或空行为永远到达不了门内玩家。 */
    public static boolean mayAffectOccupant(@Nullable Identifier actionId) {
        return actionId != null && AFFECT_ALLOWED_ON_OCCUPANT.contains(actionId);
    }

    /**
     * The occupant player-affect policy decision: only a target inside a gate is restricted, a player acting on itself
     * is never blocked, and otherwise only {@link #AFFECT_ALLOWED_ON_OCCUPANT} passes.
     * 门内玩家的玩家影响策略判定：只限制位于门内的目标；玩家作用于自己时从不拦截；其余只放行
     * {@link #AFFECT_ALLOWED_ON_OCCUPANT}。
     */
    public static boolean occupantPolicyAllows(boolean targetInside, boolean selfAction, @Nullable Identifier actionId) {
        return !targetInside || selfAction || mayAffectOccupant(actionId);
    }

    /** Whole seconds shown to players, rounded up (1..20 ticks → 1 s). / 向玩家显示的整秒，向上取整。 */
    public static int secondsCeil(int ticks) {
        return ticks <= 0 ? 0 : (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /** Only these modes are ours to swap for SPECTATOR and back (never creative). / 只有这两种模式可以换成旁观再换回。 */
    public static boolean isRestorableMode(@Nullable GameMode mode) {
        return mode == GameMode.ADVENTURE || mode == GameMode.SURVIVAL;
    }

    // ---- Entry / 进门 ----

    /**
     * Live facts for one entry request, gathered by the service. {@code participant} = playing and alive, not
     * spectator, not creative, not an active Wraith; {@code controlled} = Taotie-swallowed, Last Stand pending, Last
     * Escape, Kidnapper control, Control Expert stun, a Seeker session or a foreign camera.
     * 一次进门请求的实时事实，由服务收集。{@code participant} = 正在参赛且存活、非旁观、非创造、非激活冤魂；
     * {@code controlled} = 被饕餮吞下、背水一战待决、最后逃亡、被绑架者控制、被控场专家眩晕、处于搜寻者会话或镜头被他人占用。
     */
    public record EntryFacts(RiftGateUser user, boolean roundActive, boolean participant, boolean alreadyInside,
                             boolean controlled, @Nullable GameMode currentMode, boolean gateRegistered,
                             boolean withinReach, int cooldownRemainingTicks, boolean hasManaSystem, int mana) {
    }

    /** Why an entry is refused; {@link #SILENT} sends nothing. / 进门被拒原因；SILENT 不发送任何提示。 */
    public enum EntryDenial {
        SILENT(null),
        UNAVAILABLE(UNAVAILABLE_KEY),
        TOO_FAR(TOO_FAR_KEY),
        COOLDOWN(COOLDOWN_KEY),
        NOT_ENOUGH_MANA(NOT_ENOUGH_MANA_KEY);

        @Nullable
        private final String messageKey;

        EntryDenial(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        @Nullable
        public String messageKey() {
            return messageKey;
        }
    }

    /**
     * Ordered entry validation (D2, D5, C1); null = allowed. Non-users are refused silently first, so a right-click
     * never tells a non-witch anything. The fee is checked last because it is charged only once everything else passed.
     * 有序的进门校验（D2、D5、C1）；null 表示允许。先静默拒绝非使用者，因此右键从不向非魔女透露任何信息。入场费最后检查，
     * 因为只有其他条件全部通过后才扣费。
     */
    @Nullable
    public static EntryDenial entryDenial(EntryFacts facts) {
        if (facts.user() == null || !facts.user().mayUse() || facts.alreadyInside()) {
            return EntryDenial.SILENT;
        }
        if (!facts.roundActive() || !facts.participant() || facts.controlled()
                || !isRestorableMode(facts.currentMode()) || !facts.gateRegistered()) {
            return EntryDenial.UNAVAILABLE;
        }
        if (!facts.withinReach()) {
            return EntryDenial.TOO_FAR;
        }
        if (facts.cooldownRemainingTicks() > 0) {
            return EntryDenial.COOLDOWN;
        }
        if (facts.user().paysMana() && (!facts.hasManaSystem() || facts.mana() < facts.user().entryFee())) {
            return EntryDenial.NOT_ENOUGH_MANA;
        }
        return null;
    }

    // ---- Exit / 出门 ----

    /** What an exit does to the body. / 出门对本体的处理。 */
    public enum BodyAction {
        /** Safe-spot search at the gate, teleport while still spectator, then the ownership rule. / 在门处找安全点。 */
        RELEASE_AT_GATE,
        /** A foreign force moved the body: keep that position, then the ownership rule. / 保留外力造成的位置。 */
        KEEP_POSITION,
        /** Never teleport and never touch the game mode (dead, intercepted, offline, round reset). / 不动本体。 */
        CLEAR_ONLY
    }

    public static BodyAction bodyAction(RiftExitReason reason) {
        return switch (reason) {
            case PLAYER, STAY_EXPIRED, GATE_CLOSED, INELIGIBLE -> BodyAction.RELEASE_AT_GATE;
            case BODY_MOVED -> BodyAction.KEEP_POSITION;
            case INTERCEPTED, DIED, DISCONNECTED, ROUND_END -> BodyAction.CLEAR_ONLY;
        };
    }

    /**
     * A forced release may relax the "no other entity in the way" probe once (blocks, floor and bounds stay strict);
     * the player's own Shift never does, it simply stays inside and is told the way is blocked.
     * 强制出门可以放宽一次「不与其他实体重叠」的检查（方块、地面与边界仍然严格）；玩家自己按 Shift 出门则从不放宽，只会留在
     * 门内并收到出口被堵的提示。
     */
    public static boolean mayRelaxEntityOverlap(RiftExitReason reason) {
        return reason != RiftExitReason.PLAYER && bodyAction(reason) == BodyAction.RELEASE_AT_GATE;
    }

    /** Owner-facing actionbar line for an exit, or null. / 出门时给本人的动作栏提示，或 null。 */
    @Nullable
    public static String exitMessageKey(RiftExitReason reason) {
        return switch (reason) {
            case STAY_EXPIRED -> STAY_EXPIRED_KEY;
            case GATE_CLOSED -> GATE_CLOSED_KEY;
            case INELIGIBLE -> INELIGIBLE_KEY;
            default -> null;
        };
    }

    /**
     * Whether this exit starts the re-entry cooldown (D14, C2, C3): every reason except ROUND_END, and a gate close
     * only when its close reason applies one (never on the round-end sweep).
     * 本次出门是否开始再次进门冷却（D14、C2、C3）：除 ROUND_END 外的所有原因；关门只在其关闭原因要求时才上冷却（对局结束清扫
     * 从不上冷却）。
     */
    public static boolean appliesCooldown(RiftExitReason reason, @Nullable RiftGateCloseReason closeReason) {
        return reason.appliesCooldown() && (closeReason == null || closeReason.appliesExitCooldown());
    }

    /**
     * Re-entry cooldown length by the user class at exit time (the live class if it may still use gates, otherwise
     * the class the session started with). Apprentice and Murderous Witches use the non-Riftwalker 45 s (C1).
     * 按出门时的使用者类别取再次进门冷却（若实时类别仍可用门则取实时类别，否则取进门时的类别）。预备魔女与杀意魔女按非隙行者
     * 计算，为 45 秒（C1）。
     */
    public static int reentryCooldownTicks(@Nullable RiftGateUser sessionUser, @Nullable RiftGateUser liveUser) {
        if (liveUser != null && liveUser.mayUse()) {
            return liveUser.reentryCooldownTicks();
        }
        return sessionUser == null ? 0 : sessionUser.reentryCooldownTicks();
    }

    /**
     * Live facts about the body at exit time, for the game-mode ownership rule.
     * 出门时关于本体的实时事实，用于游戏模式归属规则。
     */
    public record BodyFacts(boolean playingAndAlive, boolean spectatorNow, boolean cameraSelf, boolean swallowed,
                            boolean lastStandPending, @Nullable GameMode previousMode) {
    }

    /**
     * Game-mode ownership (research 03 §3.3 item 8): SPECTATOR is handed back only when the body is released or kept
     * (never for CLEAR_ONLY), the player is still playing and alive, still SPECTATOR (because of us: nobody else changed
     * it), no other authority holds the camera, it is neither Taotie-swallowed nor Last-Stand pending, and the recorded
     * mode is ADVENTURE or SURVIVAL. Anything else leaves the mode alone, so a corpse is never revived.
     * 游戏模式归属（调研 03 §3.3 第 8 条）：只有在释放或保留本体时（CLEAR_ONLY 从不），且玩家仍在参赛并存活、仍为旁观模式
     * （由我们设置，未被他人改动）、镜头未被其他机制占用、既未被饕餮吞下也不处于背水一战待决、记录的模式为冒险或生存时，
     * 才把旁观模式换回。其余情况一律不动游戏模式，因此绝不会复活尸体。
     */
    public static boolean mayRestoreMode(BodyAction action, BodyFacts facts) {
        return action != BodyAction.CLEAR_ONLY
                && facts.playingAndAlive()
                && facts.spectatorNow()
                && facts.cameraSelf()
                && !facts.swallowed()
                && !facts.lastStandPending()
                && isRestorableMode(facts.previousMode());
    }

    // ---- Tick / 逐刻 ----

    /**
     * Live facts for the per-tick check, gathered by the service. {@code roundRunning} is Wathe's {@code isRunning}
     * (ACTIVE or STOPPING): during the post-win fade the occupant stays held at the gate until Wathe's own
     * {@code resetPlayer} (ADVENTURE + spawn teleport) and the ResetPlayer listener end the session silently, so
     * nobody becomes a free-flying spectator for the end screen.
     * 逐刻检查的实时事实，由服务收集。{@code roundRunning} 即 Wathe 的 {@code isRunning}（ACTIVE 或 STOPPING）：胜负后的
     * 淡出期间门内玩家仍被固定在门处，直到 Wathe 自己的 {@code resetPlayer}（冒险模式 + 传送回出生点）与 ResetPlayer 监听器
     * 静默结束会话，因此结算画面期间不会有人变成自由飞行的旁观者。
     */
    public record TickFacts(boolean roundRunning, boolean matchBound, boolean sameWorld, boolean dead,
                            boolean swallowed, boolean lastStandPending, boolean cameraSelf, boolean spectatorNow,
                            boolean classUnchanged, boolean gateRegistered, boolean foreignMoveBeyondTolerance,
                            boolean stayExpired) {
    }

    /**
     * First failing per-tick check (plan §6.5, research 03 §4.3), or null to stay inside. Round edges first (silent),
     * then other authorities on the body (never touch the mode), then death, then eligibility and the gate, then a
     * foreign move, then the stay limit. The order matters: a Last Stand or Taotie hold also teleports the body, which
     * must read as INTERCEPTED, never BODY_MOVED.
     * 第一个失败的逐刻检查（plan §6.5、调研 03 §4.3），或 null 表示留在门内。先是对局边界（静默），再是其他机制接管本体
     * （不动游戏模式），然后是死亡、资格与门，再是外力移动，最后是停留上限。顺序很重要：背水一战或饕餮也会传送本体，
     * 必须判为 INTERCEPTED 而不是 BODY_MOVED。
     */
    @Nullable
    public static RiftExitReason tickExitReason(TickFacts facts) {
        if (!facts.roundRunning() || !facts.matchBound()) {
            return RiftExitReason.ROUND_END;
        }
        if (facts.swallowed() || facts.lastStandPending() || !facts.cameraSelf() || !facts.spectatorNow()) {
            return RiftExitReason.INTERCEPTED;
        }
        if (facts.dead()) {
            return RiftExitReason.DIED;
        }
        if (!facts.sameWorld()) {
            return RiftExitReason.BODY_MOVED;
        }
        if (!facts.classUnchanged()) {
            return RiftExitReason.INELIGIBLE;
        }
        if (!facts.gateRegistered()) {
            return RiftExitReason.GATE_CLOSED;
        }
        if (facts.foreignMoveBeyondTolerance()) {
            return RiftExitReason.BODY_MOVED;
        }
        if (facts.stayExpired()) {
            return RiftExitReason.STAY_EXPIRED;
        }
        return null;
    }
}
