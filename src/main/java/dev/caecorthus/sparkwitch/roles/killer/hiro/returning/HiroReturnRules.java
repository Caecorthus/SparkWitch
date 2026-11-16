package dev.caecorthus.sparkwitch.roles.killer.hiro.returning;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Pure rules of 二阶堂希罗's 死亡回归 (owner rules 2026-11-11); no world access, so every decision is unit-tested.
 * {@link HiroReturnService} and {@link HiroVictimFeignService} gather the facts and act on the answers.
 * 二阶堂希罗「死亡回归」的纯规则（所有者 2026-11-11 规则）；不访问世界，因此每个判定都有单元测试。事实由
 * {@link HiroReturnService} 与 {@link HiroVictimFeignService} 收集，并按这里的结论执行。
 */
public final class HiroReturnRules {
    /**
     * Wathe deaths that have no attacker to answer and no corpse or place to come back to: the train fall (also when a
     * push is credited to someone; Wathe repeats it every tick below the play area), a disconnect, the vanilla-death
     * fallback and drowning. They are never an attack on Hiro and never become a fake death. Same set as the Charon's
     * unheld reasons, copied on purpose so the two roles stay decoupled.
     * 没有可回应的攻击者、也没有可回来的尸体或位置的 Wathe 死亡：坠车（即使推人被记到某人头上；玩家低于游戏区时 Wathe 每刻
     * 重复触发）、断线、原版死亡兜底与溺水。它们从不算对希罗的攻击，也从不变成假死。集合与卡戎不扣住的死因相同，有意复制以
     * 保持两个职业解耦。
     */
    public static final Set<Identifier> ENVIRONMENT_DEATH_REASONS = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.ESCAPED,
            GameConstants.DeathReasons.VANILLA_DEATH,
            GameConstants.DeathReasons.DROWNED
    );

    /** What a {@code killPlayer} attempt on Hiro is. / 一次针对希罗的 {@code killPlayer} 尝试属于哪一类。 */
    public enum Attack {
        /** Not an attack: no attacker, her own action, or an environment death. / 不算攻击：无攻击者、她自己的行为或环境死亡。 */
        NONE,
        /** Another player's kill that the Return cannot undo. / 其他玩家的击杀，但死亡回归无法撤销。 */
        UNCANCELLABLE,
        /** Another player's lethal hit the Return may undo. / 其他玩家的致命一击，死亡回归可以撤销。 */
        CANCELLABLE
    }

    /** What an attack does. / 一次攻击的结果。 */
    public enum Outcome {
        IGNORE,
        /** Her fake-dead victims revive; the kill itself runs on. / 假死的受害者复活；击杀本身照常进行。 */
        REVIVE_VICTIMS,
        /** The kill is cancelled and the Return fires (which also revives the victims). / 取消击杀并触发回归（同时复活受害者）。 */
        RETURN
    }

    /** Why a key press is refused with a message. / 按键被拒绝并提示的原因。 */
    public enum CastRefusal {
        NONE,
        WINDOW_RUNNING,
        COOLDOWN
    }

    private HiroReturnRules() {
    }

    /**
     * Owner rule 6: an attack is a kill attempt by ANOTHER player (the {@code killer} argument or the Judge-attributed
     * cause), whether or not something would have blocked it. Only a plain lethal hit can be undone (the Charon
     * pardon's scope): never a forced kill, a SparkTraits-terminal or Savior-terminal reason, or a hit while Hiro is a
     * Knight duelist (the duel must end in a death).
     * 所有者规则 6：攻击指其他玩家发起的击杀尝试（{@code killer} 参数或法官归因的责任人），不论是否会被其他机制挡下。只有普通
     * 致命一击可以撤销（与卡戎宽恕范围一致）：强制击杀、SparkTraits 终结或救世主终结死因，以及希罗身为骑士决斗者时受到的
     * 攻击（决斗必须以死亡收场）都不可撤销。
     */
    public static Attack classifyAttack(@Nullable UUID hiro, @Nullable UUID attacker, @Nullable Identifier deathReason,
                                        boolean force, boolean terminal, boolean dueling) {
        if (hiro == null || attacker == null || attacker.equals(hiro)
                || deathReason != null && ENVIRONMENT_DEATH_REASONS.contains(deathReason)) {
            return Attack.NONE;
        }
        return force || terminal || dueling ? Attack.UNCANCELLABLE : Attack.CANCELLABLE;
    }

    /**
     * Inside her window a cancellable attack fires the Return; every other attack only revives her fake-dead victims
     * (also after the window ended, and also when that attack kills her).
     * 窗口内的可撤销攻击触发回归；其余攻击只复活她的假死受害者（窗口结束后同样如此，即使这次攻击杀死了她）。
     */
    public static Outcome outcome(Attack attack, boolean windowActive) {
        if (attack == Attack.NONE) {
            return Outcome.IGNORE;
        }
        return windowActive && attack == Attack.CANCELLABLE ? Outcome.RETURN : Outcome.REVIVE_VICTIMS;
    }

    /**
     * Anti-farm: an attacker on the killer team pays nothing; the Return itself still fires.
     * 防刷：杀手阵营的攻击者不提供金币；回归本身仍然触发。
     */
    public static int rewardCoins(boolean attackerOnKillerTeam) {
        return attackerOnKillerTeam ? 0 : HiroRules.RETURN_REWARD_COINS;
    }

    /**
     * Who counts as "on the killer team" for the anti-farm rule: the attacker's SparkFactionAPI EFFECTIVE faction (the
     * Specter's {@code isEffectiveKiller} convention), so a SparkTraits Impostor civilian pays nothing and a Conscience
     * killer, who effectively plays for the civilians, pays. Only an attacker whose effective faction cannot be read
     * (an attributed attacker who is offline) falls back to the raw role's {@code canUseKiller}.
     * 防刷规则中“属于杀手阵营”的判定：攻击者的 SparkFactionAPI 有效阵营（与幽魂 {@code isEffectiveKiller} 的约定一致），因此
     * SparkTraits 内鬼平民不提供金币，而实际站在平民一方的善良杀手会提供。只有读不到有效阵营的攻击者（被归因但已离线）才回退
     * 到原始职业的 {@code canUseKiller}。
     */
    public static boolean attackerOnKillerTeam(@Nullable Identifier effectiveFaction, boolean rawRoleCanUseKiller) {
        return effectiveFaction != null ? FactionIds.KILLER.equals(effectiveFaction) : rawRoleCanUseKiller;
    }

    /** A running window is reported before the cooldown. / 进行中的窗口先于冷却提示。 */
    public static CastRefusal castRefusal(boolean windowActive, int cooldownTicks) {
        if (windowActive) {
            return CastRefusal.WINDOW_RUNNING;
        }
        return cooldownTicks > 0 ? CastRefusal.COOLDOWN : CastRefusal.NONE;
    }

    /** Whole seconds shown for a remaining cooldown, rounded up. / 剩余冷却显示的整秒数，向上取整。 */
    public static int displaySeconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    /**
     * Owner rule 2, asked at the commit point of a death nothing else stopped: the kill is credited to a living Hiro
     * whose window runs, the victim is someone else, the death leaves a corpse at a place to come back to, and the
     * victim's body, camera and game mode are free to be taken. {@code settling} is the service's own replay of a
     * victim's real death.
     * 所有者规则 2，在一次没有被任何机制挡下的死亡的提交点询问：击杀记在窗口进行中的存活希罗头上、受害者是其他人、这次死亡会
     * 在可回来的位置留下尸体，且受害者的身体、视角与游戏模式可被接管。{@code settling} 表示服务自身正在重放受害者的真正死亡。
     */
    public static boolean feignsDeath(boolean settling, boolean creditedToHiroInWindow, boolean victimIsHiro,
                                      boolean spawnBody, @Nullable Identifier deathReason, boolean victimHoldable) {
        return !settling && creditedToHiroInWindow && !victimIsHiro && spawnBody
                && (deathReason == null || !ENVIRONMENT_DEATH_REASONS.contains(deathReason))
                && victimHoldable;
    }

    /**
     * The Charon's feign gate for an arbitrary victim: standing in the world, not already a fake-dead victim, not
     * swallowed and not held by another session that owns its camera, body or game mode (Rift Gate, Kidnapper control,
     * Raven Form, Seeker session). A victim that cannot be held dies for real at once.
     * 沿用卡戎对任意玩家的假死门槛：站在世界中、尚未处于假死、未被吞下，且未被其他占用其视角、身体或游戏模式的状态控制
     * （裂隙门、绑匪控制、化鸦、搜寻者会话）。无法被接管的受害者当场真正死亡。
     */
    public static boolean victimHoldable(boolean aliveAndSurvival, boolean alreadyFeigned, boolean swallowed,
                                         boolean otherSession) {
        return aliveAndSurvival && !alreadyFeigned && !swallowed && !otherSession;
    }

    /** Each victim's own timer. / 每名受害者各自的计时。 */
    public static boolean victimExpired(long now, long endTick) {
        return now >= endTick;
    }

    /**
     * Whether the real-death replay passes Hiro as the {@code killer} argument: only while she is online, still a
     * living round participant (a re-roled Hiro is) and SparkFactionAPI's structural veto would let her affect the
     * victim. Otherwise (offline, dead, an active Wraith, any actor the veto refuses) the replay runs without a killer
     * inside the Judge attribution scope of her UUID: Wathe's {@code killPlayer} guards that ignore {@code force} judge
     * the actor, and a death that committed 15 s ago must not be refused because of what its killer became since.
     * 真正死亡的重放是否把希罗作为 {@code killer} 参数传入：仅当她在线、仍是存活的对局参与者（被换职业的希罗也算），且
     * SparkFactionAPI 的结构性否决允许她影响受害者时。否则（离线、已死亡、成为激活冤魂、任何会被否决的行动者）重放不带凶手，
     * 在以她的 UUID 建立的法官归因作用域内运行：Wathe {@code killPlayer} 上无视 {@code force} 的守卫审查的是行动者，而 15 秒前
     * 已经落定的死亡不能因凶手此后的变化而被拒绝。
     */
    public static boolean replaysWithHiroAsKiller(boolean online, boolean playingAndAlive, boolean mayAffectVictim) {
        return online && playingAndAlive && mayAffectVictim;
    }

    /**
     * Whether a triggered Return may move Hiro to the record point: same dimension, and her body is hers to move
     * (not a spectator, swallowed or held by a session). When false everything else of the Return still happens.
     * 触发的回归能否把希罗移回记录点：同一维度，且她的身体可由她移动（非旁观、未被吞下、未被会话控制）。为 false 时回归的
     * 其余效果照常发生。
     */
    public static boolean mayTeleport(boolean sameDimension, boolean spectator, boolean swallowed,
                                      boolean otherSession) {
        return sameDimension && !spectator && !swallowed && !otherSession;
    }
}
