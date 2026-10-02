package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Witches' Sabbath rules (plan §10, D6, C6): the caster refusal order, the per-teammate skip order, the mana
 * gate, and the message keys and tuning numbers this package owns. No Minecraft state is read here; the server
 * adapters in {@link WitchesSabbathTargets} feed the probes lazily, so a later (more expensive) seam is consulted only
 * when every earlier check passed — the SparkFactionAPI veto always last.
 * 魔女集会的纯规则（plan §10、D6、C6）：施放者拒绝顺序、逐个队友的跳过顺序、魔力门槛，以及本包自有的消息键与数值。
 * 此处不读取任何 Minecraft 状态；{@link WitchesSabbathTargets} 中的服务端适配器惰性提供探针，只有前序检查全部通过时
 * 才会查询后面（更昂贵）的接缝——SparkFactionAPI 否决永远最后。
 */
public final class WitchesSabbathRules {
    // --- Message keys (actionbar) / 消息键（动作栏） ---
    public static final String UNAVAILABLE = "message.sparkwitch.skill.unavailable";
    public static final String NOT_ENOUGH_MANA = "message.sparkwitch.skill.not_enough_mana";
    public static final String NO_TARGETS = "message.sparkwitch.riftwalker.sabbath.no_targets";
    public static final String NO_SPACE = "message.sparkwitch.riftwalker.sabbath.no_space";
    /**
     * No spot because the caster stands in a door cell: every sight ray would cross it (N-5).
     * 施放者站在门格里导致没有落点：每条视线射线都会穿过该门格（N-5）。
     */
    public static final String IN_DOORWAY = "message.sparkwitch.riftwalker.sabbath.in_doorway";
    /** Caster feedback: {@code %s} = teammates pulled. / 施放者反馈：{@code %s} = 召集人数。 */
    public static final String CAST = "message.sparkwitch.riftwalker.sabbath.cast";
    /** Caster feedback: pulled, then left behind for lack of room. / 施放者反馈：召集人数与因空位不足未召集的人数。 */
    public static final String CAST_PARTIAL = "message.sparkwitch.riftwalker.sabbath.cast_partial";
    /** Pulled teammate: {@code %s} = the caster's name. / 被召集的队友：{@code %s} = 施放者名字。 */
    public static final String SUMMONED = "message.sparkwitch.riftwalker.sabbath.summoned";
    /** Replay line: actor, then the pulled names. / 回放：施放者，随后为被召集者名单。 */
    public static final String REPLAY = "replay.skill.sparkwitch.witches_sabbath";

    // --- Landing search (research/04 §2) / 落点搜索（research/04 §2） ---
    /** Rings around the caster's feet, inner first. / 以施放者脚下为中心的圆环，由内向外。 */
    public static final double[] RING_RADII = {1.0, 1.5, 2.0};
    /** A candidate may step up or down this far onto the real floor (carpets, slabs). / 候选点可上下吸附到实际地面的高度。 */
    public static final double FLOOR_STEP = 0.5;
    /**
     * How far below an airborne (falling) caster the rings look for the floor they are anchored on.
     * 施放者在空中（下落）时，圆环向下寻找锚定地面的深度。
     */
    public static final double AIRBORNE_FLOOR_DEPTH = 1.5;
    /** Floor probe depth under the whole footprint (Angler exit precedent). / 整个脚底下的地面探测深度（钓鱼佬出口先例）。 */
    public static final double SUPPORT_DEPTH = 0.0625;
    /** Door cells this far below the feet also reject a spot (open-door drop column). / 脚下这么深内的门格也拒绝（开门掉落列）。 */
    public static final double DOOR_COLUMN_DROP = 1.0;
    /** Clearance kept from any Rift Gate box. / 与任何裂隙门碰撞箱保持的间隙。 */
    public static final double GATE_MARGIN = 0.1;
    /**
     * Height above both feet of the caster→spot collider ray: under the top of a one-block wall or counter, above
     * carpets, slabs and beds. / 施放者→落点碰撞射线距两端脚底的高度：低于一格高的墙或柜台顶，高于地毯、台阶与床。
     */
    public static final double SIGHT_RAY_HEIGHT = 0.9;
    /**
     * A spot is refused when the standing box grown by these margins overlaps a placed Hunter trap: the same growth
     * as the trap's own trigger box ({@code HunterTrapEntity} TRIGGER_EXPAND_XZ / _Y), so a pulled teammate is never
     * dropped where the trap would catch them on its next tick.
     * 站立碰撞箱按以下余量扩大后与已放置的猎人捕兽夹重叠时拒绝该落点：与捕兽夹自身触发箱的扩展量相同
     * （{@code HunterTrapEntity} 的 TRIGGER_EXPAND_XZ / _Y），被召集的队友不会落在捕兽夹下一 tick 就能夹住的位置。
     */
    public static final double HUNTER_TRAP_MARGIN_XZ = 0.35;
    public static final double HUNTER_TRAP_MARGIN_Y = 0.15;

