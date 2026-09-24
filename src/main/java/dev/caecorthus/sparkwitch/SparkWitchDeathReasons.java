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

    private SparkWitchDeathReasons() {
    }
}
