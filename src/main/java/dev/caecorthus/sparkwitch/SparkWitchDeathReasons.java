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

    private SparkWitchDeathReasons() {
    }
}