    private WitchesSabbathRules() {
    }

    /** Why the caster may not cast (no mana spent, no cooldown). / 施放者不能施放的原因（不扣魔力、不进冷却）。 */
    public enum CasterRefusal {
        NOT_RIFTWALKER(UNAVAILABLE),
        ROUND_NOT_ACTIVE(UNAVAILABLE),
        NOT_PARTICIPANT(UNAVAILABLE),
        CAMERA_ELSEWHERE(UNAVAILABLE),
        SWALLOWED(UNAVAILABLE),
        LAST_STAND_PENDING(UNAVAILABLE),
        LAST_ESCAPE(UNAVAILABLE),
        KIDNAPPER_CONTROLLED(UNAVAILABLE),
        CAPTURE_STUNNED(UNAVAILABLE),
        ROLE_SKILL_BLOCKED(UNAVAILABLE),
        GLIMMERING(UNAVAILABLE);

        private final String messageKey;

        CasterRefusal(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    /** Why one teammate is not pulled. / 某名队友不被召集的原因。 */
    public enum TargetSkip {
        CASTER,
        NOT_PARTICIPANT,
        NOT_WITCH_FACTION,
        CAMERA_ELSEWHERE,
        SWALLOWED,
        LAST_STAND_PENDING,
        LAST_ESCAPE,
        KIDNAPPER_CONTROLLED,
        CAPTURE_STUN_PINNED,
        SEEKER_SESSION,
        INSIDE_GATE,
        FACTION_VETO
    }

    /**
     * Lazily evaluated caster state. "Participant" = playing and alive in Wathe, not spectating (this also covers the
     * in-gate living-spectator body and fake deaths), not creative, not an active Wraith, has a role.
     * 惰性求值的施放者状态。「参与者」= 在 Wathe 中参与且存活、非旁观（也覆盖门内活体旁观者与假死）、非创造、非激活冤魂、有职业。
     */
    public interface CasterProbe {
        boolean exactRiftwalker();

        boolean roundActive();

        boolean participant();

        boolean ownCamera();

        boolean swallowed();

        boolean lastStandPending();

        boolean lastEscape();

        boolean kidnapperControlled();

        /**
         * SparkStrength capture stun: SparkStrength only locks the input on the client, so the server refuses too.
         * SparkStrength 捕捉眩晕：SparkStrength 只在客户端锁定输入，因此服务端同样拒绝。
         */
        boolean captureStunned();

        boolean roleSkillBlocked();

        boolean glimmering();
    }

    /** Lazily evaluated teammate state; same participant meaning as the caster's. / 惰性求值的队友状态；参与者含义同上。 */
    public interface TargetProbe {
        boolean isCaster();

        boolean participant();

        /** SFA effective faction is exactly {@code sparkwitch:witch} (raw role; unknown → false). / 有效阵营恰为魔女阵营。 */
        boolean witchFaction();

        boolean ownCamera();

        boolean swallowed();

        boolean lastStandPending();

        boolean lastEscape();

        boolean kidnapperControlled();

        /**
         * D19: SparkStrength capture-stunned AND the lock point cannot be moved to the landing spot (fail closed). A
         * capture stun whose lock can move, a Hunter trap root and a Control Expert stun are pulled and stay held.
         * D19：处于 SparkStrength 捕捉眩晕，且锁定点无法移到落点（失败即关闭）。锁定点可移动的捕捉眩晕、猎人捕兽夹定身与
         * 控场专家眩晕都会被召集，并保持原状态。
         */
        boolean captureStunPinned();

        boolean seekerSession();

        boolean insideGate();

        /** {@code SparkFactionApi.canAffectPlayer(caster, p, SABBATH_ACTION_ID, game)}. */
        boolean factionAllows();
    }

    /**
     * First refusal in a fixed order, or null when the caster may proceed. There is no separate "inside a gate"
     * refusal: the occupant's {@code sparkwitch:use_skill} payload is already dropped by the in-gate payload guard, and
     * the in-gate body is a living spectator, so it would fail {@code participant} anyway.
     * 按固定顺序返回第一个拒绝原因；可继续时返回 null。没有单独的「在门内」拒绝：门内玩家的 {@code sparkwitch:use_skill}
     * 数据包已被门内数据包守卫丢弃，且门内本体是活着的旁观者，本就无法通过 {@code participant}。
     */
    public static @Nullable CasterRefusal casterRefusal(CasterProbe caster) {
        if (!caster.exactRiftwalker()) {
            return CasterRefusal.NOT_RIFTWALKER;
        }
        if (!caster.roundActive()) {
            return CasterRefusal.ROUND_NOT_ACTIVE;
        }
        if (!caster.participant()) {
            return CasterRefusal.NOT_PARTICIPANT;
        }
        if (!caster.ownCamera()) {
            return CasterRefusal.CAMERA_ELSEWHERE;
        }
        if (caster.swallowed()) {
            return CasterRefusal.SWALLOWED;
        }
        if (caster.lastStandPending()) {
            return CasterRefusal.LAST_STAND_PENDING;
        }
        if (caster.lastEscape()) {
            return CasterRefusal.LAST_ESCAPE;
        }
        if (caster.kidnapperControlled()) {
            return CasterRefusal.KIDNAPPER_CONTROLLED;
        }
        if (caster.captureStunned()) {
            return CasterRefusal.CAPTURE_STUNNED;
        }
        if (caster.roleSkillBlocked()) {
            return CasterRefusal.ROLE_SKILL_BLOCKED;
        }
        if (caster.glimmering()) {
            return CasterRefusal.GLIMMERING;
        }
        return null;
    }

    /**
     * First reason this teammate is skipped, or null when they are pulled. Apprentice and Murderous Witches fail
     * {@code witchFaction} (C6); teammates inside a gate are skipped (D6), never ejected. Stunned, rooted and captured
     * teammates are pulled and stay held at the landing spot (D19, superseding C10's Sabbath half); only a capture stun
     * whose lock point cannot be moved is skipped.
     * 返回该队友被跳过的第一个原因；会被召集时返回 null。预备魔女与杀意魔女在 {@code witchFaction} 处被排除（C6）；
     * 门内的队友被跳过（D6），不会被拉出。被眩晕、定身或捕捉的队友会被召集，并在落点保持原状态（D19，取代 C10 中关于
     * 魔女集会的后半句）；只有锁定点无法移动的捕捉眩晕会被跳过。
     */
    public static @Nullable TargetSkip targetSkip(TargetProbe target) {
        if (target.isCaster()) {
            return TargetSkip.CASTER;
        }
        if (!target.participant()) {
            return TargetSkip.NOT_PARTICIPANT;
        }
        if (!target.witchFaction()) {
            return TargetSkip.NOT_WITCH_FACTION;
        }
        if (!target.ownCamera()) {
            return TargetSkip.CAMERA_ELSEWHERE;
        }
        if (target.swallowed()) {
            return TargetSkip.SWALLOWED;
        }
        if (target.lastStandPending()) {
            return TargetSkip.LAST_STAND_PENDING;
        }
        if (target.lastEscape()) {
            return TargetSkip.LAST_ESCAPE;
        }
        if (target.kidnapperControlled()) {
            return TargetSkip.KIDNAPPER_CONTROLLED;
        }
        if (target.captureStunPinned()) {
            return TargetSkip.CAPTURE_STUN_PINNED;
        }
        if (target.seekerSession()) {
            return TargetSkip.SEEKER_SESSION;
        }
        if (target.insideGate()) {
            return TargetSkip.INSIDE_GATE;
        }
        if (!target.factionAllows()) {
            return TargetSkip.FACTION_VETO;
        }
        return null;
    }

    /** The mana gate checked before the landing search (the spend itself re-checks). / 落点搜索前的魔力门槛（实际扣除时再次校验）。 */
    public static boolean canAfford(boolean hasManaSystem, int mana) {
        return hasManaSystem && mana >= RiftwalkerRules.SABBATH_MANA_COST;
    }
}
