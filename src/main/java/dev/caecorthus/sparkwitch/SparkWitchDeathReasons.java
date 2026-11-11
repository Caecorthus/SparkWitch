package dev.caecorthus.sparkwitch;

import net.minecraft.util.Identifier;

public final class SparkWitchDeathReasons {
    public static final Identifier CEREMONIAL_BLADE = SparkWitch.id("ceremonial_blade");
    public static final Identifier MIGHTY_FORCE = SparkWitch.id("mighty_force");
    public static final Identifier PIERCED_BY_RAY = SparkWitch.id("pierced_by_ray");
    public static final Identifier NINJA_KNIFE_KILL = SparkWitch.id("ninja_knife_kill");
    public static final Identifier NINJA_SHURIKEN_KILL = SparkWitch.id("ninja_shuriken_kill");
    /**
     * Owner-approved exception: a forced kill that pierces every shield, registered as SparkTraits-terminal.
     * 所有者批准的例外：穿透所有护盾的强制击杀，并注册为 SparkTraits 终结死亡原因。
     */
    public static final Identifier BELL_TOLL = SparkWitch.id("bell_toll");
    /**
     * Second owner-approved exception: the Clock curse kill is forced and registered SparkTraits-terminal, so it
     * pierces every role, item, and trait protection; only a Timekeeper purchase lifts the curse before it settles.
     * 第二个所有者批准的例外：时钟诅咒击杀为强制击杀并注册为 SparkTraits 终结死亡原因，穿透所有职业、物品与天赋保护；
     * 仅计时员购买可在结算前解除诅咒。
     */
    public static final Identifier TIME_STOLEN = SparkWitch.id("time_stolen");
    /** Ordinary, non-forced Swordfish stab kill. / 普通、非强制的剑鱼刺杀。 */
    public static final Identifier SWORDFISH_STAB = SparkWitch.id("swordfish_stab");
    /** Ordinary, non-forced Potion Gunner TR shell kill. / 普通、非强制的药炮手 TR 炮弹击杀。 */
    public static final Identifier POTION_SHELL = SparkWitch.id("potion_shell");
    /** Ordinary, non-forced Potion Gunner launcher backblast kill. / 普通、非强制的药炮筒尾焰击杀。 */
    public static final Identifier POTION_BACKBLAST = SparkWitch.id("potion_backblast");
    /**
     * A Vulture ate a feigning Fiend's fake corpse: forced, body-less, and registered SparkTraits-terminal, so the
     * Fiend dies outright (owner request 2026-10-16).
     * 秃鹫吃掉了假死魔人的假尸体：强制、不留尸体，并注册为 SparkTraits 终结死亡原因，魔人直接死亡（所有者 2026-10-16 需求）。
     */
    public static final Identifier FIEND_DEVOURED = SparkWitch.id("fiend_devoured");
    /**
     * Ordinary, non-forced Witch Maiden crimson wither skull kill; treated as a gunshot elsewhere (owner Q5).
     * 普通、非强制的巫女赤色凋灵之首击杀；在其他系统中按枪击处理（所有者 Q5）。
     */
    public static final Identifier CRIMSON_WITHER_SKULL = SparkWitch.id("crimson_wither_skull");
    /**
     * The Knight challenged a good player and dies at once. Forced, body kept, no killer, and registered
     * SparkTraits-terminal (owner 2026-10-30).
     * 骑士对好人发起决斗，当场自裁。强制击杀、保留尸体、无凶手，并注册为 SparkTraits 终结死亡原因（所有者 2026-10-30）。
     */
    public static final Identifier DUEL_SUICIDE = SparkWitch.id("duel_suicide");
    /**
     * The duel ran 120 s without a duel kill (both die). Forced, body kept, no killer, and registered
     * SparkTraits-terminal (owner 2026-10-30). Third-party interference no longer kills the other duelist
     * (owner 2026-10-31).
     * 决斗 120 秒未分胜负（双方均死）。强制击杀、保留尸体、无凶手，并注册为 SparkTraits 终结死亡原因（所有者 2026-10-30）。
     * 第三方干预不再使另一名决斗者死亡（所有者 2026-10-31）。
     */
    public static final Identifier DUEL_FAILED = SparkWitch.id("duel_failed");
    /**
     * Legacy: the Knight's push-off penalty before it moved to Wathe's {@code shot_innocent}. Nothing produces it now;
     * the id, its lang line and its Prophecy PENALTY group stay so older replays and records still read.
     * 旧版：骑士推下好人的惩罚改用 Wathe 的 {@code shot_innocent} 之前的原因。现已无处产生；保留该 id、语言条目与预言
     * PENALTY 分组，使旧回放与记录仍可读取。
     */
    public static final Identifier CEREBELLUM = SparkWitch.id("cerebellum");
    /**
     * The Ferryman's 0.5 s pardon window expired: the held kill replays with this reason when the original one is
     * unknown. Ordinary, non-forced kill; the body follows the held kill's own {@code spawnBody}.
     * 摆渡人的 0.5 秒宽恕窗口过期：原死亡原因未知时以该原因重放被扣住的击杀。普通、非强制击杀；是否留尸沿用被扣住击杀
     * 自身的 {@code spawnBody}。
     */
    public static final Identifier PARDON_EXPIRED = SparkWitch.id("pardon_expired");
    /**
     * A Vulture ate the fake corpse of a marked attacker feigning (10 s) before the Ferryman: forced, body-less and
     * registered SparkTraits-terminal, so the player dies outright (the {@link #FIEND_DEVOURED} pattern). Never shown on
     * the fake corpse itself, which carries a common murder reason.
     * 秃鹫吃掉了在摆渡人面前假死（10 秒）的被标记者的假尸体：强制、不留尸体，并注册为 SparkTraits 终结死亡原因，该玩家直接
     * 死亡（沿用 {@link #FIEND_DEVOURED} 的做法）。假尸体本身从不显示该原因，而是显示常见谋杀死因。
     */
    public static final Identifier FERRYMAN_FEIGN = SparkWitch.id("ferryman_feign");
    /**
     * Every Lost Soul of a Soul Moment died: the Ferryman dies with them and the moment ends.
     * 亡魂时刻的迷失灵魂全部死亡：摆渡人随之而亡，时刻结束。
     */
    public static final Identifier FERRYMAN_MOMENT_LOST = SparkWitch.id("ferryman_moment_lost");
    /**
     * A third party attacked a duelist while the duel variable was 1. Forced, body kept, no killer, and registered
     * SparkTraits-terminal, so the attacker's traits and every shield are bypassed; the attack itself resolves normally
     * (owner 2026-10-31).
     * 决斗变量为 1 时第三人攻击决斗者。强制击杀、保留尸体、无凶手，并注册为 SparkTraits 终结死亡原因，绕过攻击者
     * 的词条与一切护盾；攻击本身正常结算（所有者 2026-10-31）。
     */
    public static final Identifier DUEL_INTERFERENCE = SparkWitch.id("duel_interference");
    /**
     * 二阶堂希罗's Rope finished its strangle (窒息 / Asphyxiated). The kill bypasses shields without consuming them
     * (owner spec 2026-11-11); how it is issued belongs to {@code item/rope/RopeStrangleService}.
     * 二阶堂希罗的绳索完成勒杀（窒息）。该击杀无视护盾且不消耗护盾（所有者规格 2026-11-11）；具体的击杀方式由
     * {@code item/rope/RopeStrangleService} 负责。
     */
    public static final Identifier STRANGLED = SparkWitch.id("strangled");

    private SparkWitchDeathReasons() {
    }
}
