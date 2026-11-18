package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.caecorthus.sparkwitch.util.OffMatchUse;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the Sniper's M1903A4: cooldown lengths, the hit margin, the fire gate and when a shot counts as a kill.
 * Both sides may read it; nothing here touches the world.
 * 狙击手 M1903A4 的纯规则：冷却时长、命中余量、开火门槛以及一发子弹何时算击杀。两端均可读取；这里不触碰世界。
 */
public final class M1903A4Rules {
    /** 10 s after every shot; the rifle's nominal cooldown. / 每次开火后 10 秒；步枪的标准冷却。 */
    public static final int SHOT_COOLDOWN_TICKS = 200;
    /**
     * 2 s floor after a round is loaded: a longer running cooldown is kept, a shorter one is raised to it.
     * 装入一发后的 2 秒下限：更长的冷却保持不变，更短的被抬高到 2 秒。
     */
    public static final int RELOAD_COOLDOWN_TICKS = 40;
    /** 60 s round-start lock, written once per grant. / 开局 60 秒锁定，每次发放写入一次。 */
    public static final int ROUND_START_COOLDOWN_TICKS = 20 * 60;
    /**
     * Rewound player hitbox margin of the round, the AXMC's 0.1: a rifle bullet is no wider than the AXMC's.
     * 子弹的回溯玩家命中箱余量，与 AXMC 的 0.1 相同：步枪子弹不比 AXMC 的更宽。
     */
    public static final double HIT_MARGIN = 0.1;

    /** Replay record keys. / 回放记录键。 */
    static final String RECORD_ACTION = "action";
    static final String ACTION_RELOAD = "reload";
    static final String RECORD_LOADED_ROUNDS = "loaded_rounds";
    /** Whether a player was hit (recorded before the kill). / 是否命中了玩家（在击杀之前记录）。 */
    static final String RECORD_HIT = "hit";

    private M1903A4Rules() {
    }

    /**
     * Outcome of a charge start or a release, in check order (the first failing check wins). Every refusal is silent
     * and spends nothing: no round, no cooldown, no sound.
     * 开始蓄力或松开时的结果，按检查顺序排列（第一个不满足的检查即为结果）。所有拒绝都静默且不消耗任何东西：不耗弹、
     * 不进入冷却、不出声。
     */
    public enum Decision {
        /**
         * Not {@link OffMatchUse.Mode#MATCH}: no ACTIVE round, a non-participant, or a dead participant (a Sniper
         * killed mid-charge keeps the active use, so its spectator release lands here).
         * 不是 {@link OffMatchUse.Mode#MATCH}：没有 ACTIVE 对局、非参与者，或已死亡的参与者（蓄力中被杀的狙击手仍保留使用状态，
         * 其旁观松手落在这里）。
         */
        NOT_IN_MATCH,
        /** The rifle is not the main-hand stack. / 步枪不是主手物品。 */
        NOT_MAIN_HAND,
        SPECTATOR,
        /**
         * Another authority holds the shooter: Control Expert stun, Seeker remote view, Kidnapper control, an active
         * Wraith, or a running Dominator forced-shot sequence (the Savior pistol's set).
         * 射手被其他机制控制：控场专家眩晕、搜寻者远程视角、绑匪操控、激活的冤魂，或进行中的支配者强制开枪序列（与救世主手枪同一组）。
         */
        HELD,
        /** SparkTraits blocks a weapon action with the rifle. / SparkTraits 阻止用步枪执行武器动作。 */
        WEAPON_BLOCKED,
        /**
         * The rifle item cooldown runs: its own shot / reload / round-start lock, or any outside lock (Knight duel seal,
         * Fiend aura, Abyss suppression, AC shell, Saint penalty).
         * 步枪物品冷却正在运行：自身的开火 / 装填 / 开局锁定，或任何外部锁定（骑士决斗封印、魔人光环、深渊压制、AC 炮弹、圣徒惩罚）。
         */
        COOLING_DOWN,
        /** No round loaded. / 没有装弹。 */
        EMPTY,
        FIRE
    }

    /**
     * Side-effect-free facts about one charge start or release. The client predicts with only what it can see
     * ({@code mode} MATCH, {@code held} and {@code weaponBlocked} false); the server decides with all of them.
     * 单次开始蓄力或松开的无副作用事实。客户端只用自己可见的事实预测（{@code mode} 视为 MATCH，{@code held} 与
     * {@code weaponBlocked} 视为 false）；服务端用全部事实裁决。
     *
     * @param mode          {@link OffMatchUse#mode}; only MATCH fires
     * @param mainHand      the rifle is the main-hand stack
     * @param spectator     spectator game mode
     * @param held          stun, Seeker remote lock, Kidnapper control, active Wraith or Dominator sequence
     * @param weaponBlocked SparkTraits blocks a weapon action with the rifle
     * @param coolingDown   the rifle item cooldown runs
     * @param loaded        at least one round is loaded
     */
    public record Facts(@Nullable OffMatchUse.Mode mode, boolean mainHand, boolean spectator, boolean held,
                        boolean weaponBlocked, boolean coolingDown, boolean loaded) {
    }

    public static Decision decide(Facts facts) {
        if (facts.mode() != OffMatchUse.Mode.MATCH) {
            return Decision.NOT_IN_MATCH;
        }
        if (!facts.mainHand()) {
            return Decision.NOT_MAIN_HAND;
        }
        if (facts.spectator()) {
            return Decision.SPECTATOR;
        }
        if (facts.held()) {
            return Decision.HELD;
        }
        if (facts.weaponBlocked()) {
            return Decision.WEAPON_BLOCKED;
        }
        if (facts.coolingDown()) {
            return Decision.COOLING_DOWN;
        }
        return facts.loaded() ? Decision.FIRE : Decision.EMPTY;
    }

    /**
     * Kill confirmation (the shooter-only kill sound): a player was hit who was alive before, is dead after the one
     * non-forced kill, and whose death SparkTraits did not intercept. A shield, a feign death or any veto is no kill.
     * 击杀确认（只有射手听得到的击杀音效）：命中了一名此前存活的玩家，在这一次非强制击杀之后已死亡，且其死亡未被 SparkTraits
     * 拦截。护盾、假死或任何否决都不算击杀。
     */
    public static boolean confirmsKill(boolean hitPlayer, boolean aliveBefore, boolean deadAfter, boolean intercepted) {
        return hitPlayer && aliveBefore && deadAfter && !intercepted;
    }

    /** Pure Vendetta isolation: a Vendetta pair only reaches each other. / 纯复仇者隔离：复仇配对只能互相影响。 */
    public static boolean vendettaAllows(boolean shooterVendetta, boolean targetVendetta, boolean exactPair) {
        return !(shooterVendetta || targetVendetta) || exactPair;
    }

    /**
     * Replay line key of one recorded use: the reload line, else "shot %s" when a player was hit, else the miss line.
     * 一次记录的回放文本键：装填行；否则命中玩家时为“射中 %s”；否则为未命中行。
     */
    static String replayKey(String action, boolean hit, boolean hasTarget) {
        if (ACTION_RELOAD.equals(action)) {
            return M1903A4Replay.RELOAD_KEY;
        }
        return hit && hasTarget ? M1903A4Replay.SHOT_KEY : M1903A4Replay.MISS_KEY;
    }
}
