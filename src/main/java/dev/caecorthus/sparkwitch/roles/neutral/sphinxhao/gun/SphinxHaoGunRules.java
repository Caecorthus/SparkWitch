package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;

/**
 * Pure rules of the Sphinx Hao "手"枪 (owner spec 2026-11-17): what a use does, whether a shot counted as a kill, and
 * what the shooter gets for it. Both sides may read it; nothing here touches the world.
 * 狮身豪“手”枪的纯规则（所有者规格 2026-11-17）：一次使用做什么、一次射击是否算击杀、射手因此获得什么。两端均可读取；
 * 这里不触碰世界。
 */
public final class SphinxHaoGunRules {
    /**
     * Rewound hitbox margin of the fist ray, the Taser's 0.2: a fist is no thinner than a dart.
     * 拳头射线的回溯命中箱余量，与电击枪的 0.2 相同：拳头不比电击镖细。
     */
    public static final double HIT_MARGIN = 0.2;
    /** Sound pitches of the shot (owner: piston extend ~1.6). / 射击音效的音调（所有者：活塞伸出约 1.6）。 */
    public static final float FIRE_PISTON_PITCH = 1.6F;
    public static final float FIRE_CROSSBOW_PITCH = 1.3F;

    /** What one use of the gun does. / 一次使用手枪的结果。 */
    public enum Action {
        /** Not a living Sphinx Hao, or blocked: nothing happens, nothing is spent. / 非存活狮身豪或被阻止：无事发生。 */
        REFUSED,
        /** Loaded: the fist is fired and the gun is empty again. / 已装填：射出拳头，枪重新变空。 */
        FIRE,
        /** Empty with a fist in the inventory: one is loaded. / 空膛且背包有拳头子弹：装填一发。 */
        LOAD,
        /** Empty without ammo: a dry click and a hint. / 空膛且没有子弹：空响与提示。 */
        DRY
    }

    /** Whether a shot killed: decides the shooter's effects. / 一次射击是否击杀：决定射手获得的效果。 */
    public enum Outcome {
        KILL,
        /** A miss, a block, a shield, or a death SparkTraits intercepted. / 未命中、被挡、护盾或被 SparkTraits 拦截的死亡。 */
        NO_KILL
    }

    /**
     * Shooter effects of an outcome. Zero ticks means no such effect.
     * 某结果给射手的效果。刻数为 0 表示没有该效果。
     *
     * @param hao            hao paid through {@code SphinxHaoPlayerComponent.addHao} / 经 addHao 支付的豪度值
     * @param speedAmplifier Speed amplifier (0 = Speed I) / 速度等级（0 = 速度 I）
     * @param speedTicks     Speed duration / 速度持续刻
     * @param glowTicks      Glowing duration / 发光持续刻
     * @param missMessage    whether 「嘉豪击中了敌人身后的恶灵」 is shown / 是否显示该动作栏提示
     */
    public record ShooterEffects(int hao, int speedAmplifier, int speedTicks, int glowTicks, boolean missMessage) {
    }

    private static final ShooterEffects KILL_EFFECTS = new ShooterEffects(0, SphinxHaoRules.KILL_SPEED_AMPLIFIER,
            SphinxHaoRules.KILL_SPEED_TICKS, SphinxHaoRules.KILL_GLOW_TICKS, false);
    private static final ShooterEffects NO_KILL_EFFECTS = new ShooterEffects(SphinxHaoRules.MISS_HAO,
            SphinxHaoRules.MISS_SPEED_AMPLIFIER, SphinxHaoRules.MISS_SPEED_TICKS, 0, true);

    private SphinxHaoGunRules() {
    }

    /**
     * Use gate: only a Sphinx Hao who is playing, alive and not a spectator, and not held by another authority (Control
     * Expert stun, Seeker remote view, Kidnapper control, an active Wraith).
     * 使用门槛：只有在局、存活、非旁观、且未被其他机制控制（控场专家眩晕、搜寻者远程视角、绑匪操控、激活的冤魂）的狮身豪。
     */
    public static boolean mayUse(boolean sphinxHao, boolean playingAndAlive, boolean spectator, boolean held) {
        return sphinxHao && playingAndAlive && !spectator && !held;
    }

    /**
     * Loaded-flag transitions of one accepted use. A refused use never reaches here; the SparkTraits weapon gate is
     * asked only for {@link Action#FIRE}.
     * 一次被接受的使用对应的装填标记转换。被拒绝的使用不会到达这里；SparkTraits 武器门槛只在 {@link Action#FIRE} 时询问。
     */
    public static Action decide(boolean mayUse, boolean loaded, boolean hasAmmo) {
        if (!mayUse) {
            return Action.REFUSED;
        }
        if (loaded) {
            return Action.FIRE;
        }
        return hasAmmo ? Action.LOAD : Action.DRY;
    }

    /** The loaded flag after an action. / 某动作之后的装填标记。 */
    public static boolean loadedAfter(Action action, boolean loadedBefore) {
        return switch (action) {
            case FIRE -> false;
            case LOAD -> true;
            case REFUSED, DRY -> loadedBefore;
        };
    }

    /** Ammo spent by an action: one fist per load, none in creative. / 某动作消耗的子弹：每次装填一发，创造模式不消耗。 */
    public static int ammoSpent(Action action, boolean creative) {
        return action == Action.LOAD && !creative ? 1 : 0;
    }

    /**
     * A KILL needs a player hit who was alive before, is dead after the one non-forced kill, and whose death SparkTraits
     * did not intercept; everything else is NO_KILL.
     * 击杀需要：命中了一名此前存活的玩家，在这一次非强制击杀之后已死亡，且其死亡未被 SparkTraits 拦截；其余都算未击杀。
     */
    public static Outcome outcome(boolean hitPlayer, boolean aliveBefore, boolean deadAfter, boolean intercepted) {
        return hitPlayer && aliveBefore && deadAfter && !intercepted ? Outcome.KILL : Outcome.NO_KILL;
    }

    /** The owner's effects table. / 所有者的效果表。 */
    public static ShooterEffects effects(Outcome outcome) {
        return outcome == Outcome.KILL ? KILL_EFFECTS : NO_KILL_EFFECTS;
    }
}
