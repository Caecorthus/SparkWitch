package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Stable Ferryman contract: identifiers, tuning values and pure gates (identifiers and numbers in, values out, no
 * world access). The passive, the pardon, the ferry and the shop all read these values and never redefine them.
 * 摆渡人的稳定契约：标识符、调优数值与纯规则判断（输入标识符与数值、输出结果，不访问世界）。
 * 被动、宽恕、摆渡与商店都只读取这些数值，不另行定义。
 */
public final class FerrymanRules {
    public static final Identifier ROLE_ID = SparkWitch.id("charon");
    public static final int COLOR = 0x4C6B7A;

    /** The Lost Souls' own role id and cyan, shared by the sight marks and the name-tag cohort label. */
    /** 迷失的灵魂自身的职业 id 与青色，供视野描边与名牌同伙标签共用。 */
    public static final Identifier LOST_SOUL_ID = SparkWitch.id("lost_soul");
    public static final int SOUL_TEAM_COLOR = 0x39E6D0;
    public static final String LOST_SOUL_COHORT_KEY = "game.tip.sparkwitch.lost_soul_cohort";

    /**
     * The Ferryman is never drawn below this population; it carries its own win condition, so small rounds keep it out.
     * 低于该人数时不抽取摆渡人：它自带胜利条件，小对局不放入。
     */
    public static final int MIN_PLAYERS = 8;

    /** The ability key is armed at round start, re-armed 5 s after a successful block. / 技能键开局 60 秒冷却，成功格挡后 5 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int PARDON_COOLDOWN_TICKS = 5 * 20;

    /**
     * Pressing the key with no lethal hit to refuse is a whiff: the longer cooldown teaches the Charon to hold it, and
     * stops the key from being a free "is anything about to kill me" probe.
     * 没有致命伤可拒绝时按键就算空放：更长的冷却让摆渡人学会留住技能，也让它不再是"是不是有人要杀我"的免费探针。
     */
    public static final int PARDON_WHIFF_COOLDOWN_TICKS = 15 * 20;

    /** Press window after a lethal hit (1.5 s). / 致命一击后的按键窗口（1.5 秒）。 */
    public static final int PARDON_WINDOW_TICKS = 30;

    /**
     * The mark lands 1.5 s after a successful block, not at the moment of the block: the attacker keeps a short window
     * to answer the Charon before the glance settles on them.
     * 标记在成功格挡后 1.5 秒才落下，而不是格挡的瞬间：攻击者因此还有一小段时间回应摆渡人，之后「瞥视」才落到他身上。
     */
    public static final int GAZE_MARK_DELAY_TICKS = 30;

    /** A body stays "fresh" (highlighted, ferriable) for 60 s after death. / 尸体在死亡后 60 秒内保持新鲜（高亮、可摆渡）。 */
    public static final int FRESH_BODY_TICKS = 60 * 20;

    /** Ferry reach and pay. / 摆渡距离与报酬。 */
    public static final double FERRY_RANGE = 4.0;
    public static final double FERRY_RANGE_SQUARED = FERRY_RANGE * FERRY_RANGE;
    public static final int FERRY_GOLD = 50;
    public static final int MERCY_BREAK_GOLD = 50;
    /** Every successful ferry starts a 5 s cooldown on the secondary key. / 每次成功摆渡都会让第二技能键进入 5 秒冷却。 */
    public static final int FERRY_COOLDOWN_TICKS = 5 * 20;

    /** Second hit on a marked attacker: 10 s of feign death, invisible to instinct for 5 s after it ends. */
    /** 被标记者再次攻击摆渡人：10 秒假死，结束后 5 秒内本能透视不到。 */
    public static final int FEIGN_TICKS = 10 * 20;
    public static final int FEIGN_INSTINCT_BLOCK_TICKS = 5 * 20;

