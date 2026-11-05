package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.PoliceRoles;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * 夏目安安 Natsume Anan — killer hypnotist (PR #194). Every number and every pure gate lives here.
 * <p>The ability key opens a target menu; confirming hypnotises the pick if they are within {@link #RANGE} blocks
 * and in the caster's line of sight (she may pick herself). A hypnotised player is blinded, loses
 * {@link #SANITY_PER_SECOND} real sanity per second (a breakdown is credited to her) and cannot use items or role
 * skills, whatever their faction (owner decision). Non-police civilians stay hypnotised 12 s, everyone else 8 s
 * ({@link #durationTicks}). The cooldown is 60 s at role assignment and 90 s after each cast.
 * 夏目安安——杀手阵营的催眠师（PR #194）。所有数值与纯规则门槛都在这里。
 * <p>技能键打开目标菜单；确认后，若目标在 {@link #RANGE} 格内且处于施放者视线中，即被催眠（可以选择自己）。被催眠者
 * 失明、每秒失去 {@link #SANITY_PER_SECOND} 真实理智（崩溃死亡记在她名下），且无论阵营都无法使用道具与职业技能（所有者
 * 决定）。非警察平民被催眠 12 秒，其余所有人 8 秒（{@link #durationTicks}）。冷却：分配职业时 60 秒，每次施放后 90 秒。
 */
public final class NatsumeRules {

    public static final Identifier ROLE_ID = SparkWitch.id("natsume_anan");

    /**
     * Stable id of the hypnosis skill: the SparkFactionAPI role-skill cooldown store and the
     * {@code canAffectPlayer} action id (Wraith, Vendetta and Rift occupant policies answer it).
     * 催眠技能的稳定 id：SparkFactionAPI 职业技能冷却存储的 id，同时也是 {@code canAffectPlayer} 的行为 id
     * （冤魂、复仇者与裂隙门内玩家策略据此作答）。
     */
    public static final Identifier HYPNOSIS_SKILL_ID = SparkWitch.id("natsume_hypnosis");

    /** Wathe's 亡命徒 (Loose End); always the short hypnosis. / Wathe 的亡命徒；始终为短催眠。 */
    static final Identifier LOOSE_END_ROLE_ID = Identifier.of("wathe", "loose_end");

    /** Identity colour C5B4FF. 身份色 C5B4FF。 */
    public static final int COLOR = 0xC5B4FF;

    /** Cooldowns: 60 s at role assignment, 90 s after each cast. 冷却：分配职业时 60 秒，每次施放后 90 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 60 * TickTime.TICKS_PER_SECOND;
    public static final int COOLDOWN_TICKS = 90 * TickTime.TICKS_PER_SECOND;

    /** Hypnosis on a non-police civilian: 12 s. 非警察平民的催眠时长：12 秒。 */
    public static final int HYPNOSIS_TICKS = 12 * TickTime.TICKS_PER_SECOND;
    /** Hypnosis on everyone else (police, killers, Loose End, neutrals, witches, Jiahao): 8 s. 其余所有人：8 秒。 */
    public static final int SHORT_HYPNOSIS_TICKS = 8 * TickTime.TICKS_PER_SECOND;

    /** Casting range in blocks; the caster must also see the target. 施放距离（格）；且施放者必须看得见目标。 */
    public static final double RANGE = 3.0D;

    /** Real sanity drained per whole second of hypnosis (4% of the bar). 催眠期间每整秒扣除的真实理智（4%）。 */
    public static final float SANITY_PER_SECOND = 0.04F;
    /** Ticks between the victim-only warden roars, counted down from the window's end. 只有受害者能听到的监守者咆哮的间隔刻数，自窗口结束点倒数。 */
    static final int ROAR_INTERVAL_TICKS = 2 * TickTime.TICKS_PER_SECOND;
    /** Wathe's sanity floor; a REAL-mood player at or below it breaks down. Wathe 的理智下限；真实理智到达即崩溃。 */
    static final float MOOD_FLOOR = -1.0F;

    private NatsumeRules() {
    }

    public static boolean isNatsume(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /** How a target is classified for the hypnosis length. / 按催眠时长对目标的分类。 */
    public enum TargetClass {
        /** A non-police civilian. / 非警察平民。 */
        CIVILIAN,
        /** A SparkFactionAPI police role whose base faction is civilian. / 基础阵营为平民的 SparkFactionAPI 警职。 */
        POLICE,
        /** Effective faction killer. / 有效阵营为杀手。 */
        KILLER,
        /** Wathe's {@code wathe:loose_end}. / Wathe 的亡命徒。 */
        LOOSE_END,
        /** Any other effective faction: neutral, witch, Jiahao or a custom faction. / 其他有效阵营：中立、魔女、嘉豪或自定义阵营。 */
        OTHER_FACTION
    }

    /**
     * Pure classification, in this order: Loose End by id; police (PoliceRoles membership on a role whose base faction
     * is civilian, the SparkTraits police rule, so the Jiahao Insider and Corrupt Cop never count); then the effective
     * faction: civilian, killer, anything else.
     * 纯分类，依次为：按 id 判定亡命徒；警察（在 PoliceRoles 中登记且基础阵营为平民的职业，与 SparkTraits 的警察规则相同，
     * 因此嘉豪阵营的内应与黑警不算）；然后看有效阵营：平民、杀手、其他。
     */
    public static TargetClass classify(@Nullable Identifier roleId, boolean police,
                                       @Nullable Identifier effectiveFaction) {
        if (LOOSE_END_ROLE_ID.equals(roleId)) {
            return TargetClass.LOOSE_END;
        }
        if (police) {
            return TargetClass.POLICE;
        }
        if (FactionIds.CIVILIAN.equals(effectiveFaction)) {
            return TargetClass.CIVILIAN;
        }
        if (FactionIds.KILLER.equals(effectiveFaction)) {
            return TargetClass.KILLER;
        }
        return TargetClass.OTHER_FACTION;
    }

    /**
     * The single duration switch. Owner: non-police civilians 12 s; police, killers and Loose End 8 s. Neutrals,
     * witches and Jiahao take 8 s as the coordinator default; change that here only.
     * 唯一的时长开关。所有者：非警察平民 12 秒；警察、杀手与亡命徒 8 秒。中立、魔女与嘉豪取协调者默认的 8 秒；只在此处修改。
     */
    public static int durationTicks(TargetClass target) {
        return switch (target) {
            case CIVILIAN -> HYPNOSIS_TICKS;
            case POLICE, KILLER, LOOSE_END, OTHER_FACTION -> SHORT_HYPNOSIS_TICKS;
        };
    }

    /**
     * Live hypnosis length for {@code target}: the raw role for Loose End and police, SparkFactionAPI's effective
     * faction for the rest (so a SparkTraits Impostor civilian counts as a killer). No role reads as
     * {@link TargetClass#OTHER_FACTION}.
     * {@code target} 的实时催眠时长：亡命徒与警察读取真实职业，其余读取 SparkFactionAPI 的有效阵营（因此 SparkTraits 内鬼平民
     * 视为杀手）。没有职业时视为 {@link TargetClass#OTHER_FACTION}。
     */
    public static int durationTicksFor(PlayerEntity target, GameWorldComponent game) {
        Role role = game.getRole(target);
        Identifier roleId = role == null ? null : role.identifier();
        boolean police = role != null && PoliceRoles.contains(role)
                && FactionIds.CIVILIAN.equals(SparkFactionApi.resolveBaseFaction(role));
        Identifier effective = role == null ? null : SparkFactionApi.resolveEffectiveFaction(target, game);
        return durationTicks(classify(roleId, police, effective));
    }

    /** Outcome of a menu request or a cast. / 菜单请求或施放的结果。 */
    public enum CastVerdict {
        /** Proceed. / 继续。 */
        USE,
        /** Refused silently; costs nothing. / 静默拒绝，不消耗任何东西。 */
        REFUSE,
        /** Refused by Grand Witch Fear; the caster is told why. / 被大魔女恐惧拒绝，并提示原因。 */
        FEARED
    }

    /**
     * Caster gate for both the menu request and the cast, in the Dominator's order: the raw role in an exactly ACTIVE
     * round; alive, not spectating and not swallowed; not held by another mechanic; not NoellesRoles-silenced; not
     * SparkTraits role-skill blocked; not hypnotised herself; ready. Fear is checked last, so its message only appears
     * when the skill would otherwise run.
     * 菜单请求与施放共用的施放者门槛，顺序同支配者：对局恰为 ACTIVE 且真实职业为夏目安安；存活、非旁观、未被吞下；未被其他
     * 机制持有；未被 NoellesRoles 沉默；未被 SparkTraits 封锁职业技能；自己未被催眠；已就绪。恐惧最后检查，使其提示只在技能
     * 本可生效时出现。
     */
    public static CastVerdict casterVerdict(boolean natsume, boolean roundActive, boolean activeCaster, boolean held,
                                            boolean silenced, boolean roleSkillBlocked, boolean hypnotised,
                                            boolean ready, boolean feared) {
        if (!natsume || !roundActive || !activeCaster || held || silenced || roleSkillBlocked || hypnotised
                || !ready) {
            return CastVerdict.REFUSE;
        }
        return feared ? CastVerdict.FEARED : CastVerdict.USE;
    }

    /**
     * Target gate shared by the menu list and the cast. The caster herself always passes (owner: self-target allowed;
     * her own gates already ran). Anyone else must be a living participant, not spectating, in her world, not
     * swallowed and not inside a Rift Gate; SparkFactionAPI {@code canAffectPlayer} is asked last and only then.
     * 菜单列表与施放共用的目标门槛。施放者本人始终通过（所有者：允许以自己为目标；她自己的门槛已经检查过）。其他人必须是
     * 存活参与者、非旁观、与她同一世界、未被吞下且不在裂隙门内；最后且仅在此时才询问 SparkFactionAPI {@code canAffectPlayer}。
     */
    public static boolean isTargetable(boolean self, boolean playingAndAlive, boolean spectator, boolean sameWorld,
                                       boolean swallowed, boolean riftOccupant, BooleanSupplier factionAllows) {
        if (self) {
            return true;
        }
        return playingAndAlive && !spectator && sameWorld && !swallowed && !riftOccupant
                && factionAllows.getAsBoolean();
    }

    /**
     * Whether the drain lands on this tick: once each time the shown second drops, the last one on the expiry tick, so
     * a 12 s window drains exactly 12 times whatever tick the cast arrived on.
     * 本刻是否扣除理智：显示秒数每下降一次扣一次，最后一次在到期刻，因此无论施放落在哪一刻，12 秒窗口恰好扣 12 次。
     */
    static boolean drainsOn(int shownBefore, int shownNow) {
        return shownNow < shownBefore;
    }

    /** Whether the victim-only roar plays on this tick. / 本刻是否播放只给受害者的咆哮。 */
    static boolean roarsAt(long leftTicks) {
        return leftTicks > 0 && leftTicks % ROAR_INTERVAL_TICKS == 0;
    }

    /** Whole seconds from {@code now} to {@code tick}, rounded up; 0 once reached. / 由 now 到 tick 的整秒（向上取整），到达后为 0。 */
    static int secondsLeft(long tick, long now) {
        return (int) Math.min(Integer.MAX_VALUE, TickTime.ceilSeconds(tick - now));
    }

    /** Sanity after one drain, floored at Wathe's -1. / 一次扣除后的理智，下限为 Wathe 的 -1。 */
    static float drained(float mood) {
        return Math.max(MOOD_FLOOR, mood - SANITY_PER_SECOND);
    }

    /** Whether a REAL-mood sanity value is a breakdown. / 真实理智值是否已崩溃。 */
    static boolean breaksDown(float mood) {
        return mood <= MOOD_FLOOR;
    }
}