    /**
     * Wathe deaths the Charon never holds, neither with a pardon window nor with a gaze feign: the train fall (Wathe
     * re-kills every tick below the play area, crediting the last attacker), a disconnect, the vanilla-death safety net
     * and drowning. None is an attack the Charon could answer, and a held one would only replay or repeat.
     * 摆渡人从不扣住的 Wathe 死亡，宽恕窗口与「瞥视」假死都不适用：坠车（玩家低于游戏区时 Wathe 每刻重新击杀并记给最后的
     * 攻击者）、断线、原版死亡兜底与溺水。它们都不是摆渡人能回应的攻击，扣住只会被重放或重复触发。
     */
    public static final Set<Identifier> UNHELD_DEATH_REASONS = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.ESCAPED,
            GameConstants.DeathReasons.VANILLA_DEATH,
            GameConstants.DeathReasons.DROWNED
    );

    /**
     * Reasons the marked attacker's fake corpse may show: common Wathe-core murder reasons only, mirroring the Fiend's
     * periodic pool (owner decision: the Charon's feign stays standalone, so the pool is copied, not shared). A
     * Charon-specific reason would tell every body reader (Coroner, dead spectators) that the body is a feign.
     * 被标记者假尸体可显示的死因：只取 Wathe 本体的常见谋杀死因，与魔人的定时死因池一致（所有者决定摆渡人的假死独立实现，
     * 因此复制而非共用）。摆渡人专属的死因会让所有能读取尸体的人（验尸官、已死亡的旁观者）看出这是假死。
     */
    public static final List<Identifier> FEIGN_CORPSE_REASONS = List.of(
            GameConstants.DeathReasons.KNIFE,
            GameConstants.DeathReasons.GUN,
            GameConstants.DeathReasons.POISON,
            GameConstants.DeathReasons.BAT
    );

    /** Shop. / 商店。 */
    public static final int MOMENT_PRICE = 250;
    public static final int CROWBAR_PRICE = 25;
    public static final String MOMENT_SHOP_ENTRY_ID = "sparkwitch_ferryman_moment";
    public static final String CROWBAR_SHOP_ENTRY_ID = "sparkwitch_ferryman_crowbar";

    /** Lost Soul revival: one body per 6 players in the round, at least one. / 亡魂时刻复活数量：每 6 名玩家 1 具，至少 1 具。 */
    public static final int SOUL_PLAYERS_PER_BODY = 6;

    private FerrymanRules() {
    }

    /** Exact-role gate; never inferred from faction or namespace. / 仅按精确职业判断，不从阵营或命名空间推断。 */
    public static boolean isFerryman(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean canPardon(int cooldownTicks) {
        return cooldownTicks <= 0;
    }

    /** False for the {@link #UNHELD_DEATH_REASONS}; a missing reason may be held. / 不扣住的死因返回 false；缺失死因可扣住。 */
    public static boolean isHeldReason(@Nullable Identifier deathReason) {
        return deathReason == null || !UNHELD_DEATH_REASONS.contains(deathReason);
    }

    /**
     * Whether the Charon may hold this kill at all (pardon window or gaze feign). A forced kill ignores a cancel, so it
     * is never held (no window, no feign, no gaze spent); neither is a SparkTraits-terminal reason, a kill with no
     * attacker or by the Charon itself, an unheld Wathe reason, or the window's own replay.
     * 摆渡人能否扣住这次击杀（宽恕窗口或「瞥视」假死）。强制击杀无视取消，因此从不扣住（不开窗口、不假死、不消耗瞥视）；
     * SparkTraits 终结死因、没有攻击者或由摆渡人自己造成的击杀、不扣住的 Wathe 死因以及窗口自身的重放同样不扣住。
     */
    public static boolean mayHoldKill(boolean replaying, boolean force, boolean terminal, boolean hasKiller,
                                      boolean selfKill, @Nullable Identifier deathReason) {
        return !replaying && !force && !terminal && hasKiller && !selfKill && isHeldReason(deathReason);
    }

    /**
     * Whether a marked attacker can fall into the feign now (the Fiend's start gate, mirrored): an ACTIVE round, the
     * attacker playing and alive and standing in the world (not a spectator, not creative), not already feigning, not
     * swallowed by a Taotie, and not held by another session that owns its camera, body or game mode (Rift Gate,
     * Kidnapper control, Raven Form, Seeker remote session). When it cannot, the hit falls back to the plain pardon.
     * 被标记者此刻能否陷入假死（照搬魔人的开始条件）：ACTIVE 回合，攻击者在局内存活且站在世界中（非旁观、非创造），尚未假死，
     * 未被饕餮吞下，也未被其他占用其视角、身体或游戏模式的状态控制（裂隙门、绑匪控制、化鸦、搜寻者远程会话）。不能时，
     * 这一击回到普通宽恕逻辑。
     */
    public static boolean canFeign(boolean gameActive, boolean playingAndAlive, boolean inWorld, boolean feigning,
                                   boolean swallowed, boolean otherSession) {
        return gameActive && playingAndAlive && inWorld && !feigning && !swallowed && !otherSession;
    }

    /**
     * Whether a round reset changes anything the owner's client holds (both mark sets, the pardon cooldown that becomes
     * {@link #INITIAL_COOLDOWN_TICKS}, the ferry cooldown that becomes 0), compared with the values before the reset.
     * The pardon window is server-only and never synced, so it is not part of the answer.
     * 换局重置是否改变了拥有者客户端持有的任何值（两组标记、变为 {@link #INITIAL_COOLDOWN_TICKS} 的宽恕冷却、变为 0 的
     * 摆渡冷却），与重置前的值比较。宽恕窗口只在服务端、从不同步，因此不计入。
     */
    public static boolean roundResetChanges(int gazeCount, int ferriedCount, int cooldownTicks,
                                            int ferryCooldownTicks) {
        return gazeCount != 0 || ferriedCount != 0 || cooldownTicks != INITIAL_COOLDOWN_TICKS
                || ferryCooldownTicks != 0;
    }

    /** One believable reason for the fake corpse. / 为假尸体抽取一个可信的死因。 */
    public static Identifier feignCorpseReason(RandomGenerator random) {
        return FEIGN_CORPSE_REASONS.get(random.nextInt(FEIGN_CORPSE_REASONS.size()));
    }

    public static boolean isFreshBody(long now, long deathGameTime) {
        return now - deathGameTime <= FRESH_BODY_TICKS;
    }

    public static boolean inFerryRange(double squaredDistance) {
        return squaredDistance <= FERRY_RANGE_SQUARED;
    }

    /** Number of Lost Souls one purchase may revive. / 一次购买可复活的迷失灵魂数量。 */
    public static int soulCount(int totalPlayers) {
        return Math.max(1, totalPlayers / SOUL_PLAYERS_PER_BODY);
    }
}
